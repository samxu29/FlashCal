package io.github.samxu29.flashcal.ar

import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.ImageMetadata

/** A camera-reported AF metering rectangle in normalized, display-rotated view coordinates. */
data class FocusRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)

object CameraFocus {
    fun read(frame: Frame): List<FocusRegion> = try {
        val metadata = frame.imageMetadata
        // OFF and EDOF do not use autofocus metering regions.
        if (metadata.getByte(ImageMetadata.CONTROL_AF_MODE).toInt() !in 1..4) {
            emptyList()
        } else {
            val dimensions = frame.camera.imageIntrinsics.imageDimensions
            FocusRegionMapping.map(
                regions = metadata.getIntArray(ImageMetadata.CONTROL_AF_REGIONS),
                crop = metadata.getIntArray(ImageMetadata.SCALER_CROP_REGION),
                imageWidth = dimensions[0],
                imageHeight = dimensions[1],
            ) { imagePoints ->
                FloatArray(imagePoints.size).also { viewPoints ->
                    // ARCore handles preview cropping and display rotation.
                    frame.transformCoordinates2d(
                        Coordinates2d.IMAGE_NORMALIZED,
                        imagePoints,
                        Coordinates2d.VIEW_NORMALIZED,
                        viewPoints,
                    )
                }
            }
        }
    } catch (_: Exception) {
        // Metadata may be unavailable or omit AF regions. Never invent an area.
        emptyList()
    }
}

internal object FocusRegionMapping {
    fun map(
        regions: IntArray,
        crop: IntArray,
        imageWidth: Int,
        imageHeight: Int,
        toView: (FloatArray) -> FloatArray,
    ): List<FocusRegion> {
        if (crop.size != 4 || crop[2] <= 0 || crop[3] <= 0 ||
            imageWidth <= 0 || imageHeight <= 0 || regions.size % 5 != 0
        ) return emptyList()

        // NDK metadata uses (left, top, width, height) for the sensor crop,
        // but (left, top, right, bottom, weight) for each AF region.
        // The CPU image can have a further centered crop for its aspect ratio.
        val imageAspect = imageWidth.toFloat() / imageHeight
        val width = minOf(crop[2].toFloat(), crop[3] * imageAspect)
        val height = minOf(crop[3].toFloat(), crop[2] / imageAspect)
        val left = crop[0] + (crop[2] - width) / 2f
        val top = crop[1] + (crop[3] - height) / 2f

        return regions.asList().chunked(5).mapNotNull { region ->
            val (x0, y0, x1, y1, weight) = region
            // Zero weight means device-selected/default behavior, not a known area.
            if (weight <= 0 || x1 <= x0 || y1 <= y0) return@mapNotNull null
            val points = toView(
                floatArrayOf(
                    (x0 - left) / width, (y0 - top) / height,
                    (x1 - left) / width, (y0 - top) / height,
                    (x1 - left) / width, (y1 - top) / height,
                    (x0 - left) / width, (y1 - top) / height,
                ),
            )
            if (points.size != 8 || points.any { !it.isFinite() }) return@mapNotNull null
            val xs = listOf(points[0], points[2], points[4], points[6])
            val ys = listOf(points[1], points[3], points[5], points[7])
            val bounds = FocusRegion(
                xs.min().coerceIn(0f, 1f), ys.min().coerceIn(0f, 1f),
                xs.max().coerceIn(0f, 1f), ys.max().coerceIn(0f, 1f),
            )
            bounds.takeIf { it.right > it.left && it.bottom > it.top }
        }
    }
}
