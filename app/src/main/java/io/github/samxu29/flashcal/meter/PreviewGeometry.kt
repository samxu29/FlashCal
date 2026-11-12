/**
 * Preview size and rotation for a Camera2 [android.view.TextureView].
 *
 * The SurfaceTexture already turns the camera buffer upright for the phone's
 * natural (portrait) orientation, and mirrors the front camera. The view only
 * has to undo the display rotation and keep the upright aspect.
 * [relativeRotation] is still the sensor-to-display angle, used to pick a
 * buffer size whose aspect matches the screen.
 *
 * Checked on a device: rotating the view by the sensor angle left both
 * cameras 90° off, with the top of the scene on the left.
 *
 * Lens frame: angles are kept as tangents of half the field of view, since a
 * flat image is linear in tan, not in degrees. The profile lens is a focal
 * length on 35 mm film (36 × 24 mm), drawn landscape across the screen.
 * [lensCrop] fits that frame to most of the width without ever shrinking the
 * preview (shrinking left black bars): wide lenses zoom the camera out, which
 * on some phones switches to the ultrawide camera, and long
 * lenses scale the view up. A lens wider than even that is marked clipped.
 * [frameRegion] is the same frame as a fraction of the sensor, for metering.
 */
package io.github.samxu29.flashcal.meter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal fun relativeRotation(sensorOrientation: Int, displayRotationDegrees: Int, front: Boolean): Int {
    val turned = if (front) {
        (sensorOrientation + displayRotationDegrees) % 360
    } else {
        (sensorOrientation - displayRotationDegrees + 360) % 360
    }
    return if (front) (360 - turned) % 360 else turned
}

/** Clockwise view rotation that undoes the display rotation. */
internal fun previewViewRotation(displayRotationDegrees: Int): Int =
    (360 - ((displayRotationDegrees % 360) + 360) % 360) % 360

/** Buffer size as the SurfaceTexture shows it in the phone's natural orientation. */
internal fun uprightContentSize(bufferWidth: Int, bufferHeight: Int, sensorOrientation: Int): Pair<Int, Int> =
    if (sensorOrientation % 180 != 0) bufferHeight to bufferWidth else bufferWidth to bufferHeight

internal fun choosePreviewSize(
    sizes: List<Pair<Int, Int>>,
    viewWidth: Int,
    viewHeight: Int,
    rotationDegrees: Int,
): Pair<Int, Int>? {
    if (sizes.isEmpty() || viewWidth <= 0 || viewHeight <= 0) return null
    val swapped = rotationDegrees % 180 != 0
    val targetWidth = if (swapped) viewHeight else viewWidth
    val targetHeight = if (swapped) viewWidth else viewHeight
    val targetRatio = targetWidth.toFloat() / targetHeight
    val capped = sizes.filter { (width, height) ->
        width in 320..1920 && height in 240..1920
    }
    val pool = capped.ifEmpty { sizes }
    return pool.minBy { (width, height) ->
        val ratioPenalty = abs(width.toFloat() / height - targetRatio)
        val sizePenalty = abs(width - targetWidth) / targetWidth.toFloat() * 0.05f
        ratioPenalty + sizePenalty
    }
}

internal data class PreviewFit(
    val viewWidth: Int,
    val viewHeight: Int,
    val rotationDegrees: Int,
)

/**
 * Size the TextureView to the upright content, then rotate that view clockwise.
 *
 * The view is scaled so the content covers the host after the rotation, and
 * the host clips the overflow.
 */
internal fun previewFit(
    hostWidth: Int,
    hostHeight: Int,
    contentWidth: Int,
    contentHeight: Int,
    rotationDegrees: Int,
): PreviewFit? {
    if (hostWidth <= 0 || hostHeight <= 0 || contentWidth <= 0 || contentHeight <= 0) return null
    val rotation = ((rotationDegrees % 360) + 360) % 360
    val quarter = rotation % 180 != 0
    val shownWidth = if (quarter) contentHeight.toFloat() else contentWidth.toFloat()
    val shownHeight = if (quarter) contentWidth.toFloat() else contentHeight.toFloat()
    val scale = max(hostWidth / shownWidth, hostHeight / shownHeight)
    return PreviewFit(
        viewWidth = (contentWidth * scale).roundToInt().coerceAtLeast(1),
        viewHeight = (contentHeight * scale).roundToInt().coerceAtLeast(1),
        rotationDegrees = rotation,
    )
}

/** Half of a 35 mm film frame, in mm. */
internal const val FILM_HALF_LONG = 18.0
internal const val FILM_HALF_SHORT = 12.0

/** Half-angle tangents of an image: across its width and down its height. */
internal data class LensField(val tanHalfWidth: Double, val tanHalfHeight: Double)

/**
 * Field of a [bufferWidth] × [bufferHeight] stream from a sensor whose active
 * area spans [sensorField] (landscape, long side first). Streams of another
 * aspect are center crops of the sensor, so one axis keeps the full angle.
 */
internal fun bufferField(sensorField: LensField, bufferWidth: Int, bufferHeight: Int): LensField {
    val long = maxOf(bufferWidth, bufferHeight).toDouble()
    val short = minOf(bufferWidth, bufferHeight).toDouble()
    val bufferAspect = long / short
    val sensorAspect = sensorField.tanHalfWidth / sensorField.tanHalfHeight
    return if (bufferAspect >= sensorAspect) {
        LensField(sensorField.tanHalfWidth, sensorField.tanHalfWidth / bufferAspect)
    } else {
        LensField(sensorField.tanHalfHeight * bufferAspect, sensorField.tanHalfHeight)
    }
}

/** A landscape field as the upright (portrait) preview shows it. */
internal fun uprightField(field: LensField, sensorOrientation: Int): LensField =
    if (sensorOrientation % 180 != 0) LensField(field.tanHalfHeight, field.tanHalfWidth) else field

internal data class LensCrop(
    /** Camera zoom ratio; below 1 the phone switches to its ultrawide camera. */
    val cameraZoom: Float,
    /** Extra scale on the preview view, around its center; never below 1. */
    val viewZoom: Float,
    val frameWidth: Float,
    val frameHeight: Float,
    /** Even the widest camera cannot show the whole frame across the screen. */
    val clipped: Boolean,
)

/**
 * How to show a landscape 35 mm frame at [focalMm] spanning [fill] of the
 * host width. The preview always covers the host, so no black bars appear.
 * A frame too wide for the covered view asks the camera to zoom out, down to
 * [minCameraZoom]; a narrower one scales the view up. [field] is the upright
 * preview content at camera zoom 1; [fit] is how [previewFit] laid it out.
 */
internal fun lensCrop(
    hostWidth: Int,
    fit: PreviewFit,
    field: LensField,
    focalMm: Double,
    minCameraZoom: Float = 1f,
    fill: Float = 0.9f,
): LensCrop? {
    if (hostWidth <= 0 || focalMm <= 0.0) return null
    val quarter = fit.rotationDegrees % 180 != 0
    val shownWidth = (if (quarter) fit.viewHeight else fit.viewWidth).toDouble()
    val tanX = if (quarter) field.tanHalfHeight else field.tanHalfWidth
    if (tanX <= 0.0 || shownWidth <= 0.0) return null
    val pixelsPerTan = shownWidth / (2.0 * tanX)
    val visibleTan = hostWidth / (2.0 * pixelsPerTan)
    val frameTanX = FILM_HALF_LONG / focalMm
    val frameTanY = FILM_HALF_SHORT / focalMm
    val needed = fill * visibleTan / frameTanX
    val cameraZoom = if (needed >= 1.0) 1.0 else maxOf(needed, minCameraZoom.toDouble().coerceAtMost(1.0))
    val viewZoom = if (needed >= 1.0) needed else 1.0
    val scale = pixelsPerTan * cameraZoom * viewZoom
    val frameWidth = (2.0 * frameTanX * scale).toFloat()
    return LensCrop(
        cameraZoom = cameraZoom.toFloat(),
        viewZoom = viewZoom.toFloat(),
        frameWidth = frameWidth,
        frameHeight = (2.0 * frameTanY * scale).toFloat(),
        clipped = frameWidth > hostWidth + 0.5f,
    )
}

/**
 * The landscape-on-screen 35 mm frame as fractions of the camera's view at
 * [cameraZoom]: (along the sensor's long side, along its short side), at
 * most 1. Metering regions follow the zoomed view, not the raw sensor.
 */
internal fun frameRegion(
    sensorField: LensField,
    focalMm: Double,
    sensorOrientation: Int,
    cameraZoom: Float = 1f,
): Pair<Double, Double> {
    val zoomed = LensField(sensorField.tanHalfWidth / cameraZoom, sensorField.tanHalfHeight / cameraZoom)
    val screenAcross = FILM_HALF_LONG / focalMm
    val screenDown = FILM_HALF_SHORT / focalMm
    val (alongLong, alongShort) = if (sensorOrientation % 180 != 0) {
        screenDown to screenAcross
    } else {
        screenAcross to screenDown
    }
    return (alongLong / zoomed.tanHalfWidth).coerceIn(0.0, 1.0) to
        (alongShort / zoomed.tanHalfHeight).coerceIn(0.0, 1.0)
}

/**
 * Widest 35 mm focal length whose frame still spans the screen width when the
 * preview covers a [hostWidth] × [hostHeight] screen at [minCameraZoom].
 * Assumes the stream keeps the sensor's aspect, as [LightMeter] asks for.
 */
internal fun widestFocal(
    sensorField: LensField,
    sensorOrientation: Int,
    minCameraZoom: Float,
    hostWidth: Int,
    hostHeight: Int,
): Double? {
    if (hostWidth <= 0 || hostHeight <= 0 || minCameraZoom <= 0f) return null
    val upright = uprightField(sensorField, sensorOrientation)
    val hostAspect = hostWidth.toDouble() / hostHeight
    val contentAspect = upright.tanHalfWidth / upright.tanHalfHeight
    val visible = if (hostAspect < contentAspect) upright.tanHalfHeight * hostAspect else upright.tanHalfWidth
    return FILM_HALF_LONG * minCameraZoom / visible
}

/**
 * Widest 35 mm focal length whose vertical frame (24 mm across, 36 mm down)
 * fits a preview showing [tanHalfWidth] × [tanHalfHeight] of half-angle.
 */
internal fun widestFittingFocal(tanHalfWidth: Double, tanHalfHeight: Double): Double? {
    if (tanHalfWidth <= 0.0 || tanHalfHeight <= 0.0) return null
    return maxOf(FILM_HALF_SHORT / tanHalfWidth, FILM_HALF_LONG / tanHalfHeight)
}

/**
 * The upright (portrait) preview's visible half-angle tangents when a camera
 * of [sensorField] covers a [hostWidth] × [hostHeight] screen.
 */
internal fun visibleField(sensorField: LensField, sensorOrientation: Int, hostWidth: Int, hostHeight: Int): LensField? {
    if (hostWidth <= 0 || hostHeight <= 0) return null
    val upright = uprightField(sensorField, sensorOrientation)
    val hostAspect = hostWidth.toDouble() / hostHeight
    val contentAspect = upright.tanHalfWidth / upright.tanHalfHeight
    return if (hostAspect < contentAspect) {
        LensField(upright.tanHalfHeight * hostAspect, upright.tanHalfHeight)
    } else {
        LensField(upright.tanHalfWidth, upright.tanHalfWidth / hostAspect)
    }
}

/** A camera the meter can open: its sensor field (landscape) and zoom ratio range. */
internal data class CameraOption(
    val id: String,
    val field: LensField,
    val sensorOrientation: Int,
    val minZoom: Float,
    val maxZoom: Float,
)

/**
 * The main back camera and, on phones that list their ultrawide as its own
 * camera instead of zooming the main one below 1×, that wider camera.
 * All zoom values the screen uses are relative to [main]'s field.
 */
internal data class BackCameras(val main: CameraOption, val wide: CameraOption?) {
    /** Lowest zoom, relative to [main], that some back camera can show. */
    val floorZoom: Float
        get() = wide?.let { minOf(main.minZoom, (it.minZoom * main.field.tanHalfWidth / it.field.tanHalfWidth).toFloat()) }
            ?: main.minZoom

    /**
     * Which camera shows [zoom] (relative to [main]) and at what ratio of its
     * own. The main camera is kept whenever it can go that wide. The wide
     * camera's ratio may be above 1, which the meter applies as a crop.
     */
    fun pick(zoom: Float): Pair<CameraOption, Float> {
        if (wide == null || zoom >= main.minZoom - 1e-3f) return main to zoom.coerceIn(main.minZoom, main.maxZoom)
        val ratio = (zoom * wide.field.tanHalfWidth / main.field.tanHalfWidth).toFloat()
        return wide to ratio.coerceAtLeast(wide.minZoom)
    }

    companion object {
        /** A separate camera only counts as wider when it beats the main one's widest by this much. */
        const val WIDER_BY = 1.05

        /** [main] plus the widest of [others] when it is meaningfully wider than [main] can zoom out. */
        fun of(main: CameraOption, others: List<CameraOption>): BackCameras {
            val mainReach = main.field.tanHalfWidth / main.minZoom
            val wide = others
                .filter { it.id != main.id }
                .maxByOrNull { it.field.tanHalfWidth / it.minZoom }
                ?.takeIf { it.field.tanHalfWidth / it.minZoom > mainReach * WIDER_BY }
            return BackCameras(main, wide)
        }
    }
}
