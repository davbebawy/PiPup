package nl.rogro82.pipup

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/// Fallback route for turning the screen off, for devices where the device admin
/// `lockNow()` does not reach standby. An enabled accessibility service may call
/// `GLOBAL_ACTION_LOCK_SCREEN` (Android 9 / API 28 and up). Since 0.29.0 it also sends
/// the home and back keys for POST /key.
///
/// It observes nothing by default: no event types are declared in accessibility_service.xml.
/// Only with the foreground app push turned on ([ForegroundApp], opt-in) does it subscribe to
/// window-state changes at runtime, and then it reads only the package and class name of
/// the window that came to the front, never its content.
/// Enable it once over adb (install.sh --accessibility does this):
///
///     adb shell settings put secure enabled_accessibility_services \
///         nl.rogro82.pipup/nl.rogro82.pipup.PiPupAccessibilityService
///     adb shell settings put secure accessibility_enabled 1
class PiPupAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyForegroundWatch()
        Log.d(LOG_TAG, "Accessibility service connected")
    }

    /// Subscribe to window-state changes only while the foreground app push is on.
    private fun applyForegroundWatch() {
        try {
            val info = serviceInfo ?: return
            info.eventTypes = if (ForegroundApp.enabled) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
            serviceInfo = info
        } catch (ex: Throwable) {
            Log.e(LOG_TAG, "cannot change event types: ${ex.message}")
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        ForegroundApp.onWindow(this, event.packageName?.toString(), event.className?.toString())
    }

    override fun onInterrupt() {}

    companion object {
        const val LOG_TAG = "PiPupAccessibility"

        @Volatile
        private var instance: PiPupAccessibilityService? = null

        fun connected(): Boolean = instance != null

        /// The foreground app push was switched on or off: follow it if bound.
        fun foregroundWatchChanged() {
            val service = instance ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post { service.applyForegroundWatch() }
        }

        /// Whether the service is connected *and* the platform has the lock action.
        fun available(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && instance != null

        /// Whether the user (or adb) enabled us in the accessibility settings. Read from
        /// settings rather than from `instance`, so a service that is enabled but not yet
        /// bound still reports as configured.
        fun enabledInSettings(context: Context): Boolean = try {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            enabled.split(':').any { it.substringBefore('/') == context.packageName }
        } catch (ex: Throwable) {
            Log.e(LOG_TAG, "Cannot read accessibility settings: ${ex.message}")
            false
        }

        /// 0.29.0: home/back for POST /key. Same opt-in as the screen-off fallback.
        fun globalAction(action: Int): Boolean {
            val service = instance ?: return false
            return try {
                service.performGlobalAction(action)
            } catch (ex: Throwable) {
                Log.e(LOG_TAG, "global action $action failed: ${ex.message}")
                false
            }
        }

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return try {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } catch (ex: Throwable) {
                Log.e(LOG_TAG, "GLOBAL_ACTION_LOCK_SCREEN failed: ${ex.message}")
                false
            }
        }
    }
}
