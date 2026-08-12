package com.freenet.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.freenet.mobile.R
import com.freenet.mobile.mesh.call.DirectCallManager

/**
 * Started either by the user placing a call, or by MainActivity when an
 * incoming CALL_INVITE arrives. Which mode it's in is passed via extras;
 * DirectCallManager itself is held by MainActivity/the app singleton so a
 * call started before this Activity exists (an invite arriving while the
 * app is backgrounded) still has somewhere to go.
 */
class CallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PEER_NODE_ID = "peer_node_id"
        const val EXTRA_INCOMING = "incoming"
        const val EXTRA_WITH_VIDEO = "with_video"
    }

    private lateinit var callManager: DirectCallManager
    private lateinit var statusText: TextView
    private lateinit var remoteView: ImageView
    private lateinit var localPreview: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        callManager = CallManagerHolder.get(this)

        statusText = findViewById(R.id.callStatus)
        remoteView = findViewById(R.id.remoteVideo)
        localPreview = findViewById(R.id.localPreview)

        val incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)
        val withVideo = intent.getBooleanExtra(EXTRA_WITH_VIDEO, false)
        val peer = intent.getStringExtra(EXTRA_PEER_NODE_ID)

        findViewById<Button>(R.id.endButton).setOnClickListener {
            callManager.endCall(); finish()
        }
        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            if (hasCallPermissions(withVideo)) {
                callManager.acceptCall()
                findViewById<Button>(R.id.acceptButton).isEnabled = false
                findViewById<Button>(R.id.rejectButton).isEnabled = false
            }
        }
        findViewById<Button>(R.id.rejectButton).setOnClickListener {
            callManager.rejectCall(); finish()
        }

        callManager.onCallConnected = { runOnUiThread { statusText.text = "Connected" } }
        callManager.onCallEnded = { reason -> runOnUiThread { statusText.text = "Call ended: $reason"; finish() } }
        callManager.onCallFailed = { reason -> runOnUiThread { statusText.text = "Call failed: $reason" } }
        callManager.onRemoteVideoFrame = { jpeg ->
            val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            if (bmp != null) runOnUiThread { remoteView.setImageBitmap(bmp) }
        }

        localPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                callManager.attachLocalPreview(Surface(texture))
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                callManager.attachLocalPreview(null)
                return true
            }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
        }

        if (incoming && peer != null) {
            statusText.text = "Incoming call from $peer"
            findViewById<Button>(R.id.acceptButton).visibility = android.view.View.VISIBLE
            findViewById<Button>(R.id.rejectButton).visibility = android.view.View.VISIBLE
        } else if (peer != null) {
            statusText.text = "Calling $peer..."
            findViewById<Button>(R.id.acceptButton).visibility = android.view.View.GONE
            findViewById<Button>(R.id.rejectButton).visibility = android.view.View.GONE
            if (hasCallPermissions(withVideo)) {
                callManager.startCall(peer, withVideo)
            } else {
                statusText.text = "Missing microphone/camera permission"
            }
        }
    }

    private fun hasCallPermissions(withVideo: Boolean): Boolean {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (withVideo) needed += Manifest.permission.CAMERA
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 9100)
            return false
        }
        return true
    }
}
