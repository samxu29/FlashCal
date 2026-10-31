/**
 * Distance from the phone camera to whatever is under the center mark.
 *
 * ARCore depth is millimeters, little-endian, and 0 means no reading.
 * A depth hit is preferred. If the center has no depth, a detected surface
 * is next, then a feature point, which is only a rough estimate.
 */
package io.github.samxu29.flashcal.ar

import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.TrackingState
import android.media.Image

enum class DistanceSource {
    DEPTH,
    PLANE,
    FEATURE,
}

data class DistanceReading(
    val meters: Float,
    val source: DistanceSource,
)

object CenterDistance {
    private const val MIN_METERS = 0.08f
    private const val MAX_METERS = 25f

    fun read(frame: Frame, viewX: Float, viewY: Float): DistanceReading? {
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        readDepthMeters(frame, viewX, viewY)?.let { meters ->
            if (meters.inRange()) return DistanceReading(meters, DistanceSource.DEPTH)
        }
        return hitReading(frame, viewX, viewY)
    }

    private fun readDepthMeters(frame: Frame, viewX: Float, viewY: Float): Float? {
        val image = try {
            frame.acquireDepthImage16Bits()
        } catch (_: Exception) {
            return null
        }
        try {
            val (nx, ny) = normalizedImagePoint(frame, viewX, viewY)
            val px = (nx * image.width).toInt().coerceIn(0, image.width - 1)
            val py = (ny * image.height).toInt().coerceIn(0, image.height - 1)
            val plane = image.planes.firstOrNull() ?: return null
            val samples = ArrayList<Int>(9)
            for (oy in -1..1) {
                for (ox in -1..1) {
                    val sx = px + ox
                    val sy = py + oy
                    if (sx !in 0 until image.width || sy !in 0 until image.height) continue
                    val millimeters = depthMillimeters(plane, sx, sy) ?: continue
                    samples += millimeters
                }
            }
            if (samples.isEmpty()) return null
            samples.sort()
            return samples[samples.size / 2] / 1000f
        } finally {
            image.close()
        }
    }

    private fun normalizedImagePoint(frame: Frame, viewX: Float, viewY: Float): Pair<Float, Float> {
        val output = FloatArray(2)
        val mapped = try {
            frame.transformCoordinates2d(
                Coordinates2d.VIEW,
                floatArrayOf(viewX, viewY),
                Coordinates2d.IMAGE_NORMALIZED,
                output,
            )
            true
        } catch (_: Exception) {
            false
        }
        if (mapped && output[0] in -0.02f..1.02f && output[1] in -0.02f..1.02f) {
            return output[0].coerceIn(0f, 1f) to output[1].coerceIn(0f, 1f)
        }
        return 0.5f to 0.5f
    }

    private fun depthMillimeters(plane: Image.Plane, x: Int, y: Int): Int? {
        val buffer = plane.buffer
        val index = buffer.position() + y * plane.rowStride + x * plane.pixelStride
        if (index < 0 || index + 1 >= buffer.limit()) return null
        val low = buffer.get(index).toInt() and 0xFF
        val high = buffer.get(index + 1).toInt() and 0xFF
        val millimeters = low or (high shl 8)
        return millimeters.takeIf { it > 0 }
    }

    private fun hitReading(frame: Frame, viewX: Float, viewY: Float): DistanceReading? {
        val hits = try {
            frame.hitTest(viewX, viewY)
        } catch (_: Exception) {
            return null
        }
        hits.firstOrNull { it.usable() && it.trackable is DepthPoint }?.let {
            return DistanceReading(it.distance, DistanceSource.DEPTH)
        }
        hits.firstOrNull { hit ->
            val plane = hit.trackable as? Plane
            hit.usable() && plane != null &&
                plane.trackingState == TrackingState.TRACKING &&
                plane.isPoseInPolygon(hit.hitPose)
        }?.let {
            return DistanceReading(it.distance, DistanceSource.PLANE)
        }
        hits.firstOrNull { hit ->
            val point = hit.trackable as? Point
            hit.usable() && point != null && point.trackingState == TrackingState.TRACKING
        }?.let {
            return DistanceReading(it.distance, DistanceSource.FEATURE)
        }
        return null
    }

    private fun HitResult.usable(): Boolean = distance.isFinite() && distance.inRange()

    private fun Float.inRange(): Boolean = isFinite() && this in MIN_METERS..MAX_METERS
}
