package nl.rogro82.pipup

import org.junit.Assert.assertFalse
import org.junit.Test

class MediaFullBleedTest {
    @Test
    fun fullBleedIsOffByDefault() {
        assertFalse(PopupProps().mediaFullBleed)
    }

    @Test
    fun inlineButtonsAreOffByDefault() {
        assertFalse(PopupProps().buttonsInline)
    }
}
