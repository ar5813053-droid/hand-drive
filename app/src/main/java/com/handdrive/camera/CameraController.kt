package com.handdrive.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.handdrive.domain.CameraFacing
import com.handdrive.tracking.HandTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle-aware CameraX controller.
 * Owns Preview + ImageAnalysis. Hands frames to HandTracker.
 * Active only while Controller is running.
 */
class CameraController(
    private val context: Context,
    private val handTracker: HandTracker
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private val isBound = AtomicBoolean(false)
    private var currentFacing: CameraFacing = CameraFacing.FRONT

    var onCameraReady: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing
    ) {
        currentFacing = facing
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bind(lifecycleOwner, previewView, facing)
            } catch (e: Exception) {
                onError?.invoke("Camera init failed: ${e.message}")
                onCameraReady?.invoke(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing
    ) {
        currentFacing = facing
        if (cameraProvider != null) {
            bind(lifecycleOwner, previewView, facing)
        } else {
            start(lifecycleOwner, previewView, facing)
        }
    }

    fun stop() {
        isBound.set(false)
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        analysisExecutor?.shutdown()
        analysisExecutor = null
        onCameraReady?.invoke(false)
    }

    private fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing
    ) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()
            analysisExecutor?.shutdown()
            analysisExecutor = Executors.newSingleThreadExecutor()

            val selector = if (facing == CameraFacing.FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            analysis.setAnalyzer(analysisExecutor!!) { imageProxy ->
                if (!isBound.get()) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                handTracker.analyze(imageProxy, isFrontCamera = facing == CameraFacing.FRONT)
            }

            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            isBound.set(true)
            onCameraReady?.invoke(true)
        } catch (e: Exception) {
            isBound.set(false)
            onError?.invoke("Camera bind failed: ${e.message}")
            onCameraReady?.invoke(false)
        }
    }

    fun isActive(): Boolean = isBound.get()
}
