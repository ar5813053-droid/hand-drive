package com.handdrive.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.handdrive.profiles.NormPoint
import com.handdrive.profiles.ScreenOrientation

/**
 * Lightweight TYPE_ACCESSIBILITY_OVERLAY calibration UI.
 * Shown only during calibration; removed when finished/cancelled.
 */
class CalibrationOverlayManager(
    private val service: HandDriveAccessibilityService
) {
    enum class Step {
        STEERING_CENTER,
        STEERING_LEFT,
        STEERING_RIGHT,
        BRAKE,
        THROTTLE,
        DONE
    }

    data class Result(
        val steeringCenter: NormPoint,
        val steeringLeft: NormPoint,
        val steeringRight: NormPoint,
        val brake: NormPoint,
        val throttle: NormPoint,
        val screenWidth: Int,
        val screenHeight: Int,
        val orientation: ScreenOrientation
    )

    private var root: FrameLayout? = null
    private var marker: View? = null
    private var titleView: TextView? = null
    private var coordView: TextView? = null
    private var step = Step.STEERING_CENTER
    private val points = mutableMapOf<Step, Pair<Float, Float>>()
    private var screenW = 1080
    private var screenH = 1920
    private var orientation = ScreenOrientation.PORTRAIT

    var onFinished: ((Result) -> Unit)? = null
    var onCancelled: (() -> Unit)? = null

    fun show() {
        if (root != null) return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        orientation = if (screenW > screenH) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val frame = FrameLayout(service).apply {
            setBackgroundColor(Color.argb(80, 0, 0, 0))
        }

        // Instruction panel
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.argb(220, 20, 20, 30))
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.TOP
            layoutParams = lp
        }

        titleView = TextView(service).apply {
            text = stepTitle()
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        panel.addView(titleView)

        val hint = TextView(service).apply {
            text = "Drag the marker to the control, then Save."
            setTextColor(Color.LTGRAY)
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        panel.addView(hint)

        val saveBtn = Button(service).apply {
            text = "Save Position"
            setOnClickListener { saveCurrent() }
        }
        panel.addView(saveBtn)

        val cancelBtn = Button(service).apply {
            text = "Cancel"
            setOnClickListener { dismiss(cancelled = true) }
        }
        panel.addView(cancelBtn)

        val coordLabel = TextView(service).apply {
            text = "X: —  Y: —"
            setTextColor(Color.CYAN)
            textSize = 13f
            setPadding(0, 12, 0, 0)
        }
        panel.addView(coordLabel)
        coordView = coordLabel

        frame.addView(panel)

        // Draggable crosshair marker
        val markerSize = (56 * metrics.density).toInt()
        val markerView = View(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 0, 230, 118))
                setStroke((3 * metrics.density).toInt(), Color.WHITE)
            }
            val lp = FrameLayout.LayoutParams(markerSize, markerSize)
            lp.leftMargin = screenW / 2 - markerSize / 2
            lp.topMargin = screenH / 2 - markerSize / 2
            layoutParams = lp
        }
        setupDrag(markerView, markerSize)
        frame.addView(markerView)
        marker = markerView

        try {
            wm.addView(frame, params)
            root = frame
            step = Step.STEERING_CENTER
            titleView?.text = stepTitle()
        } catch (e: Exception) {
            onCancelled?.invoke()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(view: View, size: Int) {
        var dX = 0f
        var dY = 0f
        view.setOnTouchListener { v, event ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = event.rawX - lp.leftMargin
                    dY = event.rawY - lp.topMargin
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.leftMargin = (event.rawX - dX).toInt().coerceIn(0, screenW - size)
                    lp.topMargin = (event.rawY - dY).toInt().coerceIn(0, screenH - size)
                    v.layoutParams = lp
                    val nx = (lp.leftMargin + size / 2f) / screenW
                    val ny = (lp.topMargin + size / 2f) / screenH
                    coordView?.text = "X: %.2f  Y: %.2f  (%d×%d)".format(nx, ny, screenW, screenH)
                    true
                }
                else -> false
            }
        }
    }

    private fun markerCenter(): Pair<Float, Float> {
        val v = marker ?: return screenW / 2f to screenH / 2f
        val lp = v.layoutParams as FrameLayout.LayoutParams
        val cx = lp.leftMargin + v.width / 2f
        val cy = lp.topMargin + v.height / 2f
        return cx to cy
    }

    private fun saveCurrent() {
        points[step] = markerCenter()
        step = when (step) {
            Step.STEERING_CENTER -> Step.STEERING_LEFT
            Step.STEERING_LEFT -> Step.STEERING_RIGHT
            Step.STEERING_RIGHT -> Step.BRAKE
            Step.BRAKE -> Step.THROTTLE
            Step.THROTTLE -> Step.DONE
            Step.DONE -> Step.DONE
        }
        if (step == Step.DONE) {
            finish()
        } else {
            titleView?.text = stepTitle()
        }
    }

    private fun finish() {
        fun pt(s: Step): NormPoint {
            val (px, py) = points[s] ?: (screenW / 2f to screenH / 2f)
            return NormPoint.fromPixel(px, py, screenW.toFloat(), screenH.toFloat())
        }
        val result = Result(
            steeringCenter = pt(Step.STEERING_CENTER),
            steeringLeft = pt(Step.STEERING_LEFT),
            steeringRight = pt(Step.STEERING_RIGHT),
            brake = pt(Step.BRAKE),
            throttle = pt(Step.THROTTLE),
            screenWidth = screenW,
            screenHeight = screenH,
            orientation = orientation
        )
        dismiss(cancelled = false)
        onFinished?.invoke(result)
    }

    fun dismiss(cancelled: Boolean) {
        val frame = root ?: return
        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(frame)
        } catch (_: Exception) {
        }
        root = null
        marker = null
        titleView = null
        if (cancelled) onCancelled?.invoke()
    }

    private fun stepTitle(): String = when (step) {
        Step.STEERING_CENTER -> "Step 1/5 — Steering Center (landscape)"
        Step.STEERING_LEFT -> "Step 2/5 — Place Steering Left"
        Step.STEERING_RIGHT -> "Step 3/5 — Place Steering Right"
        Step.BRAKE -> "Step 4/5 — Place Brake"
        Step.THROTTLE -> "Step 5/5 — Place Throttle"
        Step.DONE -> "Done"
    }
}
