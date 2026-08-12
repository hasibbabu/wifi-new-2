package com.freenet.mobile.mesh

import android.content.Context
import com.freenet.mobile.mesh.protocol.MediaKind
import com.freenet.mobile.mesh.protocol.MessageBody
import com.freenet.mobile.mesh.protocol.MeshEnvelope
import com.freenet.mobile.mesh.discovery.LanDiscovery
import com.freenet.mobile.mesh.media.MediaReassembler
import com.freenet.mobile.mesh.media.MediaChunker
import com.freenet.mobile.mesh.media.MediaStore

/**
 * Callbacks the UI layer (or a call manager) registers to hear about mesh
 * activity. All callbacks fire on a background thread; the UI must post
 * back to the main thread itself.
 */
interface FreeNetEvents {
    fun onTextMessage(fromNodeId: String, text: String) {}
    fun onFileStarted(fromNodeId: String, fileId: String, fileName: String, mediaKind: String, totalChunks: Int) {}
    fun onFileProgress(fileId: String, receivedChunks: Int, totalChunks: Int) {}
    fun onFileReceived(fromNodeId: String, mediaKind: String, localFile: java.io.File, checksumOk: Boolean) {}
    fun onCallSignal(fromNodeId: String, signal: MessageBody.CallSignal) {}
    fun onNeighborsChanged() {}
}

class FreeNetBridge(private val context: Context) {

    val engine = MeshEngine(context)

    private val bluetooth = BluetoothTransport(
        context,
        nodeId = { engine.nodeId() },
        onPeer = { peerId, address -> registerLinkNeighbor(peerId, address, "bluetooth") },
        onEnvelope = { receive(it) }
    )
    private val ble = BleTransport(
        context,
        nodeId = { engine.nodeId() },
        onPeer = { peerId, address -> registerLinkNeighbor(peerId, address, "ble") },
        onEnvelope = { receive(it) }
    )
    private val wifi = WifiLanTransport(context)
    private val wifiDirect = WifiDirectTransport(context)
    private val discovery = LanDiscovery(
        context,
        nodeId = { engine.nodeId() }
    ) { peerId, address ->
        engine.registerNeighbor(
            peerId, address,
            setOf("wifi_lan"),
            score = 100
        )
        events?.onNeighborsChanged()
    }

    private val reassembler = MediaReassembler()
    private val mediaStore = MediaStore(context)

    var events: FreeNetEvents? = null

    private fun registerLinkNeighbor(peerId: String, address: String, transport: String) {
        // Point-to-point radios (BLE/Bluetooth) score lower than Wi-Fi LAN
        // since they're far lower bandwidth; RouteTable prefers the
        // highest-scoring hop when more than one path to a peer exists.
        engine.registerNeighbor(peerId, address, setOf(transport), score = 50)
        events?.onNeighborsChanged()
    }

    fun start() {
        wifi.onPacketBytes = { receive(it) }
        bluetooth.start()
        ble.start()
        wifi.start()
        wifiDirect.start()
        discovery.start()
    }

    fun stop() {
        discovery.stop()
        bluetooth.stop()
        ble.stop()
        wifi.stop()
        wifiDirect.stop()
    }

    fun discover() {
        bluetooth.discover()
        ble.discover()
        wifiDirect.discover()
    }

    // ---- Outgoing: plain text -------------------------------------------------

    fun sendText(destination: String?, text: String): SendResult {
        val body = MessageBody.Text(text).encode()
        val packet = engine.createPacket(destination, body)
        return sendPacket(packet)
    }

    // ---- Outgoing: files / photos / voice notes --------------------------------

    /**
     * Chunks [bytes] and sends the FileMeta announcement followed by every
     * FileChunk as its own MeshEnvelope. Each envelope is routed/queued
     * independently, so a mid-transfer disconnect just means the remaining
     * chunks sit in the offline queue until a route reappears.
     */
    fun sendFile(
        destination: String?,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        mediaKind: String
    ): List<SendResult> {
        val chunked = MediaChunker.chunk(bytes, fileName, mimeType, mediaKind)
        val results = mutableListOf<SendResult>()

        results += sendPacket(engine.createPacket(destination, chunked.meta.encode(), ttl = 16))
        chunked.chunks.forEach { chunk ->
            results += sendPacket(engine.createPacket(destination, chunk.encode(), ttl = 16))
        }
        return results
    }

    fun sendPhoto(destination: String?, bytes: ByteArray, fileName: String = "photo.jpg") =
        sendFile(destination, bytes, fileName, "image/jpeg", MediaKind.IMAGE)

    fun sendVoiceNote(destination: String?, bytes: ByteArray, fileName: String = "voice.m4a") =
        sendFile(destination, bytes, fileName, "audio/mp4", MediaKind.AUDIO)

    // ---- Outgoing: call signaling (Phase 2) -------------------------------------

    fun sendCallSignal(destination: String, signal: MessageBody.CallSignal): SendResult {
        // Call signaling should reach the destination fast; a short TTL keeps
        // stale invites from lingering in the mesh after a call is answered
        // or cancelled elsewhere.
        val packet = engine.createPacket(destination, signal.encode(), ttl = 8)
        return sendPacket(packet)
    }

    // ---- Sending machinery -------------------------------------------------

    private fun sendPacket(packet: MeshEnvelope): SendResult {
        if (packet.destinationNodeId == null) {
            engine.queue(packet)
            return SendResult(true, "Broadcast queued")
        }

        val hop = engine.nextHop(packet.destinationNodeId)
            ?: run {
                engine.queue(packet)
                return SendResult(false, "No route; packet queued")
            }

        val envelopeBytes = packet.encode()

        return when (hop.transport) {
            "wifi_lan" -> wifi.send(envelopeBytes, hop.endpoint ?: return SendResult(false, "Missing endpoint"))
            "ble" -> ble.send(envelopeBytes, hop.peerId)
            "bluetooth" -> bluetooth.send(envelopeBytes, hop.peerId)
            "wifi_direct" -> wifiDirect.send(envelopeBytes, hop.peerId)
            else -> SendResult(false, "Unknown transport")
        }
    }

    /** Re-sends everything sitting in the offline queue; call after a new route appears. */
    fun flushQueue(): Int {
        var sent = 0
        engine.drainQueue().forEach { packet ->
            val result = sendPacket(packet)
            if (result.success) sent++
        }
        return sent
    }

    // ---- Receiving -------------------------------------------------------------

    private fun receive(bytes: ByteArray) {
        try {
            val packet = MeshEnvelope.decode(bytes)
            if (!engine.shouldProcess(packet.packetId)) return // already seen this hop

            val forMe = engine.isForMe(packet)
            val broadcast = engine.isBroadcast(packet)

            if (forMe || broadcast) {
                deliver(packet)
            }

            if (!forMe) {
                val next = engine.forwardPacket(packet) ?: return
                sendPacket(next)
            }
        } catch (_: Exception) {
            // Invalid/corrupt packets are discarded.
        }
    }

    private fun deliver(packet: MeshEnvelope) {
        val body = try {
            MessageBody.decode(packet.body)
        } catch (_: Exception) {
            return
        }

        when (body) {
            is MessageBody.Text -> events?.onTextMessage(packet.sourceNodeId, body.text)

            is MessageBody.FileMeta -> {
                reassembler.onMeta(body)
                events?.onFileStarted(packet.sourceNodeId, body.fileId, body.fileName, body.mediaKind, body.totalChunks)
            }

            is MessageBody.FileChunk -> {
                val completed = reassembler.onChunk(body)
                if (completed == null) {
                    val progress = reassembler.progress(body.fileId)
                    if (progress != null) events?.onFileProgress(body.fileId, progress.first, progress.second)
                } else {
                    val localFile = mediaStore.save(completed)
                    events?.onFileReceived(packet.sourceNodeId, completed.mediaKind, localFile, completed.checksumOk)
                }
            }

            is MessageBody.FileAck -> { /* delivery receipts: reserved for future retry logic */ }

            is MessageBody.CallSignal -> events?.onCallSignal(packet.sourceNodeId, body)
        }
    }
}
