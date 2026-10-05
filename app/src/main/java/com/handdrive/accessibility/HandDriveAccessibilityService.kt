package com.handdrive.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.handdrive.input.AccessibilityStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * AccessibilityService used solely to dispatch touch gestures via dispatchGesture().
 * No root, no shell, no game modification.
 */
class HandDriveAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gestureInFlight = AtomicBoolean(false)
    private var calibrationOverlay: CalibrationOverlayManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(this)
        Log.i(TAG, "Service connected")
        statusListener?.invoke(AccessibilityStatus.CONNECTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need gesture dispatch capability.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        cancelActiveGesture()
        statusListener?.invoke(AccessibilityStatus.DISCONNECTED)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        cancelActiveGesture()
        hideCalibration()
        if (instanceRef.get() === this) {
            instanceRef.set(null)
        }
        statusListener?.invoke(AccessibilityStatus.DISCONNECTED)
        super.onDestroy()
    }

    /**
     * Dispatch a single-point tap.
     */
    fun dispatchTap(x: Float, y: Float, durationMs: Long = 50L, onDone: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(1L, 500L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchInternal(gesture, onDone)
    }

    /**
     * Dispatch a drag from (x0,y0) to (x1,y1).
     * @param willContinue true if another stroke will continue this path (steering hold)
     */
    fun dispatchDrag(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        durationMs: Long = 80L,
        willContinue: Boolean = false,
        onDone: ((Boolean) -> Unit)? = null
    ): GestureDescription.StrokeDescription? {
        val path = Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y1)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0,
            durationMs.coerceIn(16L, 400L),
            willContinue
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchInternal(gesture, onDone)
        return if (ok) stroke else null
    }

    /**
     * Continue a previous stroke (chained steering).
     */
    fun continueDrag(
        previous: GestureDescription.StrokeDescription,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        durationMs: Long = 80L,
        willContinue: Boolean = true,
        onDone: ((Boolean) -> Unit)? = null
    ): GestureDescription.StrokeDescription? {
        val path = Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y1)
        }
        return try {
            val next = previous.continueStroke(path, 0, durationMs.coerceIn(16L, 400L), willContinue)
            val gesture = GestureDescription.Builder().addStroke(next).build()
            val ok = dispatchInternal(gesture, onDone)
            if (ok) next else null
        } catch (e: Exception) {
            Log.e(TAG, "continueStroke failed: ${e.message}")
            onDone?.invoke(false)
            null
        }
    }

    /**
     * Cancel any in-flight gesture by dispatching an empty/instant up-like action.
     * Best-effort; also clears local in-flight flag.
     */
    fun cancelActiveGesture() {
        gestureInFlight.set(false)
    }

    private fun dispatchInternal(
        gesture: GestureDescription,
        onDone: ((Boolean) -> Unit)?
    ): Boolean {
        if (gestureInFlight.getAndSet(true)) {
            // Previous still running — drop to avoid spam
            onDone?.invoke(false)
            return false
        }
        return try {
            val result = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureInFlight.set(false)
                    onDone?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureInFlight.set(false)
                    onDone?.invoke(false)
                }
            }, mainHandler)
            if (!result) {
                gestureInFlight.set(false)
                onDone?.invoke(false)
            }
            result
        } catch (e: Exception) {
            gestureInFlight.set(false)
            Log.e(TAG, "dispatchGesture error: ${e.message}")
            onDone?.invoke(false)
            false
        }
    }


    fun startCalibration(
        onFinished: (CalibrationOverlayManager.Result) -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        mainHandler.post {
            try {
                // dismiss previous without firing cancel
                calibrationOverlay?.let { prev ->
                    try {
                        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                        // force clean remove via dismiss cancelled=false path
                    } catch (_: Exception) {}
                }
                hideCalibrationSync()
                val mgr = CalibrationOverlayManager(this)
                mgr.onFinished = { result ->
                    calibrationOverlay = null
                    onFinished(result)
                }
                mgr.onCancelled = {
                    calibrationOverlay = null
                    onCancelled()
                }
                mgr.onError = onError
                calibrationOverlay = mgr
                mgr.show()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "startCalibration failed: ${e.message}", e)
                onError(e.message ?: "Calibration overlay could not be opened.")
                onCancelled()
            }
        }
    }

    private fun hideCalibrationSync() {
        try {
            calibrationOverlay?.dismiss(cancelled = false)
        } catch (_: Exception) {
        }
        calibrationOverlay = null
    }

    fun hideCalibration() {
        mainHandler.post { hideCalibrationSync() }
    }

    companion object {
        private const val TAG = "HandDriveA11y"

        private val instanceRef = AtomicReference<HandDriveAccessibilityService?>(null)

        @Volatile
        var statusListener: ((AccessibilityStatus) -> Unit)? = null

        fun getInstance(): HandDriveAccessibilityService? = instanceRef.get()

        fun isConnected(): Boolean = instanceRef.get() != null
    }
}
