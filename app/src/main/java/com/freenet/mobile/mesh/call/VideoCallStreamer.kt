package com.freenet.mobile.mesh.call

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deliberately low-spec video: ~320x240 JPEG frames at a handful of fps,
 * sent as best-effort UDP datagrams. This is not meant to compete with a
 * real video-call codec (H.264/VP8 + RTP) — it is meant to work over a
 * single direct Wi-Fi Direct/Wi-Fi LAN hop using only framework APIs, no
 * extra native codec libraries. Treat it as a "see the other person, low
 * fidelity" feature, not a production video pipeline.
 *
 * Note: this class only *captures and sends*. Incoming remote frames are
 * received and reassembled centrally in [DirectCallManager], since both
 * audio and video share one UDP socket/receive loop.
 */
class VideoCallStreamer(
    private val context: Context,
    private val socket: DatagramSocket,
    private val peerAddress: InetAddress,
    private val peerPort: Int
) {
    companion object {
        private val TARGET_SIZE = Size(320, 240)
        private const val JPEG_QUALITY = 55
        private const val TARGET_FPS = 8
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val frameId = AtomicInteger(0)
    private var lastSentAt = 0L

    @SuppressLint("MissingPermission") // caller verifies CAMERA permission before starting a video call
    fun start(useFrontCamera: Boolean = true, localPreviewSurface: android.view.Surface? = null) {
        backgroundThread = HandlerThread("freenet-camera").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val facing = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (useFrontCamera) facing == CameraCharacteristics.LENS_FACING_FRONT
            else facing == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull() ?: return

        imageReader = ImageReader.newInstance(
            TARGET_SIZE.width, TARGET_SIZE.height, ImageFormat.YUV_420_888, 2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                handleFrame(image)
                image.close()
            }, backgroundHandler)
        }

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                val targets = listOfNotNull(imageReader!!.surface, localPreviewSurface)
                device.createCaptureSession(
                    targets,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(imageReader!!.surface)
                                localPreviewSurface?.let { addTarget(it) }
                                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(TARGET_FPS, TARGET_FPS))
                            }.build()
                            session.setRepeatingRequest(request, null, backgroundHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    backgroundHandler
                )
            }
            override fun onDisconnected(device: CameraDevice) { device.close() }
            override fun onError(device: CameraDevice, error: Int) { device.close() }
        }, backgroundHandler)
    }

    private fun handleFrame(image: Image) {
        val now = System.currentTimeMillis()
        if (now - lastSentAt < 1000 / TARGET_FPS) return // simple frame-rate cap
        lastSentAt = now

        val jpeg = yuv420ToJpeg(image) ?: return
        val id = frameId.getAndIncrement()
        for (wireFrame in MediaFrame.fragment(MediaFrame.TYPE_VIDEO, id, jpeg)) {
            try {
                socket.send(DatagramPacket(wireFrame, wireFrame.size, peerAddress, peerPort))
            } catch (_: Exception) { /* drop this frame, next one will follow shortly */ }
        }
    }

    /**
     * YUV_420_888 -> NV21. Most devices report pixelStride=2 for the U/V
     * planes (interleaved, semi-planar) rather than 1 (fully planar), so
     * this copies row-by-row/pixel-by-pixel respecting rowStride and
     * pixelStride instead of assuming a flat concatenation — a naive
     * concat looks fine on some devices and produces corrupted color on
     * others.
     */
    private fun yuv420ToJpeg(image: Image): ByteArray? {
        return try {
            val width = image.width
            val height = image.height
            val nv21 = ByteArray(width * height * 3 / 2)

            val yPlane = image.planes[0]
            var pos = 0
            val yRowStride = yPlane.rowStride
            val yBuffer = yPlane.buffer
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }

            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            val uvRowStride = vPlane.rowStride
            val uvPixelStride = vPlane.pixelStride
            val chromaHeight = height / 2
            val chromaWidth = width / 2

            for (row in 0 until chromaHeight) {
                for (col in 0 until chromaWidth) {
                    val vIndex = row * uvRowStride + col * uvPixelStride
                    val uIndex = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(vIndex) // NV21 = Y then interleaved V,U
                    nv21[pos++] = uBuffer.get(uIndex)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    fun stop() {
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        backgroundThread?.quitSafely()
        captureSession = null
        cameraDevice = null
        imageReader = null
        backgroundThread = null
        backgroundHandler = null
    }
}
