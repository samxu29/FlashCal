package io.github.samxu29.flashcal

import io.github.samxu29.flashcal.meter.LensField
import io.github.samxu29.flashcal.meter.visibleField
import io.github.samxu29.flashcal.meter.widestFittingFocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameFitTest {
    @Test
    fun verticalFrameNeedsBothSidesToFit() {
        // 24 mm across needs 12/0.5 = 24 mm; 36 mm down needs 18/0.9 = 20 mm.
        assertEquals(24.0, widestFittingFocal(0.5, 0.9)!!, 1e-9)
        // A short preview makes the 36 mm side the limit: 18/0.6 = 30 mm.
        assertEquals(30.0, widestFittingFocal(0.5, 0.6)!!, 1e-9)
    }

    @Test
    fun noFieldNoAnswer() {
        assertNull(widestFittingFocal(0.0, 0.5))
    }

    @Test
    fun tallScreenCropsTheSidesOfAPortraitPreview() {
        // Landscape 4:3 sensor turned upright: 0.6 across, 0.8 down.
        val visible = visibleField(LensField(0.8, 0.6), 90, 1000, 2000)!!
        // The screen is narrower (1:2) than the content (3:4), so height is kept.
        assertEquals(0.8, visible.tanHalfHeight, 1e-9)
        assertEquals(0.4, visible.tanHalfWidth, 1e-9)
    }
}
