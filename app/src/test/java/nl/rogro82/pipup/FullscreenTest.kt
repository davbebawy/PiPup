package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Full-screen popups (0.29.1, #73): which popups cover the display, and that they never stack.
class FullscreenTest {

    private val wall = PopupProps.Media.Grid(
        items = listOf(PopupProps.Media.GridItem("http://h/api/webrtc?src=a")), fullscreen = true
    )

    @Test
    fun fullscreenFlagOrFullscreenGridCoversTheScreen() {
        assertTrue(PopupProps(fullscreen = true).coversScreen)
        assertTrue(PopupProps(media = wall).coversScreen)
        assertFalse(PopupProps().coversScreen)
        assertFalse(PopupProps(media = wall.copy(fullscreen = false)).coversScreen)
    }

    @Test
    fun aFullscreenPopupNeverStacks() {
        assertNull(PopupProps(fullscreen = true, stack = "vertical").stackAxis)
        assertEquals(PopupProps.StackAxis.Vertical, PopupProps(stack = "vertical").stackAxis)
    }
}
