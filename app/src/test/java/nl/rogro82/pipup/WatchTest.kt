package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTest {

    @Test
    fun parsesKindsAndDropsDuplicates() {
        val keys = Watch.parse(" prop:sys.tcl.inputid, system:recent_play_channel_id,GLOBAL:x_y,prop:sys.tcl.inputid ")
        assertEquals(
            listOf("prop:sys.tcl.inputid", "system:recent_play_channel_id", "global:x_y"),
            keys.map { it.id }
        )
        assertEquals(Watch.Kind.PROP, keys[0].kind)
    }

    @Test
    fun emptyWatchesNothing() {
        assertTrue(Watch.parse(null).isEmpty())
        assertTrue(Watch.parse("").isEmpty())
        assertTrue(Watch.parse(" , ").isEmpty())
    }

    @Test
    fun refusesBadEntries() {
        for (bad in listOf("sys.tcl.inputid", "vendor:x", "prop:", "prop:a b", "system:a;rm")) {
            try {
                Watch.parse(bad)
                throw AssertionError("accepted $bad")
            } catch (_: IllegalArgumentException) {}
        }
        val tooMany = (0..Watch.MAX_KEYS).joinToString(",") { "prop:p$it" }
        try { Watch.parse(tooMany); throw AssertionError("accepted ${Watch.MAX_KEYS + 1} keys") }
        catch (_: IllegalArgumentException) {}
    }

    @Test
    fun onePushPerChangeAndNoneForTheFirstRead() {
        val values = Watch.Values()
        val reads = listOf("HDMI1", "HDMI1", "TV", "TV", null, null, "TV")
        val changes = reads.mapNotNull { values.update("prop:sys.tcl.inputid", it) }
        assertEquals(
            listOf(
                Watch.Change("prop:sys.tcl.inputid", "HDMI1", "TV"),
                Watch.Change("prop:sys.tcl.inputid", "TV", null),
                Watch.Change("prop:sys.tcl.inputid", null, "TV")
            ),
            changes
        )
        assertEquals("TV", values.snapshot()["prop:sys.tcl.inputid"])
    }

    @Test
    fun firstReadOfNullIsKnown() {
        val values = Watch.Values()
        assertNull(values.update("system:k", null))
        assertEquals(Watch.Change("system:k", null, "1"), values.update("system:k", "1"))
        values.clear()
        assertNull(values.update("system:k", "2"))
    }
}
