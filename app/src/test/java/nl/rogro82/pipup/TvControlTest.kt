package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Test

class TvControlTest {

    @Test
    fun stepToPercentRoundsAndClamps() {
        assertEquals(0, stepToPercent(0, 15))
        assertEquals(47, stepToPercent(7, 15))
        assertEquals(100, stepToPercent(15, 15))
        assertEquals(100, stepToPercent(20, 15))
        assertEquals(0, stepToPercent(5, 0))
    }

    @Test
    fun percentToStepKeepsLowLevelsAudible() {
        assertEquals(0, percentToStep(0.0, 15))
        assertEquals(1, percentToStep(1.0, 15))   // not rounded down to silence
        assertEquals(8, percentToStep(50.0, 15))
        assertEquals(15, percentToStep(100.0, 15))
        assertEquals(15, percentToStep(140.0, 15))
        assertEquals(0, percentToStep(50.0, 0))
    }

    @Test
    fun roundTripStaysOnTheSameStep() {
        for (max in listOf(15, 25, 100)) for (step in 0..max) {
            assertEquals(step, percentToStep(stepToPercent(step, max).toDouble(), max))
        }
    }
}
