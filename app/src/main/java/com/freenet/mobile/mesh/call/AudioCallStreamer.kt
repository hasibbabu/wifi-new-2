package com.freenet.mobile.mesh.call

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Minimal real-time voice call: 16kHz mono 16-bit PCM captured in ~20ms
 * chunks (640 bytes), sent over the shared call [socket] as they're
 * recorded, and played back as they arrive. No jitter buffer, no codec
 * (PCM is simplest to get right first) — acceptable over a single direct
 * radio hop, not something that should be asked to survive multiple mesh
 * relays (see ARCHITECTURE notes on live media).
 */
class AudioCallStreamer(
    private val socket: DatagramSocket,
    private val peerAddress: InetAddress,
    private val peerPort: Int
) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SAMPLES = 320 // 20ms @ 16kHz
    }

    private val running = AtomicBoolean(false)
    private val frameId = AtomicInteger(0)
    private var recordThread: Thread? = null
    private var playbackTrack: AudioTrack? = null

    @SuppressLint("MissingPermission") // caller verifies RECORD_AUDIO before starting a call
    fun start() {
        if (running.getAndSet(true)) return

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_SAMPLES * 2 * 4)
        )

        val playMinBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        playbackTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(playMinBuf, CHUNK_SAMPLES * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        playbackTrack?.play()

        recorder.startRecording()
        recordThread = thread(name = "freenet-audio-tx") {
            val buffer = ShortArray(CHUNK_SAMPLES)
            while (running.get()) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val bytes = shortsToBytes(buffer, read)
                val id = frameId.getAndIncrement()
                for (wireFrame in MediaFrame.fragment(MediaFrame.TYPE_AUDIO, id, bytes)) {
                    try {
                        socket.send(DatagramPacket(wireFrame, wireFrame.size, peerAddress, peerPort))
                    } catch (_: Exception) { /* peer unreachable right now; next chunk may succeed */ }
                }
            }
            recorder.stop()
            recorder.release()
        }
    }

    /** Feed a decoded audio fragment payload (already de-fragmented) here from the shared receive loop. */
    fun onAudioPayload(pcmBytes: ByteArray) {
        val shorts = bytesToShorts(pcmBytes)
        playbackTrack?.write(shorts, 0, shorts.size)
    }

    fun stop() {
        running.set(false)
        recordThread?.join(500)
        recordThread = null
        playbackTrack?.stop()
        playbackTrack?.release()
        playbackTrack = null
    }

    private fun shortsToBytes(shorts: ShortArray, count: Int): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            out[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        for (i in out.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() and 0xFF
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }
}
