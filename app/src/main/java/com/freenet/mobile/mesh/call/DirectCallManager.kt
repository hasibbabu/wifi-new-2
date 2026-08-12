package com.freenet.mobile.mesh.call

import android.content.Context
import com.freenet.mobile.mesh.FreeNetBridge
import com.freenet.mobile.mesh.protocol.MessageBody
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Call signaling (invite/accept/reject/end) travels over the mesh like any
 * other message, so it can reach a peer several hops away and ring their
 * phone. The actual audio/video only starts once both sides have swapped
 * direct IP:port endpoints — which only works if they're within radio
 * range of each other on the same Wi-Fi/Wi-Fi Direct link (see
 * ARCHITECTURE notes). If that direct link isn't there, [onCallFailed]
 * fires and the app should suggest sending a voice note instead.
 */
class DirectCallManager(
    private val context: Context,
    private val bridge: FreeNetBridge
) {
    companion object { const val MEDIA_PORT = 47832 }

    enum class State { IDLE, CALLING, RINGING, CONNECTED, ENDED }

    var state: State = State.IDLE
        private set

    private var callId: String? = null
    private var peerNodeId: String? = null
    private var withVideo: Boolean = false

    private var socket: DatagramSocket? = null
    private var audio: AudioCallStreamer? = null
    private var video: VideoCallStreamer? = null
    private val receiving = AtomicBoolean(false)
    private val audioReassembler = FrameReassembler()
    private val videoReassembler = FrameReassembler()

    var onIncomingCall: ((fromNodeId: String, callId: String, withVideo: Boolean) -> Unit)? = null
    var onCallConnected: (() -> Unit)? = null
    var onCallEnded: ((reason: String) -> Unit)? = null
    var onCallFailed: ((reason: String) -> Unit)? = null
    var onRemoteVideoFrame: ((ByteArray) -> Unit)? = null

    private var localPreviewSurface: android.view.Surface? = null

    /** CallActivity calls this once its local-preview TextureView is ready. */
    fun attachLocalPreview(surface: android.view.Surface?) {
        localPreviewSurface = surface
    }

    // ---- Outgoing call -------------------------------------------------

    fun startCall(toNodeId: String, video: Boolean) {
        val id = UUID.randomUUID().toString()
        callId = id
        peerNodeId = toNodeId
        withVideo = video
        state = State.CALLING

        val myIp = LocalAddress.ipv4()
        if (myIp == null) {
            onCallFailed?.invoke("No local network address — direct calling needs Wi-Fi or Wi-Fi Direct")
            state = State.IDLE
            return
        }

        bridge.sendCallSignal(
            toNodeId,
            MessageBody.CallSignal(
                kind = com.freenet.mobile.mesh.protocol.MessageType.CALL_INVITE,
                callId = id, withVideo = video, host = myIp, port = MEDIA_PORT
            )
        )
    }

    fun cancelCall() {
        val id = callId ?: return
        val peer = peerNodeId ?: return
        bridge.sendCallSignal(peer, MessageBody.CallSignal(com.freenet.mobile.mesh.protocol.MessageType.CALL_END, id, reason = "cancelled"))
        teardown("cancelled")
    }

    // ---- Incoming call ---------------------------------------------------

    fun acceptCall() {
        val id = callId ?: return
        val peer = peerNodeId ?: return
        val myIp = LocalAddress.ipv4()
        if (myIp == null) {
            bridge.sendCallSignal(peer, MessageBody.CallSignal(com.freenet.mobile.mesh.protocol.MessageType.CALL_REJECT, id, reason = "no_local_network"))
            onCallFailed?.invoke("No local network address — direct calling needs Wi-Fi or Wi-Fi Direct")
            state = State.IDLE
            return
        }
        bridge.sendCallSignal(
            peer,
            MessageBody.CallSignal(
                kind = com.freenet.mobile.mesh.protocol.MessageType.CALL_ACCEPT,
                callId = id, withVideo = withVideo, host = myIp, port = MEDIA_PORT
            )
        )
        // We don't have the caller's endpoint from CALL_ACCEPT's own payload
        // (it's ours); it arrived on the original CALL_INVITE — the caller
        // sends a CALL_ENDPOINT confirmation right after seeing our accept.
    }

    fun rejectCall(reason: String = "declined") {
        val id = callId ?: return
        val peer = peerNodeId ?: return
        bridge.sendCallSignal(peer, MessageBody.CallSignal(com.freenet.mobile.mesh.protocol.MessageType.CALL_REJECT, id, reason = reason))
        teardown(reason)
    }

    fun endCall() {
        val id = callId ?: return
        val peer = peerNodeId ?: return
        bridge.sendCallSignal(peer, MessageBody.CallSignal(com.freenet.mobile.mesh.protocol.MessageType.CALL_END, id, reason = "ended"))
        teardown("ended")
    }

    // ---- Signal handling (call this from FreeNetEvents.onCallSignal) -----

    fun onSignal(fromNodeId: String, signal: MessageBody.CallSignal) {
        when (signal.kind) {
            com.freenet.mobile.mesh.protocol.MessageType.CALL_INVITE -> {
                if (state != State.IDLE) {
                    bridge.sendCallSignal(fromNodeId, MessageBody.CallSignal(com.freenet.mobile.mesh.protocol.MessageType.CALL_REJECT, signal.callId, reason = "busy"))
                    return
                }
                callId = signal.callId
                peerNodeId = fromNodeId
                withVideo = signal.withVideo
                state = State.RINGING
                // Caller's endpoint travels on the invite itself.
                pendingPeerHost = signal.host
                pendingPeerPort = signal.port
                onIncomingCall?.invoke(fromNodeId, signal.callId, signal.withVideo)
            }

            com.freenet.mobile.mesh.protocol.MessageType.CALL_ACCEPT -> {
                if (signal.callId != callId) return
                // Callee's endpoint arrived; confirm ours so they can start sending too.
                val myIp = LocalAddress.ipv4()
                bridge.sendCallSignal(
                    fromNodeId,
                    MessageBody.CallSignal(com.freenet.mobile.mesh.protocol.MessageType.CALL_ENDPOINT, signal.callId, host = myIp, port = MEDIA_PORT)
                )
                beginMedia(signal.host, signal.port ?: MEDIA_PORT)
            }

            com.freenet.mobile.mesh.protocol.MessageType.CALL_ENDPOINT -> {
                if (signal.callId != callId) return
                beginMedia(signal.host ?: pendingPeerHost, signal.port ?: pendingPeerPort ?: MEDIA_PORT)
            }

            com.freenet.mobile.mesh.protocol.MessageType.CALL_REJECT -> {
                if (signal.callId != callId) return
                teardown(signal.reason ?: "rejected")
            }

            com.freenet.mobile.mesh.protocol.MessageType.CALL_END -> {
                if (signal.callId != callId) return
                teardown(signal.reason ?: "ended")
            }
        }
    }

    private var pendingPeerHost: String? = null
    private var pendingPeerPort: Int? = null

    // ---- Media session -----------------------------------------------------

    private fun beginMedia(host: String?, port: Int) {
        if (host == null) {
            onCallFailed?.invoke("Peer did not report a reachable address")
            teardown("no_endpoint")
            return
        }
        try {
            val s = DatagramSocket(MEDIA_PORT)
            socket = s
            val peerInet = InetAddress.getByName(host)

            audio = AudioCallStreamer(s, peerInet, port).also { it.start() }
            if (withVideo) {
                video = VideoCallStreamer(context, s, peerInet, port).also {
                    it.start(localPreviewSurface = localPreviewSurface)
                }
            }

            receiving.set(true)
            thread(name = "freenet-call-rx") { receiveLoop(s) }

            state = State.CONNECTED
            onCallConnected?.invoke()
        } catch (e: Exception) {
            onCallFailed?.invoke("Could not open media socket: ${e.javaClass.simpleName}")
            teardown("media_error")
        }
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(2048)
        while (receiving.get()) {
            try {
                val packet = java.net.DatagramPacket(buf, buf.size)
                s.receive(packet)
                val raw = packet.data.copyOfRange(0, packet.length)
                val fragment = MediaFrame.decode(raw) ?: continue
                when (fragment.type) {
                    MediaFrame.TYPE_AUDIO -> audioReassembler.accept(fragment)?.let { audio?.onAudioPayload(it) }
                    MediaFrame.TYPE_VIDEO -> videoReassembler.accept(fragment)?.let { onRemoteVideoFrame?.invoke(it) }
                }
            } catch (_: Exception) {
                if (!receiving.get()) break
            }
        }
    }

    private fun teardown(reason: String) {
        receiving.set(false)
        audio?.stop(); audio = null
        video?.stop(); video = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        val wasConnected = state == State.CONNECTED
        state = State.IDLE
        callId = null
        peerNodeId = null
        pendingPeerHost = null
        pendingPeerPort = null
        if (wasConnected) onCallEnded?.invoke(reason) else onCallFailed?.invoke(reason)
    }
}
