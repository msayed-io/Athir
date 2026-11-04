package com.athir.datatransfer

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX + ML Kit QR scanner with a **native camera window**.
 *
 * History of this file matters, because the "black scan screen" was reported twice:
 *
 *  - v1.0.2 bound only `ImageAnalysis`, so no camera frame was ever drawn anywhere.
 *  - v1.0.4 added a `PreviewView` *behind* the WebView and made the WebView transparent.
 *    That depends on the WebView's surface actually being translucent at runtime, which is
 *    not guaranteed (and it failed on the reporting device: the page kept painting black).
 *
 * v1.0.5 removes that dependency completely: the preview lives in its own native
 * `FrameLayout` that is added **on top** of the WebView, positioned exactly where the page
 * declares its camera window (see `setWindow`). A native scan-line view reproduces the
 * sweeping animation inside that window. Whatever the WebView does with transparency is now
 * irrelevant - if the camera runs, the image is visible by construction.
 */
class ScannerEngine(private val context: Context) {

    interface ScannerListener {
        fun onQrScanned(rawContent: String, parsedPayload: JSONObject?)
        fun onScanError(error: String)
        fun onCameraState(state: String, detail: String)
    }

    /** Rect of the camera window in CSS pixels, as reported by the scan page. */
    data class WindowRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val density: Float
    )

    private var cameraExecutor: ExecutorService? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val isScanning = AtomicBoolean(false)

    /** The WebView, used to convert page coordinates into content-view coordinates. */
    private var anchorView: View? = null
    private var hostView: FrameLayout? = null
    private var previewView: PreviewView? = null
    private var scanLine: View? = null
    private var pendingWindow: WindowRect? = null

    /** Called from the page before scanning (and again on rotation). */
    fun setWindow(rect: WindowRect?, anchor: View? = null) {
        pendingWindow = rect
        if (anchor != null) anchorView = anchor
        val host = hostView ?: return
        val activity = host.context as? Activity ?: return
        activity.runOnUiThread { applyWindowLayout(host) }
    }

    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        hostActivity: Activity?,
        listener: ScannerListener
    ) {
        if (isScanning.get()) return
        isScanning.set(true)
        listener.onCameraState("preparing", "")

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                    processImageProxy(imageProxy, listener)
                }

                val surfaceProvider = attachWindow(hostActivity)?.surfaceProvider
                if (surfaceProvider == null) {
                    isScanning.set(false)
                    listener.onCameraState("error", "Camera window could not be attached")
                    listener.onScanError("Camera window could not be attached")
                    return@addListener
                }

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(surfaceProvider)

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                // Binding both use cases succeeded, so frames are flowing into the window.
                listener.onCameraState("running", "")
            } catch (e: SecurityException) {
                isScanning.set(false)
                listener.onCameraState("error", "permission")
                listener.onScanError("Camera permission is not granted")
            } catch (e: Exception) {
                isScanning.set(false)
                listener.onCameraState("error", e.message ?: "unknown")
                listener.onScanError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Creates the native camera window (PreviewView + scan line) and puts it on top of the
     * activity content view, i.e. above the WebView.
     */
    private fun attachWindow(hostActivity: Activity?): PreviewView? {
        val activity = hostActivity ?: return null
        return try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
            val existing = previewView
            if (existing != null) return existing

            val host = FrameLayout(activity).apply {
                clipChildren = true
                clipToPadding = true
            }

            val preview = PreviewView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            host.addView(preview)

            val line = View(activity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#4592FF"), Color.TRANSPARENT)
                )
            }
            host.addView(
                line,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (2 * activity.resources.displayMetrics.density).toInt()
                )
            )

            root.addView(host) // no index -> on top of the WebView
            hostView = host
            previewView = preview
            scanLine = line
            applyWindowLayout(host)
            startScanLine(host)
            preview
        } catch (e: Exception) {
            null
        }
    }

    private fun applyWindowLayout(host: FrameLayout) {
        val rect = pendingWindow
        val metrics = host.resources.displayMetrics
        if (rect == null) {
            // Sensible default until the page reports its camera window.
            val side = (metrics.widthPixels * 0.6f).toInt()
            host.layoutParams = FrameLayout.LayoutParams(side, side).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = (metrics.heightPixels * 0.22f).toInt()
            }
        } else {
            val d = if (rect.density > 0f) rect.density else metrics.density
            // The rect arrives in CSS pixels relative to the page viewport, i.e. relative to
            // the WebView. Insets or an offset WebView would otherwise shift the window, so
            // the delta between the WebView and the native host is subtracted explicitly.
            var offsetX = 0
            var offsetY = 0
            val anchor = anchorView
            val parent = host.parent as? View
            if (anchor != null && parent != null) {
                val anchorLocation = IntArray(2)
                val parentLocation = IntArray(2)
                anchor.getLocationOnScreen(anchorLocation)
                parent.getLocationOnScreen(parentLocation)
                offsetX = anchorLocation[0] - parentLocation[0]
                offsetY = anchorLocation[1] - parentLocation[1]
            }
            host.layoutParams = FrameLayout.LayoutParams(
                (rect.width * d).toInt(),
                (rect.height * d).toInt()
            ).apply {
                leftMargin = (rect.left * d).toInt() + offsetX
                topMargin = (rect.top * d).toInt() + offsetY
            }
        }
        host.requestLayout()
    }

    private fun startScanLine(host: FrameLayout) {
        val line = scanLine ?: return
        host.post {
            val travel = (host.height - line.height).coerceAtLeast(1).toFloat()
            line.translationY = 0f
            line.animate()
                .translationY(travel)
                .setDuration(1300L)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction {
                    line.animate()
                        .translationY(0f)
                        .setDuration(1300L)
                        .withEndAction { startScanLine(host) }
                        .start()
                }
                .start()
        }
    }

    private fun detachWindow() {
        val host = hostView ?: return
        try {
            scanLine?.animate()?.cancel()
            (host.parent as? ViewGroup)?.removeView(host)
        } catch (ignored: Exception) {
        }
        hostView = null
        previewView = null
        scanLine = null
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy, listener: ScannerListener) {
        if (!isScanning.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner?.process(image)
                ?.addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val raw = barcode.rawValue ?: continue
                        var json: JSONObject? = null
                        try {
                            json = JSONObject(raw)
                        } catch (ignored: Exception) {}

                        isScanning.set(false)
                        listener.onQrScanned(raw, json)
                        break
                    }
                }
                ?.addOnFailureListener {
                    // Ignore transient analysis frame failures
                }
                ?.addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    fun stopScanning() {
        isScanning.set(false)
        try {
            cameraProvider?.unbindAll()
        } catch (ignored: Exception) {}
        try {
            barcodeScanner?.close()
        } catch (ignored: Exception) {}
        try {
            cameraExecutor?.shutdown()
        } catch (ignored: Exception) {}
        detachWindow()
        cameraExecutor = null
        barcodeScanner = null
        cameraProvider = null
    }
}
