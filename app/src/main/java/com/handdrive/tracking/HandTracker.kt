package com.handdrive.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.handdrive.domain.Landmark
import com.handdrive.domain.TrackingResult
import com.handdrive.domain.TrackingState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device MediaPipe Hand Landmarker wrapper.
 * Processes CameraX ImageProxy frames and emits TrackingResult.
 * Does NOT contain steering or gesture logic.
 */
class HandTracker(context: Context) {

    private val appContext = context.applicationContext
    private var landmarker: HandLandmarker? = null
    private val closed = AtomicBoolean(false)

    @Volatile
    var lastResult: TrackingResult = TrackingResult.lost()
        private set

    fun initialize(): Boolean {
        if (landmarker != null) return true
        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .setDelegate(Delegate.GPU)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ -> onResult(result) }
                .setErrorListener { e -> onError(e) }
                .build()
            landmarker = HandLandmarker.createFromOptions(appContext, options)
            true
        } catch (gpuEx: Exception) {
            // Fallback to CPU
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build()
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f)
                    .setMinHandPresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener { result, _ -> onResult(result) }
                    .setErrorListener { e -> onError(e) }
                    .build()
                landmarker = HandLandmarker.createFromOptions(appContext, options)
                true
            } catch (cpuEx: Exception) {
                lastResult = TrackingResult.lost()
                false
            }
        }
    }

    /**
     * Analyze one camera frame. Always closes the ImageProxy.
     * @param isFrontCamera true when using front lens (mirroring needed)
     */
    fun analyze(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (closed.get() || landmarker == null) {
            imageProxy.close()
            return
        }
        try {
            val bitmap = imageProxyToBitmap(imageProxy, isFrontCamera) ?: run {
                imageProxy.close()
                return
            }
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            val ts = SystemClock.uptimeMillis()
            landmarker?.detectAsync(mpImage, ts)
        } catch (_: Exception) {
            lastResult = TrackingResult.lost()
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        closed.set(true)
        try {
            landmarker?.close()
        } catch (_: Exception) {
        }
        landmarker = null
        lastResult = TrackingResult.lost()
    }

    private fun onResult(result: HandLandmarkerResult) {
        val now = System.currentTimeMillis()
        if (result.landmarks().isEmpty()) {
            lastResult = TrackingResult(
                landmarks = emptyList(),
                confidence = 0f,
                handedness = null,
                timestampMs = now,
                state = TrackingState.LOST
            )
            return
        }
        val hand = result.landmarks()[0]
        val landmarks = hand.map { Landmark(it.x(), it.y(), it.z()) }
        val handedness = result.handednesses()
            .firstOrNull()
            ?.firstOrNull()
            ?.categoryName()
        val score = result.handednesses()
            .firstOrNull()
            ?.firstOrNull()
            ?.score()
            ?: 0.7f

        val state = when {
            score < TrackingResult.MIN_CONFIDENCE -> TrackingState.LOW_CONFIDENCE
            else -> TrackingState.TRACKING
        }
        lastResult = TrackingResult(
            landmarks = landmarks,
            confidence = score,
            handedness = handedness,
            timestampMs = now,
            state = state
        )
    }

    private fun onError(e: RuntimeException) {
        lastResult = TrackingResult.lost()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // MediaPipe Tasks expects ARGB bitmap. CameraX RGBA_8888 analysis format.
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // RGBA_8888 buffer → copy into bitmap
        val intPixels = IntArray(width * height)
        var i = 0
        var p = 0
        while (i + 3 < bytes.size && p < intPixels.size) {
            val r = bytes[i].toInt() and 0xFF
            val g = bytes[i + 1].toInt() and 0xFF
            val b = bytes[i + 2].toInt() and 0xFF
            val a = bytes[i + 3].toInt() and 0xFF
            intPixels[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
            i += 4
            p++
        }
        bitmap.setPixels(intPixels, 0, width, 0, 0, width, height)

        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation == 0 && !isFrontCamera) return bitmap

        val matrix = Matrix()
        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat())
        }
        if (isFrontCamera) {
            // Mirror horizontally for natural selfie view / landmark coords
            matrix.postScale(-1f, 1f)
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
    }
}
