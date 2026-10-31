package io.github.samxu29.flashcal.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRegionMappingTest {
    @Test
    fun respectsSensorCropOriginAndDimensions() {
        val regions = FocusRegionMapping.map(
            intArrayOf(1100, 950, 3100, 2450, 1000),
            intArrayOf(100, 200, 4000, 3000),
            640, 480,
        ) { it }
        assertRegion(FocusRegion(0.25f, 0.25f, 0.75f, 0.75f), regions.single())
    }

    @Test
    fun accountsForExtraImageAspectRatioCrop() {
        val regions = FocusRegionMapping.map(
            intArrayOf(1000, 750, 3000, 2250, 1000),
            intArrayOf(0, 0, 4000, 3000),
            1920, 1080,
        ) { it }
        assertRegion(FocusRegion(0.25f, 1f / 6f, 0.75f, 5f / 6f), regions.single())
    }

    @Test
    fun usesAllCornersWhenDisplayIsRotated() {
        val regions = FocusRegionMapping.map(
            intArrayOf(100, 200, 300, 400, 1000),
            intArrayOf(0, 0, 1000, 1000),
            100, 100,
        ) { points ->
            FloatArray(points.size).also { rotated ->
                for (i in points.indices step 2) {
                    rotated[i] = 1f - points[i + 1]
                    rotated[i + 1] = points[i]
                }
            }
        }
        assertRegion(FocusRegion(0.6f, 0.1f, 0.8f, 0.3f), regions.single())
    }

    @Test
    fun clipsToVisiblePreviewAndDropsOffscreenRegions() {
        val regions = FocusRegionMapping.map(
            intArrayOf(-100, 200, 300, 400, 1000, 1100, 200, 1300, 400, 1000),
            intArrayOf(0, 0, 1000, 1000),
            100, 100,
        ) { it }
        assertRegion(FocusRegion(0f, 0.2f, 0.3f, 0.4f), regions.single())
    }

    @Test
    fun zeroWeightAndEmptyAreasDoNotBecomeFakeFocusAreas() {
        val regions = FocusRegionMapping.map(
            intArrayOf(0, 0, 1000, 1000, 0, 100, 200, 100, 400, 1000),
            intArrayOf(0, 0, 1000, 1000),
            100, 100,
        ) { error("Ignored regions must not be transformed") }
        assertTrue(regions.isEmpty())
    }

    @Test
    fun malformedMetadataAndInvalidTransformsDoNotProduceOutlines() {
        assertTrue(FocusRegionMapping.map(
            intArrayOf(0, 0, 100, 100), intArrayOf(0, 0, 100, 100), 100, 100,
        ) { it }.isEmpty())
        assertTrue(FocusRegionMapping.map(
            intArrayOf(0, 0, 100, 100, 1000), intArrayOf(0, 0, 0, 100), 100, 100,
        ) { it }.isEmpty())
        assertTrue(FocusRegionMapping.map(
            intArrayOf(0, 0, 100, 100, 1000), intArrayOf(0, 0, 100, 100), 100, 100,
        ) { FloatArray(8) { Float.NaN } }.isEmpty())
    }

    @Test
    fun retainsEveryReportedWeightedRegion() {
        val regions = FocusRegionMapping.map(
            intArrayOf(100, 200, 300, 400, 1000, 600, 700, 900, 800, 500),
            intArrayOf(0, 0, 1000, 1000),
            100, 100,
        ) { it }
        assertEquals(2, regions.size)
        assertRegion(FocusRegion(0.1f, 0.2f, 0.3f, 0.4f), regions[0])
        assertRegion(FocusRegion(0.6f, 0.7f, 0.9f, 0.8f), regions[1])
    }

    private fun assertRegion(expected: FocusRegion, actual: FocusRegion) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }
}
