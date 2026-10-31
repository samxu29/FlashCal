package io.github.samxu29.flashcal.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DistanceFilterTest {
    private fun depth(m: Float) = DistanceReading(m, DistanceSource.DEPTH)
    private fun plane(m: Float) = DistanceReading(m, DistanceSource.PLANE)

    @Test
    fun singleSpikeIsIgnored() {
        val filter = DistanceFilter()
        filter.update(depth(2f), 0)
        filter.update(depth(2f), 33)
        val out = filter.update(depth(9f), 66)
        assertEquals(2f, out!!.meters, 0f)
    }

    @Test
    fun newSceneReplacesOldWithoutBlending() {
        val filter = DistanceFilter()
        for (t in 0L..300L step 33) filter.update(depth(4f), t)
        var out: DistanceReading? = null
        for (t in 333L..700L step 33) out = filter.update(depth(1.2f), t)
        assertEquals(1.2f, out!!.meters, 0f)
    }

    @Test
    fun depthStaysPreferredBrieflyOverPlane() {
        val filter = DistanceFilter()
        filter.update(depth(2f), 0)
        assertEquals(DistanceSource.DEPTH, filter.update(plane(3f), 100)!!.source)
        val later = filter.update(plane(3f), 500)!!
        assertEquals(DistanceSource.PLANE, later.source)
        assertEquals(3f, later.meters, 0f)
    }

    @Test
    fun readingIsDroppedAfterHold() {
        val filter = DistanceFilter()
        filter.update(depth(2f), 0)
        assertEquals(2f, filter.update(null, 500)!!.meters, 0f)
        assertNull(filter.update(null, 700))
    }
}
