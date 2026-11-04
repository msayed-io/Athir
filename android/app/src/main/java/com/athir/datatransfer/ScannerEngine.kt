package com.athir.datatransfer

import android.app.Activity
import android.content.Context
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
 * CameraX + ML Kit QR scanner.
 *
 * Two defects used to live here and both produced the reported "black scan screen":
 *
 *  1. only an `ImageAnalysis` use case was bound - there was no `Preview` at all, so no
 *     camera frame was ever drawn. The scan page showed the page background (black) and
 *     the user could not tell whether the camera was even on;
 *  2. nothing checked the CAMERA runtime permission before binding, so the bind could fail
 *     with a SecurityException that was swallowed by the generic catch.
 *
 * The preview is attached behind the WebView (index 0 of the activity content view) and the
 * WebView is made transparent while scanning, so the HTML overlay (scan frame, sheet) keeps
 * rendering on top of live camera frames without changing the design.
 */
class ScannerEngine(private val context: Context) {

    interface ScannerListener {
        fun onQrScanned(rawContent: String, parsedPayload: JSONObject?)
        fun onScanError(error: String)
    }

    private var cameraExecutor: ExecutorService? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val isScanning = AtomicBoolean(false)

    private var previewView: PreviewView? = null

    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        hostActivity: Activity?,
        listener: ScannerListener
    ) {
        if (isScanning.get()) return
        isScanning.set(true)

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

                val preview = Preview.Builder().build()
                val attached = attachPreview(hostActivity)
                if (attached != null) {
                    preview.setSurfaceProvider(attached.surfaceProvider)
                }

                cameraProvider?.unbindAll()
                if (attached != null) {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } else {
                    // No host view (should not happen in the app): analysis still runs, but
                    // the caller is told the preview could not be displayed instead of
                    // pretending the camera is visible.
                    cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                    listener.onScanError("Camera preview view could not be attached")
                }
            } catch (e: SecurityException) {
                isScanning.set(false)
                listener.onScanError("Camera permission is not granted")
            } catch (e: Exception) {
                isScanning.set(false)
                listener.onScanError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Adds a live PreviewView behind the WebView and returns it. */
    private fun attachPreview(hostActivity: Activity?): PreviewView? {
        val activity = hostActivity ?: return null
        return try {
            val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
            val existing = previewView
            if (existing != null && existing.parent === root) return existing

            val view = PreviewView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                // TextureView-backed: composites correctly underneath the WebView.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            root.addView(view, 0)
            previewView = view
            view
        } catch (e: Exception) {
            null
        }
    }

    private fun detachPreview() {
        val view = previewView ?: return
        try {
            (view.parent as? ViewGroup)?.removeView(view)
        } catch (ignored: Exception) {
        }
        previewView = null
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
        detachPreview()
        cameraExecutor = null
        barcodeScanner = null
        cameraProvider = null
    }
}
