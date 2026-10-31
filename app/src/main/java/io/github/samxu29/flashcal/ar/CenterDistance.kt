/**
 * Distance from the phone camera to whatever is under the center mark.
 *
 * ARCore depth is millimeters, little-endian, and 0 means no reading.
 * Order of preference:
 *  1. Raw depth, keeping only pixels ARCore is confident about. It is not
 *     spatially smoothed, so it does not bleed the background into the
 *     subject, but it is sparse and only exists where there was parallax.
 *  2. Full (smoothed) depth, which covers every pixel but blurs edges.
 *  3. A depth hit, a detected surface, then a feature point, which is only
 *     a rough estimate.
 * Depth images older than [MAX_DEPTH_AGE_NS] are ignored, so a depth map
 * from before the phone moved is never reused for a new scene.
 * Depth is best between about 0.5 m and 5 m; error grows with distance.
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
    private const val MAX_DEPTH_AGE_NS = 200_000_000L
    private const val RAW_RADIUS = 2
    private const val RAW_MIN_SAMPLES = 4
    private const val MIN_CONFIDENCE = 128

    fun read(frame: Frame, viewX: Float, viewY: Float): DistanceReading? {
        if (frame.camera.trackingState != TrackingState.TRACKING) return null
        val point = normalizedImagePoint(frame, viewX, viewY)
        val depth = rawDepthMeters(frame, point)?.takeIf { it.inRange() }
            ?: smoothDepthMeters(frame, point)?.takeIf { it.inRange() }
        if (depth != null) return DistanceReading(depth, DistanceSource.DEPTH)
        return hitReading(frame, viewX, viewY)
    }

    private fun rawDepthMeters(frame: Frame, point: Pair<Float, Float>): Float? {
        val depth = try {
            frame.acquireRawDepthImage16Bits()
        } catch (_: Exception) {
            return null
        }
        depth.use {
            if (!depth.isFresh(frame)) return null
            val confidence = try {
                frame.acquireRawDepthConfidenceImage()
            } catch (_: Exception) {
                return null
            }
            confidence.use {
                if (confidence.width != depth.width || confidence.height != depth.height) return null
                return medianMeters(depth, point, RAW_RADIUS, RAW_MIN_SAMPLES, confidence)
            }
        }
    }

    private fun smoothDepthMeters(frame: Frame, point: Pair<Float, Float>): Float? {
        val depth = try {
            frame.acquireDepthImage16Bits()
        } catch (_: Exception) {
            return null
        }
        depth.use {
            if (!depth.isFresh(frame)) return null
            return medianMeters(depth, point, radius = 1, minSamples = 1, confidence = null)
        }
    }

    private fun Image.isFresh(frame: Frame): Boolean =
        timestamp <= 0L || frame.timestamp - timestamp <= MAX_DEPTH_AGE_NS

    private fun medianMeters(
        depth: Image,
        point: Pair<Float, Float>,
        radius: Int,
        minSamples: Int,
        confidence: Image?,
    ): Float? {
        val plane = depth.planes.firstOrNull() ?: return null
        val confidencePlane = confidence?.let { it.planes.firstOrNull() ?: return null }
        val px = (point.first * depth.width).toInt().coerceIn(0, depth.width - 1)
        val py = (point.second * depth.height).toInt().coerceIn(0, depth.height - 1)
        val samples = ArrayList<Int>((2 * radius + 1) * (2 * radius + 1))
        for (oy in -radius..radius) {
            for (ox in -radius..radius) {
                val sx = px + ox
                val sy = py + oy
                if (sx !in 0 until depth.width || sy !in 0 until depth.height) continue
                if (confidencePlane != null && confidenceAt(confidencePlane, sx, sy) < MIN_CONFIDENCE) continue
                samples += depthMillimeters(plane, sx, sy) ?: continue
            }
        }
        if (samples.size < minSamples) return null
        samples.sort()
        return samples[samples.size / 2] / 1000f
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

    private fun confidenceAt(plane: Image.Plane, x: Int, y: Int): Int {
        val buffer = plane.buffer
        val index = buffer.position() + y * plane.rowStride + x * plane.pixelStride
        if (index < 0 || index >= buffer.limit()) return 0
        return buffer.get(index).toInt() and 0xFF
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
                plane.subsumedBy == null &&
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
