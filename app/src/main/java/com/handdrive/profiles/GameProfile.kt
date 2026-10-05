package com.handdrive.profiles

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Normalized point on screen: x,y in 0..1 relative to full display. */
data class NormPoint(val x: Float, val y: Float) {
    fun toPixel(screenW: Float, screenH: Float): Pair<Float, Float> =
        x.coerceIn(0f, 1f) * screenW to y.coerceIn(0f, 1f) * screenH

    fun toJson(): JSONObject = JSONObject().put("x", x.toDouble()).put("y", y.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = NormPoint(
            o.optDouble("x", 0.5).toFloat(),
            o.optDouble("y", 0.5).toFloat()
        )
        fun fromPixel(px: Float, py: Float, screenW: Float, screenH: Float) = NormPoint(
            if (screenW > 0) (px / screenW).coerceIn(0f, 1f) else 0.5f,
            if (screenH > 0) (py / screenH).coerceIn(0f, 1f) else 0.5f
        )
    }
}

enum class ScreenOrientation { PORTRAIT, LANDSCAPE }

enum class ThrottleMode { HOLD, TAP }

enum class CompatibilityStatus {
    UNTESTED,
    WORKS,
    PARTIALLY_WORKS,
    NOT_COMPATIBLE
}

enum class ControlActivation { TAP, HOLD }

data class CustomControl(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val point: NormPoint,
    val activation: ControlActivation = ControlActivation.TAP
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("point", point.toJson())
        .put("activation", activation.name)

    companion object {
        fun fromJson(o: JSONObject) = CustomControl(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Custom"),
            point = NormPoint.fromJson(o.getJSONObject("point")),
            activation = runCatching {
                ControlActivation.valueOf(o.optString("activation", "TAP"))
            }.getOrDefault(ControlActivation.TAP)
        )
    }
}

/**
 * Per-game control layout in normalized coordinates.
 * Steering uses center + left + right endpoints.
 */
data class ControlLayout(
    val steeringCenter: NormPoint = NormPoint(0.25f, 0.75f),
    val steeringLeft: NormPoint = NormPoint(0.10f, 0.75f),
    val steeringRight: NormPoint = NormPoint(0.40f, 0.75f),
    val brake: NormPoint = NormPoint(0.85f, 0.80f),
    val throttle: NormPoint = NormPoint(0.70f, 0.80f),
    val customControls: List<CustomControl> = emptyList(),
    val calibrated: Boolean = false,
    val calibrationScreenWidth: Int = 0,
    val calibrationScreenHeight: Int = 0,
    val calibrationOrientation: ScreenOrientation = ScreenOrientation.PORTRAIT
) {
    fun isSteeringCalibrated(): Boolean =
        calibrated && (steeringLeft.x != steeringRight.x || steeringLeft.y != steeringRight.y)

    fun toJson(): JSONObject = JSONObject()
        .put("steeringCenter", steeringCenter.toJson())
        .put("steeringLeft", steeringLeft.toJson())
        .put("steeringRight", steeringRight.toJson())
        .put("brake", brake.toJson())
        .put("throttle", throttle.toJson())
        .put("customControls", JSONArray().also { arr ->
            customControls.forEach { arr.put(it.toJson()) }
        })
        .put("calibrated", calibrated)
        .put("calibrationScreenWidth", calibrationScreenWidth)
        .put("calibrationScreenHeight", calibrationScreenHeight)
        .put("calibrationOrientation", calibrationOrientation.name)

    companion object {
        fun fromJson(o: JSONObject): ControlLayout {
            val customs = mutableListOf<CustomControl>()
            val arr = o.optJSONArray("customControls")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    customs.add(CustomControl.fromJson(arr.getJSONObject(i)))
                }
            }
            return ControlLayout(
                steeringCenter = NormPoint.fromJson(o.getJSONObject("steeringCenter")),
                steeringLeft = NormPoint.fromJson(o.getJSONObject("steeringLeft")),
                steeringRight = NormPoint.fromJson(o.getJSONObject("steeringRight")),
                brake = NormPoint.fromJson(o.getJSONObject("brake")),
                throttle = NormPoint.fromJson(o.getJSONObject("throttle")),
                customControls = customs,
                calibrated = o.optBoolean("calibrated", false),
                calibrationScreenWidth = o.optInt("calibrationScreenWidth", 0),
                calibrationScreenHeight = o.optInt("calibrationScreenHeight", 0),
                calibrationOrientation = runCatching {
                    ScreenOrientation.valueOf(o.optString("calibrationOrientation", "PORTRAIT"))
                }.getOrDefault(ScreenOrientation.PORTRAIT)
            )
        }
    }
}

/**
 * Full game profile: layout + per-profile steering/throttle settings.
 */
data class GameProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val layout: ControlLayout = ControlLayout(),
    val sensitivity: Float = 1.0f,
    val smoothing: Float = 0.35f,
    val deadZone: Float = 0.08f,
    val invertSteering: Boolean = false,
    val maxAngleDegrees: Float = 90f,
    val throttleMode: ThrottleMode = ThrottleMode.HOLD,
    val compatibilityStatus: CompatibilityStatus = CompatibilityStatus.UNTESTED,
    val compatibilityNotes: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    /** True when steering range, brake, and throttle have been calibrated. */
    fun isReady(): Boolean =
        layout.calibrated && layout.isSteeringCalibrated()

    fun readinessIssues(): List<String> {
        val issues = mutableListOf<String>()
        if (!layout.calibrated) issues.add("Controls not calibrated")
        if (!layout.isSteeringCalibrated()) issues.add("Steering range incomplete")
        return issues
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("layout", layout.toJson())
        .put("sensitivity", sensitivity.toDouble())
        .put("smoothing", smoothing.toDouble())
        .put("deadZone", deadZone.toDouble())
        .put("invertSteering", invertSteering)
        .put("maxAngleDegrees", maxAngleDegrees.toDouble())
        .put("throttleMode", throttleMode.name)
        .put("compatibilityStatus", compatibilityStatus.name)
        .put("compatibilityNotes", compatibilityNotes)
        .put("createdAtMs", createdAtMs)
        .put("updatedAtMs", updatedAtMs)

    companion object {
        fun fromJson(o: JSONObject) = GameProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Unnamed"),
            layout = ControlLayout.fromJson(o.getJSONObject("layout")),
            sensitivity = o.optDouble("sensitivity", 1.0).toFloat(),
            smoothing = o.optDouble("smoothing", 0.35).toFloat(),
            deadZone = o.optDouble("deadZone", 0.08).toFloat(),
            invertSteering = o.optBoolean("invertSteering", false),
            maxAngleDegrees = o.optDouble("maxAngleDegrees", 90.0).toFloat(),
            throttleMode = runCatching {
                ThrottleMode.valueOf(o.optString("throttleMode", "HOLD"))
            }.getOrDefault(ThrottleMode.HOLD),
            compatibilityStatus = runCatching {
                CompatibilityStatus.valueOf(o.optString("compatibilityStatus", "UNTESTED"))
            }.getOrDefault(CompatibilityStatus.UNTESTED),
            compatibilityNotes = o.optString("compatibilityNotes", ""),
            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
            updatedAtMs = o.optLong("updatedAtMs", System.currentTimeMillis())
        )

        fun create(name: String) = GameProfile(name = name.trim().ifEmpty { "New Game" })
    }
}
