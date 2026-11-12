/**
 * Software spot metering on ARCore's CPU camera image.
 *
 * ARCore owns auto-exposure, so the camera cannot be told where to meter.
 * Instead the luma under the reticle is compared with the luma of the whole
 * frame, both linearized with the sRGB curve. The camera's exposure is what
 * renders the frame average; the spot is that many stops brighter or darker.
 * When the spot covers the whole frame this equals the camera's own reading.
 *
 * Phone tone curves are not exactly sRGB, so the offset is approximate and
 * gets less trustworthy toward clipped highlights or crushed shadows, which
 * are flagged instead of being turned into a number.
 */
package io.github.samxu29.flashcal.ar

import android.media.Image
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

enum class SpotClip { NONE, HIGH, LOW }

/** Spot brightness relative to the frame average, in stops. */
data class SpotSample(val stops: Double, val clip: SpotClip)

internal object SpotMeter {
    private const val FRAME_STEP = 8
    private const val HIGH_LUMA = 245
    private const val LOW_LUMA = 8
    private const val MAX_STOPS = 6.0

    /** sRGB-decoded luma, 0–1, for each 8-bit code value. */
    private val linear = FloatArray(256) { code ->
        val v = code / 255.0
        (if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)).toFloat()
    }

    /**
     * Meters a disc of [radiusPx] view pixels centered on ([viewX], [viewY]).
     * Null while ARCore has no CPU image for this frame.
     */
    fun read(frame: Frame, viewX: Float, viewY: Float, radiusPx: Float): SpotSample? {
        val image = try {
            frame.acquireCameraImage()
        } catch (_: Exception) {
            return null
        }
        image.use {
            val mapped = FloatArray(4)
            try {
                frame.transformCoordinates2d(
                    Coordinates2d.VIEW,
                    floatArrayOf(viewX, viewY, viewX + radiusPx, viewY),
                    Coordinates2d.IMAGE_PIXELS,
                    mapped,
                )
            } catch (_: Exception) {
                return null
            }
            val radius = hypot(mapped[2] - mapped[0], mapped[3] - mapped[1]).coerceAtLeast(2f)
            return measure(image, mapped[0], mapped[1], radius)
        }
    }

    private fun measure(image: Image, cx: Float, cy: Float, radius: Float): SpotSample? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        fun luma(x: Int, y: Int): Int {
            val index = y * rowStride + x * pixelStride
            return if (index in 0 until buffer.limit()) buffer.get(index).toInt() and 0xFF else -1
        }

        var frameSum = 0.0
        var frameCount = 0
        var y = FRAME_STEP / 2
        while (y < height) {
            var x = FRAME_STEP / 2
            while (x < width) {
                val code = luma(x, y)
                if (code >= 0) {
                    frameSum += linear[code]
                    frameCount++
                }
                x += FRAME_STEP
            }
            y += FRAME_STEP
        }

        val r = radius.roundToInt()
        val centerX = cx.roundToInt()
        val centerY = cy.roundToInt()
        val step = (r / 12).coerceAtLeast(1)
        var spotSum = 0.0
        var codeSum = 0L
        var spotCount = 0
        var dy = -r
        while (dy <= r) {
            var dx = -r
            while (dx <= r) {
                if (dx * dx + dy * dy <= r * r) {
                    val px = centerX + dx
                    val py = centerY + dy
                    if (px in 0 until width && py in 0 until height) {
                        val code = luma(px, py)
                        if (code >= 0) {
                            spotSum += linear[code]
                            codeSum += code
                            spotCount++
                        }
                    }
                }
                dx += step
            }
            dy += step
        }
        if (frameCount == 0 || spotCount == 0) return null
        val frameMean = frameSum / frameCount
        val spotMean = spotSum / spotCount
        if (frameMean <= 0.0) return null
        val meanCode = codeSum / spotCount
        val clip = when {
            meanCode >= HIGH_LUMA -> SpotClip.HIGH
            meanCode <= LOW_LUMA -> SpotClip.LOW
            else -> SpotClip.NONE
        }
        val stops = if (spotMean <= 0.0) -MAX_STOPS else ln(spotMean / frameMean) / ln(2.0)
        return SpotSample(stops.coerceIn(-MAX_STOPS, MAX_STOPS), clip)
    }
}
