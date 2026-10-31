package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppTest {

    private val activities = setOf(
        "com.tcl.tv" to "com.tcl.tv.TvActivity",
        "com.netflix.ninja" to "com.netflix.ninja.MainActivity",
        "com.android.systemui" to "com.android.systemui.recents.RecentsActivity",
        "nl.rogro82.pipup" to "nl.rogro82.pipup.MainActivity"
    )

    private fun tracker() = ForegroundApp.Tracker("nl.rogro82.pipup") { pkg, cls -> (pkg to cls) in activities }

    @Test
    fun onePushPerAppChange() {
        val t = tracker()
        assertTrue(t.onWindow("com.tcl.tv", "com.tcl.tv.TvActivity"))
        assertFalse(t.onWindow("com.tcl.tv", "com.tcl.tv.TvActivity"))
        assertTrue(t.onWindow("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
        assertEquals("com.netflix.ninja", t.current)
        assertEquals("com.tcl.tv", t.previous)
    }

    @Test
    fun ignoresOwnPopupsSystemUiDialogsAndKeyboards() {
        val t = tracker()
        t.onWindow("com.tcl.tv", "com.tcl.tv.TvActivity")
        assertFalse(t.onWindow("nl.rogro82.pipup", "nl.rogro82.pipup.MainActivity"))
        assertFalse(t.onWindow("com.android.systemui", "com.android.systemui.recents.RecentsActivity"))
        assertFalse(t.onWindow("com.google.android.inputmethod", "android.inputmethodservice.SoftInputWindow"))
        assertFalse(t.onWindow(null, "x"))
        assertFalse(t.onWindow("com.tcl.tv", null))
        assertEquals("com.tcl.tv", t.current)
        assertNull(t.previous)
    }

    @Test
    fun resetForgetsBoth() {
        val t = tracker()
        t.onWindow("com.tcl.tv", "com.tcl.tv.TvActivity")
        t.reset()
        assertNull(t.current)
        assertTrue(t.onWindow("com.tcl.tv", "com.tcl.tv.TvActivity"))
    }
}
