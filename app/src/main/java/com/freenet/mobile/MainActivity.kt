package com.freenet.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.freenet.mobile.mesh.AndroidPermissions
import com.freenet.mobile.mesh.FreeNetEvents
import com.freenet.mobile.mesh.protocol.MediaKind
import com.freenet.mobile.mesh.protocol.MessageBody
import com.freenet.mobile.ui.CallActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val app get() = application as FreeNetApp
    private val bridge get() = app.bridge
    private val callManager get() = app.callManager

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var destinationInput: EditText
    private lateinit var messageInput: EditText

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) sendPickedPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPermissions.request(this)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        logView = findViewById(R.id.messageLog)
        logScroll = (logView.parent as ScrollView)
        destinationInput = findViewById(R.id.destinationInput)
        messageInput = findViewById(R.id.messageInput)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        )

        bridge.events = object : FreeNetEvents {
            override fun onTextMessage(fromNodeId: String, text: String) {
                appendLog("${shortId(fromNodeId)}: $text")
            }

            override fun onFileStarted(fromNodeId: String, fileId: String, fileName: String, mediaKind: String, totalChunks: Int) {
                appendLog("${shortId(fromNodeId)} is sending $mediaKind \"$fileName\" ($totalChunks chunks)...")
            }

            override fun onFileProgress(fileId: String, receivedChunks: Int, totalChunks: Int) {
                // Intentionally not logged per-chunk to keep the log readable;
                // wire this up to a progress bar in a real UI.
            }

            override fun onFileReceived(fromNodeId: String, mediaKind: String, localFile: File, checksumOk: Boolean) {
                val integrity = if (checksumOk) "ok" else "CHECKSUM MISMATCH"
                appendLog("${shortId(fromNodeId)} sent $mediaKind -> ${localFile.name} [$integrity]")
            }

            override fun onCallSignal(fromNodeId: String, signal: MessageBody.CallSignal) {
                callManager.onSignal(fromNodeId, signal)
                if (signal.kind == com.freenet.mobile.mesh.protocol.MessageType.CALL_INVITE) {
                    runOnUiThread {
                        startActivity(Intent(this@MainActivity, CallActivity::class.java).apply {
                            putExtra(CallActivity.EXTRA_PEER_NODE_ID, fromNodeId)
                            putExtra(CallActivity.EXTRA_INCOMING, true)
                            putExtra(CallActivity.EXTRA_WITH_VIDEO, signal.withVideo)
                        })
                    }
                }
            }

            override fun onNeighborsChanged() {
                runOnUiThread { statusView.text = "FreeNet running — ${bridge.engine.pendingCount()} queued" }
            }
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            bridge.start()
            statusView.text = "FreeNet bridge started (node ${shortId(bridge.engine.nodeId())})"
        }

        findViewById<Button>(R.id.scanButton).setOnClickListener {
            bridge.discover()
            val sent = bridge.flushQueue()
            appendLog("Discovery triggered; flushed $sent queued packet(s)")
        }

        findViewById<Button>(R.id.sendTextButton).setOnClickListener {
            val text = messageInput.text.toString()
            if (text.isBlank()) return@setOnClickListener
            val result = bridge.sendText(destination(), text)
            appendLog("me -> ${destinationLabel()}: $text  [${result.detail}]")
            messageInput.text.clear()
        }

        findViewById<Button>(R.id.sendPhotoButton).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.voiceButton).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startVoiceRecording()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopVoiceRecordingAndSend()
            }
            true
        }

        findViewById<Button>(R.id.audioCallButton).setOnClickListener { launchOutgoingCall(video = false) }
        findViewById<Button>(R.id.videoCallButton).setOnClickListener { launchOutgoingCall(video = true) }
    }

    private fun launchOutgoingCall(video: Boolean) {
        val to = destination() ?: run {
            appendLog("Enter a destination node id before calling")
            return
        }
        startActivity(Intent(this, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_PEER_NODE_ID, to)
            putExtra(CallActivity.EXTRA_INCOMING, false)
            putExtra(CallActivity.EXTRA_WITH_VIDEO, video)
        })
    }

    private val mediaStore by lazy { com.freenet.mobile.mesh.media.MediaStore(this) }

    private fun sendPickedPhoto(uri: Uri) {
        val bytes = mediaStore.readBytes(uri) ?: run {
            appendLog("Could not read the picked photo")
            return
        }
        val results = bridge.sendPhoto(destination(), bytes)
        appendLog("me -> ${destinationLabel()}: [photo, ${bytes.size} bytes, ${results.size} envelopes]")
    }

    private fun startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 9200)
            return
        }
        val file = mediaStore.newRecordingFile()
        recordingFile = file
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            try { prepare(); start() } catch (_: Exception) { /* device without a mic, etc. */ }
        }
        appendLog("Recording voice note...")
    }

    private fun stopVoiceRecordingAndSend() {
        val file = recordingFile ?: return
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        recordingFile = null

        val bytes = try { file.readBytes() } catch (_: Exception) { null } ?: return
        val results = bridge.sendVoiceNote(destination(), bytes)
        appendLog("me -> ${destinationLabel()}: [voice note, ${bytes.size} bytes, ${results.size} envelopes]")
    }

    private fun destination(): String? = destinationInput.text.toString().trim().ifBlank { null }
    private fun destinationLabel() = destination()?.let { shortId(it) } ?: "broadcast"
    private fun shortId(nodeId: String) = if (nodeId.length > 10) nodeId.take(10) else nodeId

    private fun appendLog(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        runOnUiThread {
            logView.append("[$time] $line\n")
            logScroll.post { logScroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        bridge.stop()
        super.onDestroy()
    }
}
