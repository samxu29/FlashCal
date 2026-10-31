package io.github.samxu29.flashcal

import io.github.samxu29.flashcal.domain.ApertureStep
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.PowerFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashMathTest {
    @Test
    fun matchesGodoxPrintedGuideNumbers() {
        val table = listOf(
            Triple(1, 50, 10.6),
            Triple(1, 100, 15.0),
            Triple(1, 200, 21.2),
            Triple(1, 400, 30.0),
            Triple(1, 800, 42.4),
            Triple(1, 1600, 60.0),
            Triple(2, 100, 10.6),
            Triple(4, 100, 7.5),
            Triple(8, 100, 5.3),
            Triple(16, 100, 3.8),
            Triple(32, 100, 2.7),
            Triple(64, 100, 1.9),
        )
        table.forEach { (denominator, iso, printed) ->
            val actual = FlashMath.effectiveGuideNumber(15.0, iso, 1.0 / denominator)
            assertEquals("1/$denominator at ISO $iso", printed, actual, 0.08)
        }
    }

    @Test
    fun fullPowerDistancesMatchPrintedChartWithinRounding() {
        val expected = listOf(
            2.0 to 7.5,
            2.8 to 5.4,
            4.0 to 3.8,
            5.6 to 2.7,
            8.0 to 1.9,
            11.0 to 1.3,
            16.0 to 0.9,
        )
        expected.forEach { (aperture, printedMeters) ->
            assertEquals(printedMeters, 15.0 / aperture, 0.1)
        }
    }

    @Test
    fun threeMetersAtFullPowerRecommendsOpeningFromF56() {
        val result = FlashMath.apertureFor(15.0, 100, 1.0, 3.0)!!
        assertEquals(5.0, result.exactAperture, 0.001)
        assertEquals("5.6", result.nearest.label)
        assertEquals("4", result.toward?.label)
        assertTrue(result.stopError > 0.3)
        assertTrue(result.withinLensScale)
    }

    @Test
    fun fixedDistanceStaysInsideTheSavedStops() {
        val stops = FlashMath.FULL_STOPS.filter { it.label in setOf("4", "5.6", "8") }
        val tooFar = FlashMath.apertureFor(15.0, 100, 1.0, 1.0, stops)!!
        val tooClose = FlashMath.apertureFor(15.0, 100, 1.0, 10.0, stops)!!
        assertEquals("8", tooFar.nearest.label)
        assertEquals("4", tooClose.nearest.label)
        assertTrue(tooFar.nearest.label in stops.map { it.label })
        assertTrue(tooClose.nearest.label in stops.map { it.label })
    }

    @Test
    fun chartDistanceAtF4IsTheFullPowerGuideNumber() {
        val result = FlashMath.apertureFor(15.0, 100, 1.0, 3.75)!!
        assertEquals(4.0, result.exactAperture, 0.001)
        assertEquals("4", result.nearest.label)
        assertEquals(0.0, result.stopError, 0.02)
    }

    @Test
    fun quarterPowerReachesF4AtAbout1Point9Meters() {
        val suggestion = FlashMath.powerForAperture(15.0, 100, 1.875, 4.0)!!
        assertEquals(4, suggestion.step.denominator)
        assertEquals(PowerFit.MATCH, suggestion.fit)
    }

    @Test
    fun conventionalStopsMatchPrintedHalfAndThirdMarks() {
        val rows = listOf(
            Triple(1.4, 2.0, listOf("1.4", "1.7", "2") to listOf("1.4", "1.6", "1.8", "2")),
            Triple(2.0, 2.8, listOf("2", "2.4", "2.8") to listOf("2", "2.2", "2.5", "2.8")),
            Triple(2.8, 4.0, listOf("2.8", "3.3", "4") to listOf("2.8", "3.2", "3.5", "4")),
            Triple(4.0, 5.6, listOf("4", "4.8", "5.6") to listOf("4", "4.5", "5", "5.6")),
            Triple(5.6, 8.0, listOf("5.6", "6.7", "8") to listOf("5.6", "6.3", "7.1", "8")),
            Triple(8.0, 11.0, listOf("8", "9.5", "11") to listOf("8", "9", "10", "11")),
        )
        rows.forEach { (low, high, expected) ->
            val (half, third) = expected
            assertEquals(half, FlashMath.aperturesInRange(ApertureStep.HALF, low, high).map { it.label })
            assertEquals(third, FlashMath.aperturesInRange(ApertureStep.THIRD, low, high).map { it.label })
        }
    }

    @Test
    fun fullStopRangeStaysOnTheClassicSequence() {
        val labels = FlashMath.aperturesInRange(ApertureStep.FULL, 1.4, 16.0).map { it.label }
        assertEquals(listOf("1.4", "2", "2.8", "4", "5.6", "8", "11", "16"), labels)
    }

    @Test
    fun customApertureSortsIntoTheDialWithoutDuplicatingAStop() {
        val withFastLens = FlashMath.aperturesInRange(ApertureStep.FULL, 1.4, 16.0, custom = 1.2)
        assertEquals("1.2", withFastLens.first().label)
        val unchanged = FlashMath.aperturesInRange(ApertureStep.FULL, 1.4, 16.0, custom = 4.0)
        assertEquals(
            FlashMath.aperturesInRange(ApertureStep.FULL, 1.4, 16.0).map { it.label },
            unchanged.map { it.label },
        )
    }

    @Test
    fun switchingIncrementKeepsEndpointsThatExistOnEveryScale() {
        assertEquals("1.4" to "16", FlashMath.snappedLabels(ApertureStep.HALF, 1.4, 16.0))
        assertEquals("1.4" to "16", FlashMath.snappedLabels(ApertureStep.THIRD, 1.4, 16.0))
        assertEquals("2" to "16", FlashMath.snappedLabels(ApertureStep.FULL, 1.7, 16.0))
    }

    @Test
    fun unusualFNumberStillDrivesFlashPower() {
        val suggestion = FlashMath.powerForAperture(15.0, 100, 2.0, 1.7)!!
        assertEquals(PowerFit.MATCH, suggestion.fit)
        assertTrue(suggestion.exactAperture > 0.0)
        assertEquals("0.95", FlashMath.resolveAperture("0.95")?.label)
        assertEquals(0.7, FlashMath.parseAperture("0.7"))
        assertEquals(128.0, FlashMath.parseAperture("128"))
        assertNull(FlashMath.parseAperture("0"))
        assertNull(FlashMath.parseAperture("0.6"))
        assertNull(FlashMath.parseAperture("200"))
    }

    @Test
    fun selectableAperturesRunFromF07ThroughF128() {
        assertEquals("0.7", FlashMath.FULL_STOPS.first().label)
        assertEquals("128", FlashMath.FULL_STOPS.last().label)
        assertEquals("0.7", FlashMath.HALF_STOPS.first().label)
        assertEquals("128", FlashMath.HALF_STOPS.last().label)
        assertEquals("0.7", FlashMath.THIRD_STOPS.first().label)
        assertEquals("128", FlashMath.THIRD_STOPS.last().label)
        assertEquals(
            listOf("0.7", "0.8", "1"),
            FlashMath.aperturesInRange(ApertureStep.HALF, 0.7, 1.0).map { it.label },
        )
        assertEquals(
            listOf("0.7", "0.8", "0.9", "1"),
            FlashMath.aperturesInRange(ApertureStep.THIRD, 0.7, 1.0).map { it.label },
        )
        assertEquals(
            listOf("32", "36", "40", "45"),
            FlashMath.aperturesInRange(ApertureStep.THIRD, 32.0, 45.0).map { it.label },
        )
        assertEquals(
            listOf("0.95", "1", "1.4", "2"),
            FlashMath.aperturesInRange(ApertureStep.FULL, 0.95, 2.0).map { it.label },
        )
    }

    @Test
    fun farSubjectAtF8NeedsMoreThanFullPower() {
        val suggestion = FlashMath.powerForAperture(15.0, 100, 10.0, 8.0)!!
        assertEquals(PowerFit.NEEDS_MORE, suggestion.fit)
        assertEquals(1, suggestion.step.denominator)
        assertEquals(1.5, suggestion.exactAperture, 0.001)
        assertTrue(suggestion.stopError > 0.0)
    }

    @Test
    fun closeSubjectAtF2IsStillTooBrightAtMinimumPower() {
        val suggestion = FlashMath.powerForAperture(15.0, 400, 0.3, 2.0)!!
        assertEquals(PowerFit.STILL_TOO_MUCH, suggestion.fit)
        assertEquals(64, suggestion.step.denominator)
        assertTrue(suggestion.stopError < 0.0)
    }
}
