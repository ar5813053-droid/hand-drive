package com.handdrive.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.handdrive.input.AccessibilityStatus

object AccessibilityHelper {

    fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expected = context.packageName + "/" + HandDriveAccessibilityService::class.java.canonicalName
        return enabled.any { it.id == expected || it.id.contains("HandDriveAccessibilityService") }
    }

    fun resolveStatus(context: Context): AccessibilityStatus {
        return when {
            HandDriveAccessibilityService.isConnected() -> AccessibilityStatus.CONNECTED
            isServiceEnabled(context) -> AccessibilityStatus.DISCONNECTED // enabled but not yet bound
            else -> AccessibilityStatus.NOT_ENABLED
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
