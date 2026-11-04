package com.athir.datatransfer

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
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

class ScannerEngine(private val context: Context) {

    interface ScannerListener {
        fun onQrScanned(rawContent: String, parsedPayload: JSONObject?)
        fun onScanError(error: String)
    }

    private var cameraExecutor: ExecutorService? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val isScanning = AtomicBoolean(false)

    fun startScanning(lifecycleOwner: LifecycleOwner, listener: ScannerListener) {
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

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
            } catch (e: Exception) {
                isScanning.set(false)
                listener.onScanError("Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
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
        cameraExecutor = null
        barcodeScanner = null
        cameraProvider = null
    }
}
