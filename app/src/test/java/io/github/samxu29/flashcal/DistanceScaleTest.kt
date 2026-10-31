package io.github.samxu29.flashcal

import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.domain.DistanceScale
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceScaleTest {
    @Test
    fun feetDialHandsMetersToTheMath() {
        val scale = DistanceScale(300, 3_300, 100, DistanceUnit.FEET)
        assertEquals(31, scale.size)
        assertEquals(10f, scale.valueAt(7), 0.001f)
        assertEquals(3.048f, scale.metersAt(7), 0.001f)
        assertEquals(7, scale.indexFor(3.048f))
    }

    @Test
    fun defaultMeterDialConvertsToRoundFeet() {
        val feet = DistanceScale.DEFAULT.inUnit(DistanceUnit.FEET)
        assertEquals(DistanceUnit.FEET, feet.unit)
        assertEquals(300, feet.minimum)
        assertEquals(3_300, feet.maximum)
        assertEquals(200, feet.increment)
        val back = feet.inUnit(DistanceUnit.METERS)
        assertEquals(90, back.minimum)
        assertEquals(1_010, back.maximum)
        assertEquals(50, back.increment)
    }

    @Test
    fun typedFeetAllowLongerRangeThanMeters() {
        assertEquals(30_000, DistanceScale.hundredths("300", DistanceUnit.FEET))
        assertNull(DistanceScale.hundredths("300", DistanceUnit.METERS))
        assertEquals(50, DistanceScale.hundredths("0.5", DistanceUnit.FEET))
    }

    @Test
    fun switchingToFeetKeepsTheSameFlashPower() {
        val settings = GearSettingsState(
            iso = 100,
            powerDenominator = 1,
            guideNumberText = "15",
            targetLabel = "4",
            apertureOptions = setOf("4"),
        )
        settings.setDistanceUnit(DistanceUnit.FEET)
        assertEquals("49.2", settings.guideNumberText)
        assertEquals(15.0, settings.guideNumber, 0.02)
        val power = FlashMath.powerForAperture(settings.guideNumber, 100, 1.875, 4.0)!!
        assertEquals(4, power.step.denominator)
        settings.setDistanceUnit(DistanceUnit.METERS)
        assertEquals("15", settings.guideNumberText)
    }
}
