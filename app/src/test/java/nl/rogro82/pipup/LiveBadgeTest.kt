package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import android.view.Gravity
import org.junit.Test

class LiveBadgeTest {
    @Test
    fun liveBadgeIsOffByDefault() {
        assertFalse(PopupProps().liveBadge)
        assertNull(PopupProps().liveBadgeColor)
    }

    @Test
    fun defaultBadgeColorIsAmber() {
        assertEquals("#FFA500", PopupView.DEFAULT_LIVE_BADGE_COLOR)
    }

    @Test
    fun badgeDefaultsToBottomRight() {
        assertEquals(Gravity.BOTTOM or Gravity.END, liveBadgeGravity(null))
        assertEquals(Gravity.BOTTOM or Gravity.END, liveBadgeGravity("nonsense"))
    }

    @Test
    fun badgeHonorsEachCorner() {
        assertEquals(Gravity.TOP or Gravity.START, liveBadgeGravity("top_left"))
        assertEquals(Gravity.TOP or Gravity.END, liveBadgeGravity("top_right"))
        assertEquals(Gravity.BOTTOM or Gravity.START, liveBadgeGravity("bottom_left"))
    }
}
