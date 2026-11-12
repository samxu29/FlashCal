package io.github.samxu29.flashcal

import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.domain.ApertureStep
import io.github.samxu29.flashcal.domain.EvWindow
import io.github.samxu29.flashcal.domain.ExposureLimit
import io.github.samxu29.flashcal.domain.LightMath
import io.github.samxu29.flashcal.meter.choosePreviewSize
import io.github.samxu29.flashcal.meter.previewFit
import io.github.samxu29.flashcal.meter.previewViewRotation
import io.github.samxu29.flashcal.meter.relativeRotation
import io.github.samxu29.flashcal.meter.uprightContentSize
import io.github.samxu29.flashcal.data.LensKind
import io.github.samxu29.flashcal.meter.BackCameras
import io.github.samxu29.flashcal.meter.CameraOption
import io.github.samxu29.flashcal.meter.LensField
import io.github.samxu29.flashcal.meter.bufferField
import io.github.samxu29.flashcal.meter.frameRegion
import io.github.samxu29.flashcal.meter.lensCrop
import io.github.samxu29.flashcal.meter.uprightField
import io.github.samxu29.flashcal.meter.widestFocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private val FlashMathThirds = io.github.samxu29.flashcal.domain.FlashMath
    .aperturesInRange(ApertureStep.THIRD, 1.4, 16.0)
    .map { it.value }

class LightMathTest {
    @Test
    fun sunnySixteenIsAboutEv15() {
        val ev = LightMath.ev100(aperture = 16.0, shutterSeconds = 1.0 / 125, iso = 100)!!
        assertEquals(14.97, ev, 0.02)
        val back = LightMath.shutterSeconds(ev, 16.0, 100)!!
        assertEquals(1.0 / 125, back, 1e-9)
        assertEquals("1/125", LightMath.nearestShutter(back)!!.label)
    }

    @Test
    fun twoStopsOfIsoLowersEvByTwo() {
        val at100 = LightMath.ev100(8.0, 1.0 / 125, 100)!!
        val at400 = LightMath.ev100(8.0, 1.0 / 125, LightMath.effectiveIso(200, 200))!!
        assertEquals(2.0, at100 - at400, 0.001)
    }

    @Test
    fun f8AtEv12IsAboutOne250thAtIso400() {
        val seconds = LightMath.shutterSeconds(12.0, 8.0, 400)!!
        assertEquals(1.0 / 256, seconds, 1e-6)
        assertEquals("1/250", LightMath.nearestShutter(seconds)!!.label)
        assertEquals(ExposureLimit.INSIDE, LightMath.exposureLimit(seconds))
    }

    @Test
    fun nearestShutterUsesStopsNotLinearTime() {
        assertEquals("1/250", LightMath.nearestShutter(1.0 / 180)!!.label)
        assertEquals("30 s", LightMath.nearestShutter(30.0)!!.label)
    }

    @Test
    fun exposureOutsideTheSeriesIsFlagged() {
        assertEquals(ExposureLimit.TOO_BRIGHT, LightMath.exposureLimit(1.0 / 16000))
        assertEquals(ExposureLimit.TOO_DARK, LightMath.exposureLimit(60.0))
        assertEquals(ExposureLimit.INSIDE, LightMath.exposureLimit(1.0 / 8000))
        assertEquals(ExposureLimit.INSIDE, LightMath.exposureLimit(30.0))
    }

    @Test
    fun invalidExposureIsRejected() {
        assertNull(LightMath.ev100(0.0, 0.01, 100))
        assertNull(LightMath.ev100(4.0, 0.0, 100))
        assertNull(LightMath.ev100(4.0, 0.01, 0))
        assertNull(LightMath.shutterSeconds(12.0, 4.0, 0))
        assertNull(LightMath.nearestShutter(0.0))
        assertEquals(0, LightMath.effectiveIso(0, 100))
        assertEquals(200, LightMath.effectiveIso(200, 0))
    }

    @Test
    fun formatEvUsesOneDecimal() {
        assertEquals("15.0", LightMath.formatEv(14.97))
        assertEquals("12.1", LightMath.formatEv(12.14))
    }

    @Test
    fun sunnySixteenSettingsNeedNoOffset() {
        val scene = LightMath.ev100(16.0, 1.0 / 125, 100)!!
        val offset = LightMath.exposureOffset(scene, 16.0, 1.0 / 125, 100)!!
        assertEquals(0.0, offset, 0.001)
        assertEquals("0", LightMath.formatStops(offset))
    }

    @Test
    fun oneStopDarkSettingsAskForPlusOne() {
        val scene = LightMath.ev100(8.0, 1.0 / 125, 100)!!
        val offset = LightMath.exposureOffset(scene, 8.0, 1.0 / 250, 100)!!
        assertEquals(1.0, offset, 0.05)
        assertEquals("+1.0", LightMath.formatStops(1.0))
        assertEquals("-0.7", LightMath.formatStops(-0.74))
    }

    @Test
    fun evWindowDropsASingleSpike() {
        val window = EvWindow()
        window.add(10.0, 0)
        window.add(10.0, 100)
        assertEquals(10.0, window.add(14.0, 200), 0.0)
        assertEquals(12.0, window.add(12.0, 300), 0.0)
    }

    @Test
    fun evWindowForgetsSamplesOlderThanTheWindow() {
        val window = EvWindow()
        window.add(4.0, 0)
        window.add(4.0, 100)
        val median = window.add(9.0, 600)
        assertEquals(9.0, median, 0.0)
        assertEquals(0.0, abs(median - 9.0), 0.0)
    }

    @Test
    fun portraitBackCameraRotatesNinetyDegrees() {
        assertEquals(90, relativeRotation(sensorOrientation = 90, displayRotationDegrees = 0, front = false))
        assertEquals(0, relativeRotation(sensorOrientation = 90, displayRotationDegrees = 90, front = false))
        assertEquals(90, relativeRotation(sensorOrientation = 270, displayRotationDegrees = 0, front = true))
    }

    @Test
    fun shutterRangeKeepsMarksBetweenTheEndpoints() {
        val full = LightMath.shuttersInRange(ApertureStep.FULL, slowest = 1.0, fastest = 1.0 / 1000)
        assertEquals("1 s", full.first().label)
        assertEquals("1/1000", full.last().label)
        assertTrue(full.any { it.label == "1/125" })
        assertFalse(full.any { it.label == "30 s" })
        val half = LightMath.shuttersInRange(ApertureStep.HALF, slowest = 1.0, fastest = 1.0 / 1000)
        assertTrue(half.any { it.label == "1/90" })
        assertTrue(half.any { it.label == "1/180" })
    }

    @Test
    fun profileRemembersTheShutterRange() {
        val settings = GearSettingsState(
            iso = 100,
            powerDenominator = 1,
            guideNumberText = "15",
            targetLabel = "4",
            apertureOptions = setOf("4"),
        )
        assertEquals("30 s", settings.enabledShutters.first().label)
        assertEquals("1/8000", settings.enabledShutters.last().label)
        assertTrue(settings.setSlowestShutter(1.0))
        assertTrue(settings.setFastestShutter(1.0 / 1000))
        assertFalse(settings.setFastestShutter(30.0))
        assertEquals("1 s", settings.enabledShutters.first().label)
        assertEquals("1/1000", settings.enabledShutters.last().label)
        val saved = settings.snapshot()
        assertEquals(1.0, saved.slowestShutter, 1e-9)
        assertEquals(1.0 / 1000, saved.fastestShutter, 1e-6)
        assertTrue(settings.setShutterStep(ApertureStep.HALF))
        assertTrue(settings.enabledShutters.any { it.label == "1/90" })
    }

    @Test
    fun sceneAtF8SuggestsTheMatchingShutter() {
        val scene = LightMath.ev100(16.0, 1.0 / 125, 100)!!
        val shutter = LightMath.shutterForScene(scene, 8.0, 100, LightMath.SPEEDS)!!
        assertEquals("1/500", shutter.label)
        assertEquals(ExposureLimit.INSIDE, LightMath.shutterLimit(shutter.seconds, 1.0, 1.0 / 1000))
        assertEquals(ExposureLimit.TOO_BRIGHT, LightMath.shutterLimit(1.0 / 4000, 1.0, 1.0 / 1000))
        assertEquals(ExposureLimit.TOO_DARK, LightMath.shutterLimit(4.0, 1.0, 1.0 / 1000))
    }

    @Test
    fun portraitPreviewKeepsTheUprightTextureUnrotated() {
        assertEquals(0, previewViewRotation(0))
        assertEquals(270, previewViewRotation(90))
        assertEquals(1080 to 1920, uprightContentSize(1920, 1080, sensorOrientation = 90))
        assertEquals(1080 to 1920, uprightContentSize(1920, 1080, sensorOrientation = 270))
        val fit = previewFit(1080, 2400, 1080, 1920, previewViewRotation(0))!!
        assertEquals(0, fit.rotationDegrees)
        assertEquals(1350, fit.viewWidth)
        assertEquals(2400, fit.viewHeight)
    }

    @Test
    fun autoMeterKeepsTheApertureWhenAShutterFits() {
        val apertures = listOf(2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(16.0, 1.0 / 125, 100)!!
        val (a, s) = LightMath.autoExposure(scene, 100, apertures, shutters, 8.0, 1.0 / 60)!!
        assertEquals(8.0, apertures[a], 0.0)
        assertEquals(1.0 / 500, shutters[s], 1e-9)
    }

    @Test
    fun autoMeterKeepsAThirdStopApertureWithFullStopShutters() {
        val apertures = FlashMathThirds
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(1.4, 1.0 / 1000, 400)!! + 0.4
        val (a, _) = LightMath.autoExposure(scene, 400, apertures, shutters, 5.6, 1.0 / 30)!!
        assertEquals(5.6, apertures[a], 0.0)
    }

    @Test
    fun autoMeterRoundsTowardOverByDefault() {
        val apertures = listOf(5.6)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        // Closer to 1/125 (about 0.4 under) than to 1/60 (0.7 over), yet 1/60 wins.
        val scene = LightMath.ev100(5.6, 1.0 / 60, 400)!! + 0.7
        val (_, s) = LightMath.autoExposure(scene, 400, apertures, shutters, 5.6, 1.0 / 250)!!
        assertEquals(1.0 / 60, shutters[s], 1e-9)
        assertTrue(LightMath.overexposure(scene, 5.6, shutters[s], 400)!! > 0.0)
    }

    @Test
    fun autoMeterRoundsTowardUnderWhenAsked() {
        val apertures = listOf(5.6)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(5.6, 1.0 / 60, 400)!! + 0.3
        val (_, s) = LightMath.autoExposure(scene, 400, apertures, shutters, 5.6, 1.0 / 30, preferOver = false)!!
        assertEquals(1.0 / 125, shutters[s], 1e-9)
    }

    @Test
    fun shutterPriorityKeepsTheShutterAndMovesTheAperture() {
        val apertures = listOf(2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(16.0, 1.0 / 125, 100)!!
        val (a, s) = LightMath.autoExposure(scene, 100, apertures, shutters, 8.0, 1.0 / 125, shutterPriority = true)!!
        assertEquals(1.0 / 125, shutters[s], 1e-9)
        assertEquals(16.0, apertures[a], 0.0)
    }

    @Test
    fun shutterPriorityMovesTheShutterWhenNoApertureIsEnough() {
        val apertures = listOf(2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(16.0, 1.0 / 1000, 100)!!
        val (a, s) = LightMath.autoExposure(scene, 100, apertures, shutters, 8.0, 1.0 / 60, shutterPriority = true)!!
        assertEquals(16.0, apertures[a], 0.0)
        assertEquals(1.0 / 1000, shutters[s], 1e-9)
    }

    @Test
    fun autoMeterFallsBackWhenNothingLandsOver() {
        val apertures = listOf(16.0)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0 / 60, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(16.0, 1.0 / 1000, 100)!! + 2.0
        val (_, s) = LightMath.autoExposure(scene, 100, apertures, shutters, 16.0, 1.0 / 60)!!
        assertEquals(1.0 / 1000, shutters[s], 1e-9)
    }

    @Test
    fun autoMeterOpensUpWhenNoShutterIsSlowEnough() {
        val apertures = listOf(2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0)
        val shutters = LightMath.shuttersInRange(ApertureStep.FULL, 1.0, 1.0 / 1000).map { it.seconds }
        val scene = LightMath.ev100(2.0, 1.0 / 2, 100)!!
        val (a, s) = LightMath.autoExposure(scene, 100, apertures, shutters, 16.0, 1.0 / 125)!!
        assertEquals(0.0, abs(LightMath.overexposure(scene, apertures[a], shutters[s], 100)!!), 0.2)
        assertEquals(1.0, shutters[s], 1e-9)
    }

    @Test
    fun brighterSettingsReadAsStopsOver() {
        val scene = LightMath.ev100(16.0, 1.0 / 125, 100)!!
        assertEquals(1.0, LightMath.overexposure(scene, 16.0, 1.0 / 60, 100)!!, 0.1)
        assertEquals(-2.0, LightMath.overexposure(scene, 16.0, 1.0 / 500, 100)!!, 0.01)
    }

    @Test
    fun previewSizePrefersTheViewAspectUnder1080p() {
        val sizes = listOf(
            4000 to 3000,
            640 to 480,
            1280 to 720,
            1920 to 1080,
        )
        assertEquals(1920 to 1080, choosePreviewSize(sizes, 1080, 2400, rotationDegrees = 90))
    }

    @Test
    fun sixteenByNineStreamKeepsTheLongSideOfAFourByThreeSensor() {
        val field = bufferField(LensField(0.8, 0.6), 1920, 1080)
        assertEquals(0.8, field.tanHalfWidth, 1e-9)
        assertEquals(0.45, field.tanHalfHeight, 1e-9)
        val upright = uprightField(field, 90)
        assertEquals(0.45, upright.tanHalfWidth, 1e-9)
        assertEquals(0.8, upright.tanHalfHeight, 1e-9)
    }

    // Host 1000 × 2000 covered by a 1080 × 1440 image: 1500 px across, 0.4 tan visible.
    private val coverFit = previewFit(1000, 2000, 1080, 1440, 0)!!
    private val coverField = LensField(0.6, 0.8)

    @Test
    fun frameThatFitsNeedsNoZoom() {
        val crop = lensCrop(1000, coverFit, coverField, 50.0, minCameraZoom = 0.5f)!!
        assertEquals(1f, crop.cameraZoom, 1e-4f)
        assertEquals(1f, crop.viewZoom, 1e-4f)
        assertEquals(900f, crop.frameWidth, 0.5f)
        assertEquals(600f, crop.frameHeight, 0.5f)
    }

    @Test
    fun longLensScalesTheViewUpAndKeepsTheCameraAtOne() {
        val crop = lensCrop(1000, coverFit, coverField, 100.0, minCameraZoom = 0.5f)!!
        assertEquals(1f, crop.cameraZoom, 1e-4f)
        assertEquals(2f, crop.viewZoom, 1e-4f)
        assertEquals(900f, crop.frameWidth, 0.5f)
    }

    @Test
    fun wideLensZoomsTheCameraOutInsteadOfShrinkingThePreview() {
        val crop = lensCrop(1000, coverFit, coverField, 30.0, minCameraZoom = 0.5f)!!
        assertEquals(0.6f, crop.cameraZoom, 1e-4f)
        assertEquals(1f, crop.viewZoom, 1e-4f)
        assertEquals(900f, crop.frameWidth, 0.5f)
        assertFalse(crop.clipped)
    }

    @Test
    fun lensWiderThanTheWidestCameraRunsOffTheScreen() {
        val crop = lensCrop(1000, coverFit, coverField, 20.0, minCameraZoom = 0.5f)!!
        assertEquals(0.5f, crop.cameraZoom, 1e-4f)
        assertEquals(1f, crop.viewZoom, 1e-4f)
        assertTrue(crop.clipped)
        val noUltrawide = lensCrop(1000, coverFit, coverField, 30.0)!!
        assertEquals(1f, noUltrawide.cameraZoom, 1e-4f)
        assertTrue(noUltrawide.clipped)
    }

    @Test
    fun frameRegionTurnsWithTheSensor() {
        val (alongLong, alongShort) = frameRegion(LensField(0.8, 0.6), 50.0, 90)
        assertEquals(0.3, alongLong, 1e-9)
        assertEquals(0.6, alongShort, 1e-9)
        val (wideLong, wideShort) = frameRegion(LensField(0.8, 0.6), 50.0, 90, cameraZoom = 0.5f)
        assertEquals(0.15, wideLong, 1e-9)
        assertEquals(0.3, wideShort, 1e-9)
    }

    @Test
    fun primeLensKeepsOneFocalLengthAndZoomKeepsOrder() {
        val settings = GearSettingsState(100, 1, "15", "4", setOf("4"))
        assertEquals(50..50, settings.focalRange)
        assertTrue(settings.setFocalShort(35))
        assertEquals(35..35, settings.focalRange)
        settings.selectLensKind(LensKind.ZOOM)
        assertTrue(settings.setFocalLong(70))
        assertEquals(35..70, settings.focalRange)
        assertFalse(settings.setFocalShort(85))
        assertFalse(settings.setFocalLong(24))
        settings.selectLensKind(LensKind.PRIME)
        assertEquals(35..35, settings.focalRange)
    }

    @Test
    fun widestFocalMatchesWhereTheCropStartsToClip() {
        val sensor = LensField(0.8, 0.6)
        val widest = widestFocal(sensor, 90, 0.5f, 1000, 2000)!!
        // Upright 0.6 × 0.8 covering a 1:2 screen shows 0.4 across; 0.5× doubles it.
        assertEquals(18.0 * 0.5 / 0.4, widest, 1e-9)
        val fit = previewFit(1000, 2000, 1080, 1440, 0)!!
        assertFalse(lensCrop(1000, fit, uprightField(sensor, 90), widest + 0.1, 0.5f, fill = 1f)!!.clipped)
        assertTrue(lensCrop(1000, fit, uprightField(sensor, 90), widest - 0.5, 0.5f, fill = 1f)!!.clipped)
    }

    private val mainCamera = CameraOption("0", LensField(0.7, 0.525), 90, 1f, 8f)
    private val separateWide = CameraOption("2", LensField(1.4, 1.05), 90, 1f, 1f)

    @Test
    fun builtInUltrawideNeedsNoSecondCamera() {
        val zoomsOut = mainCamera.copy(minZoom = 0.5f)
        val cameras = BackCameras.of(zoomsOut, listOf(zoomsOut, separateWide.copy(field = LensField(1.36, 1.02))))
        assertNull(cameras.wide)
        assertEquals(0.5f, cameras.floorZoom, 1e-6f)
        assertEquals("0" to 0.6f, cameras.pick(0.6f).let { it.first.id to it.second })
    }

    @Test
    fun separateUltrawideOpensOnlyWhenTheMainCameraCannotGoWideEnough() {
        val cameras = BackCameras.of(mainCamera, listOf(mainCamera, separateWide))
        assertEquals("2", cameras.wide?.id)
        assertEquals(0.5f, cameras.floorZoom, 1e-6f)
        assertEquals("0", cameras.pick(1f).first.id)
        assertEquals("0", cameras.pick(2f).first.id)
        val (camera, ratio) = cameras.pick(0.7f)
        assertEquals("2", camera.id)
        // 0.7× of the main view is 1.4× (a crop) of a camera twice as wide.
        assertEquals(1.4f, ratio, 1e-6f)
    }

    @Test
    fun phoneWithoutAWideCameraStaysOnTheMainOne() {
        val cameras = BackCameras.of(mainCamera, listOf(mainCamera))
        assertNull(cameras.wide)
        assertEquals(1f, cameras.floorZoom, 1e-6f)
        assertEquals("0" to 1f, cameras.pick(0.6f).let { it.first.id to it.second })
    }
}
