package nl.rogro82.pipup

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.util.Log

/// TV state push (SPEC W1-W4): system properties and settings keys a controller asked to watch.
///
/// The controller sends the list (`POST /settings?watch=prop:sys.tcl.inputid,system:recent_play_channel_id`);
/// nothing vendor-specific is compiled in. A change is pushed as event `watch` with `key`,
/// `old` and `new`; `/state.watch` holds every current value, so a controller that
/// reconnects reads them all at once.
///
/// Settings keys have a change callback (a ContentObserver per key). System properties
/// have none an app can use, so they are read in-process every [PROP_POLL_MS], and only
/// while at least one property is watched; a read that returns the same value pushes
/// nothing.
object Watch {
    enum class Kind(val prefix: String) { PROP("prop"), SYSTEM("system"), SECURE("secure"), GLOBAL("global") }

    data class Key(val kind: Kind, val name: String) {
        /// The id the controller used, e.g. `prop:sys.tcl.inputid`: key of `/state.watch`.
        val id: String get() = "${kind.prefix}:$name"
    }

    const val MAX_KEYS = 32
    const val PROP_POLL_MS = 500L
    private val NAME = Regex("[A-Za-z0-9._-]{1,128}")

    /// Comma list of `<kind>:<name>`; empty or null = watch nothing. Throws
    /// IllegalArgumentException with a message for the controller on a bad entry.
    fun parse(spec: String?): List<Key> {
        if (spec.isNullOrBlank()) return emptyList()
        val keys = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val prefix = entry.substringBefore(':', "")
            val name = entry.substringAfter(':', "")
            val kind = Kind.values().firstOrNull { it.prefix == prefix.lowercase() }
                ?: throw IllegalArgumentException("watch entry '$entry' must start with prop:, system:, secure: or global:")
            if (!NAME.matches(name)) throw IllegalArgumentException("watch entry '$entry' has an invalid name")
            Key(kind, name)
        }.distinct()
        if (keys.size > MAX_KEYS) throw IllegalArgumentException("watch takes at most $MAX_KEYS entries")
        return keys
    }

    /// Current values by key id; [update] answers whether a value changed.
    class Values {
        private val values = LinkedHashMap<String, String?>()
        private val known = HashSet<String>()

        /// Stores [value]; returns the old value when it changed, wrapped so a change
        /// from null can be told apart from no change. The first read of a key is
        /// not a change: the controller reads it from `/state.watch`.
        @Synchronized
        fun update(id: String, value: String?): Change? {
            if (id !in known) {
                known.add(id)
                values[id] = value
                return null
            }
            val old = values[id]
            if (old == value) return null
            values[id] = value
            return Change(id, old, value)
        }

        @Synchronized
        fun snapshot(): Map<String, String?> = LinkedHashMap(values)

        @Synchronized
        fun clear() { values.clear(); known.clear() }
    }

    data class Change(val id: String, val old: String?, val new: String?)
}

/// Runs a watch list on the device. All callbacks run on [handler]'s thread.
class Watcher(
    private val context: Context,
    private val handler: Handler,
    private val onChange: (Watch.Change) -> Unit
) {
    private val values = Watch.Values()
    private val observers = mutableListOf<ContentObserver>()
    @Volatile var keys: List<Watch.Key> = emptyList()
        private set
    /// Last read error per key id (a TCL framework field refuses an app: SecurityException).
    private val errors = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val propPoll = object : Runnable {
        override fun run() {
            keys.filter { it.kind == Watch.Kind.PROP }.forEach { read(it) }
            handler.postDelayed(this, Watch.PROP_POLL_MS)
        }
    }

    /// Replace the watch list. Call on [handler]'s thread.
    fun start(newKeys: List<Watch.Key>) {
        stop()
        keys = newKeys
        val resolver = context.contentResolver
        for (key in newKeys) {
            read(key)
            val uri = settingsUri(key) ?: continue
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) { read(key) }
            }
            try {
                resolver.registerContentObserver(uri, false, observer)
                observers.add(observer)
            } catch (ex: Throwable) {
                errors[key.id] = ex.message ?: ex.javaClass.simpleName
            }
        }
        if (newKeys.any { it.kind == Watch.Kind.PROP }) handler.postDelayed(propPoll, Watch.PROP_POLL_MS)
    }

    fun stop() {
        handler.removeCallbacks(propPoll)
        val resolver = context.contentResolver
        observers.forEach { runCatching { resolver.unregisterContentObserver(it) } }
        observers.clear()
        values.clear()
        errors.clear()
        keys = emptyList()
    }

    /// For `/state.watch`: values by key id, plus `errors` when a key cannot be read.
    fun state(): Map<String, Any?> = mapOf(
        "keys" to keys.map { it.id },
        "values" to values.snapshot(),
        "errors" to HashMap(errors).takeIf { it.isNotEmpty() }
    )

    private fun read(key: Watch.Key) {
        val value = try {
            readValue(context.contentResolver, key).also { errors.remove(key.id) }
        } catch (ex: Throwable) {
            errors[key.id] = ex.message ?: ex.javaClass.simpleName
            null
        }
        values.update(key.id, value)?.let(onChange)
    }

    private fun settingsUri(key: Watch.Key): Uri? = when (key.kind) {
        Watch.Kind.PROP -> null
        Watch.Kind.SYSTEM -> Settings.System.getUriFor(key.name)
        Watch.Kind.SECURE -> Settings.Secure.getUriFor(key.name)
        Watch.Kind.GLOBAL -> Settings.Global.getUriFor(key.name)
    }

    private fun readValue(resolver: ContentResolver, key: Watch.Key): String? = when (key.kind) {
        Watch.Kind.PROP -> systemProperty(key.name)
        Watch.Kind.SYSTEM -> Settings.System.getString(resolver, key.name)
        Watch.Kind.SECURE -> Settings.Secure.getString(resolver, key.name)
        Watch.Kind.GLOBAL -> Settings.Global.getString(resolver, key.name)
    }

    companion object {
        private const val LOG_TAG = "PiPupWatch"

        private val getProp: java.lang.reflect.Method? = try {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        } catch (ex: Throwable) {
            Log.w(LOG_TAG, "SystemProperties not reachable: ${ex.message}")
            null
        }

        /// `android.os.SystemProperties.get` is hidden API, read by reflection; an empty
        /// answer means unset (or not readable by an app: SELinux gives no error then).
        fun systemProperty(name: String): String? {
            val method = getProp ?: throw IllegalStateException("SystemProperties not reachable")
            return (method.invoke(null, name) as? String)?.takeIf { it.isNotEmpty() }
        }
    }
}
