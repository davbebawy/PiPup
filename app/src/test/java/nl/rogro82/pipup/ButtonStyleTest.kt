package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonStyleTest {
    private fun popup(cornerRadius: Float? = null) =
        PopupProps(buttonCornerRadius = cornerRadius)

    @Test
    fun unsetFieldsKeepTheClassicStyle() {
        assertEquals(ButtonStyle(), ButtonStyle.from(popup()))
    }

    @Test
    fun unsetCornerRadiusIsTheClassicTwelve() {
        assertEquals(12f, ButtonStyle.from(popup()).cornerRadius)
    }

    @Test
    fun zeroCornerRadiusGivesSquareCorners() {
        assertEquals(0f, ButtonStyle.from(popup(cornerRadius = 0f)).cornerRadius)
    }

    @Test
    fun negativeCornerRadiusIsIgnored() {
        assertEquals(12f, ButtonStyle.from(popup(cornerRadius = -4f)).cornerRadius)
    }

    @Test
    fun customCornerRadiusIsUsed() {
        assertEquals(24f, ButtonStyle.from(popup(cornerRadius = 24f)).cornerRadius)
    }
}
