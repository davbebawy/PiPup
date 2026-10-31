package nl.rogro82.pipup

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock

/// Foreground app push (X1): which app is in front (live TV, Netflix, YouTube).
///
/// Opt-in (`POST /settings?foregroundApp=true`) and only with the accessibility service
/// enabled: then the service subscribes to window-state changes at runtime. It still never
/// reads window content (no canRetrieveWindowContent); it only takes the package and class
/// name of the window that came to the front.
///
/// Not every window-state change is an app: dialogs, the keyboard, system UI and PiPup's own
/// popups fire it too. [Tracker] keeps only windows that are an activity of another package,
/// and reports a change once per package.
object ForegroundApp {

    /// Decides what counts as the foreground app; pure, so it is unit tested.
    class Tracker(
        private val ownPackage: String,
        private val isActivity: (pkg: String, cls: String) -> Boolean
    ) {
        @Volatile var current: String? = null
            private set
        @Volatile var previous: String? = null
            private set

        /// A window came to the front; returns true when the foreground app changed.
        @Synchronized
        fun onWindow(pkg: String?, cls: String?): Boolean {
            if (pkg.isNullOrEmpty() || cls.isNullOrEmpty()) return false
            if (pkg == ownPackage || pkg in IGNORED) return false
            if (pkg == current) return false
            if (!isActivity(pkg, cls)) return false
            previous = current
            current = pkg
            return true
        }

        @Synchronized
        fun reset() { current = null; previous = null }
    }

    /// Never an app the user watches, even when they own an activity (system UI's recents).
    private val IGNORED = setOf("com.android.systemui")

    /// Whether the controller asked for it (prefs, set by the service).
    @Volatile var enabled = false
    @Volatile private var changedAt = 0L
    /// Called on a change, on the accessibility service's (main) thread.
    @Volatile var onChange: (() -> Unit)? = null

    private var tracker: Tracker? = null

    fun tracker(context: Context): Tracker = tracker ?: Tracker(context.packageName) { pkg, cls ->
        isActivity(context, pkg, cls)
    }.also { tracker = it }

    /// From the accessibility service.
    fun onWindow(context: Context, pkg: String?, cls: String?) {
        if (!enabled) return
        if (tracker(context).onWindow(pkg, cls)) {
            changedAt = SystemClock.elapsedRealtime()
            onChange?.invoke()
        }
    }

    fun reset() {
        tracker?.reset()
        changedAt = 0L
    }

    /// For `/state.foregroundApp`.
    fun state(context: Context): Map<String, Any?> {
        val t = tracker
        return mapOf(
            "enabled" to enabled,
            // false = enabled, but the accessibility service is not bound: nothing arrives
            "available" to PiPupAccessibilityService.connected(),
            "package" to t?.current,
            "previous" to t?.previous,
            "secondsAgo" to changedAt.takeIf { it > 0 }
                ?.let { (SystemClock.elapsedRealtime() - it) / 1000 }
        )
    }

    /// An activity of [pkg], or (when package visibility hides its activities) at least
    /// an app that can be launched: a keyboard or a dialog host is neither.
    private fun isActivity(context: Context, pkg: String, cls: String): Boolean {
        val pm = context.packageManager
        try {
            pm.getActivityInfo(ComponentName(pkg, cls), 0)
            return true
        } catch (_: PackageManager.NameNotFoundException) {
        } catch (_: Throwable) {
            return false
        }
        return try {
            pm.getLeanbackLaunchIntentForPackage(pkg) != null ||
                pm.getLaunchIntentForPackage(pkg) != null
        } catch (_: Throwable) {
            false
        }
    }
}
