package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoPosterHeightTest {
    @Test
    fun withoutRequestedHeightTheBoxStartsSixteenNine() {
        assertEquals(360, provisionalVideoHeight(640, null))
    }

    @Test
    fun requestedHeightSetsTheProvisionalBox() {
        assertEquals(480, provisionalVideoHeight(640, 480))
    }

    @Test
    fun nonPositiveRequestedHeightIsIgnored() {
        assertEquals(360, provisionalVideoHeight(640, 0))
    }

    @Test
    fun withoutRequestedHeightThePosterSetsTheShape() {
        assertEquals(436, posterVideoHeight(640, null, 704, 480))
    }

    @Test
    fun requestedHeightKeepsTheBoxForAnAnamorphicPoster() {
        assertNull(posterVideoHeight(640, 480, 704, 480))
    }
}
