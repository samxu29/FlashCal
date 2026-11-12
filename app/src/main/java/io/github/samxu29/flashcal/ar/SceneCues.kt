/**
 * Scene facts read from an ARCore frame besides the subject distance:
 * how far the background is, where the main light comes from, and how wide
 * the preview is (for drawing the lens frame lines).
 */
package io.github.samxu29.flashcal.ar

import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import kotlin.math.sqrt

/**
 * The main light as seen from the camera, as a unit vector pointing toward
 * the light: +x is screen right, +y screen up, +z behind the phone (the light
 * is over the photographer's shoulder), -z in front (the subject is backlit).
 * [contrast] is 0 for flat light up to 1 for a hard, dominant light.
 */
data class LightCue(val x: Float, val y: Float, val z: Float, val contrast: Float)

/** Half-angle tangents of what the preview shows, across and down the view. */
data class ViewField(val tanHalfWidth: Float, val tanHalfHeight: Float)

internal object SceneCues {
    private const val BACKGROUND_PERCENTILE = 0.85
    private const val MIN_DEPTH_SAMPLES = 64
    private const val DEPTH_STEP = 2

    /**
     * A far percentile of the smoothed depth map, in meters: what most of the
     * scene behind the subject sits at. Null without depth.
     */
    fun backgroundMeters(frame: Frame): Float? {
        val depth = try {
            frame.acquireDepthImage16Bits()
        } catch (_: Exception) {
            return null
        }
        depth.use {
            val plane = depth.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val values = ArrayList<Int>((depth.width / DEPTH_STEP + 1) * (depth.height / DEPTH_STEP + 1))
            var y = 0
            while (y < depth.height) {
                var x = 0
                while (x < depth.width) {
                    val index = y * plane.rowStride + x * plane.pixelStride
                    if (index + 1 < buffer.limit()) {
                        val mm = (buffer.get(index).toInt() and 0xFF) or ((buffer.get(index + 1).toInt() and 0xFF) shl 8)
                        if (mm > 0) values += mm
                    }
                    x += DEPTH_STEP
                }
                y += DEPTH_STEP
            }
            if (values.size < MIN_DEPTH_SAMPLES) return null
            values.sort()
            return values[(values.size * BACKGROUND_PERCENTILE).toInt().coerceAtMost(values.lastIndex)] / 1000f
        }
    }

    fun light(frame: Frame): LightCue? = try {
        val estimate = frame.lightEstimate
        if (estimate.state != LightEstimate.State.VALID) {
            null
        } else {
            val world = estimate.environmentalHdrMainLightDirection
            val view = frame.camera.displayOrientedPose.inverse().rotateVector(world)
            val length = sqrt(view[0] * view[0] + view[1] * view[1] + view[2] * view[2])
            if (length <= 1e-4f) {
                null
            } else {
                val main = estimate.environmentalHdrMainLightIntensity.let { (it[0] + it[1] + it[2]) / 3f }
                val ambient = estimate.environmentalHdrAmbientSphericalHarmonics
                    .let { (it[0] + it[1] + it[2]) / 3f }
                    .coerceAtLeast(0f)
                // ARCore's units are relative; this only orders flat versus hard light.
                val contrast = if (main + ambient <= 0f) 0f else (main / (main + ambient * 4f)).coerceIn(0f, 1f)
                LightCue(view[0] / length, view[1] / length, view[2] / length, contrast)
            }
        }
    } catch (_: Exception) {
        null
    }

    fun viewField(frame: Frame): ViewField? {
        val projection = FloatArray(16)
        return try {
            frame.camera.getProjectionMatrix(projection, 0, 0.05f, 100f)
            if (projection[0] <= 0f || projection[5] <= 0f) null else ViewField(1f / projection[0], 1f / projection[5])
        } catch (_: Exception) {
            null
        }
    }
}
