/**
 * Reflected spot meter fed by ARCore frames, so metering and distance share
 * one camera session and the camera is never reopened between them.
 *
 * The camera's exposure from [Frame.getImageMetadata] gives the EV of the
 * frame average; [SpotMeter] then shifts it by how much brighter or darker
 * the reticle (or tracked subject) is. Reported as EV at ISO 100.
 */
package io.github.samxu29.flashcal.ar

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.google.ar.core.Frame
import com.google.ar.core.ImageMetadata
import io.github.samxu29.flashcal.domain.EvWindow
import io.github.samxu29.flashcal.domain.LightMath
import io.github.samxu29.flashcal.meter.MeterReading
import kotlin.math.roundToInt

@Stable
internal class ArLightMeter {
    val reading = mutableStateOf<MeterReading?>(null)
    val clip = mutableStateOf(SpotClip.NONE)
    val unavailable = mutableStateOf(false)

    /** Frames are metered only while true. */
    @Volatile var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            reset()
        }

    private val main = Handler(Looper.getMainLooper())
    private val filter = EvWindow()
    private var emptyFrames = 0
    private var lastTenth = Int.MIN_VALUE

    /** Meters a disc of [radiusPx] view pixels around ([viewX], [viewY]). */
    fun onFrame(frame: Frame, viewX: Float, viewY: Float, radiusPx: Float) {
        if (!active || frame.timestamp <= 0L) return
        val frameEv = frameEv(frame)
        if (frameEv == null) {
            emptyFrames++
            if (emptyFrames == EMPTY_FRAME_LIMIT) post { unavailable.value = true }
            return
        }
        emptyFrames = 0
        val spot = SpotMeter.read(frame, viewX, viewY, radiusPx)
        val ev = frameEv + (spot?.stops ?: 0.0)
        val median = filter.add(ev, SystemClock.elapsedRealtime())
        val spotClip = spot?.clip ?: SpotClip.NONE
        if (clip.value != spotClip) post { clip.value = spotClip }
        val tenth = (median * 10.0).roundToInt()
        if (tenth == lastTenth) return
        lastTenth = tenth
        post {
            unavailable.value = false
            reading.value = MeterReading(median)
        }
    }

    fun reset() {
        filter.reset()
        emptyFrames = 0
        lastTenth = Int.MIN_VALUE
        post {
            reading.value = null
            clip.value = SpotClip.NONE
            unavailable.value = false
        }
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val EMPTY_FRAME_LIMIT = 45
        const val AE_STATE_SEARCHING = 1
        const val AE_STATE_PRECAPTURE = 5

        /** EV at ISO 100 for this frame, or null while AE is still moving or metadata is missing. */
        fun frameEv(frame: Frame): Double? = try {
            val metadata = frame.imageMetadata
            val state = runCatching { metadata.getByte(ImageMetadata.CONTROL_AE_STATE).toInt() }.getOrNull()
            if (state == AE_STATE_SEARCHING || state == AE_STATE_PRECAPTURE) {
                null
            } else {
                val exposureNs = metadata.getLong(ImageMetadata.SENSOR_EXPOSURE_TIME)
                val sensitivity = metadata.getInt(ImageMetadata.SENSOR_SENSITIVITY)
                val aperture = metadata.getFloat(ImageMetadata.LENS_APERTURE).toDouble()
                val boost = runCatching {
                    metadata.getInt(ImageMetadata.CONTROL_POST_RAW_SENSITIVITY_BOOST)
                }.getOrDefault(100)
                if (aperture <= 0.0 || exposureNs <= 0L || sensitivity <= 0) {
                    null
                } else {
                    LightMath.ev100(
                        aperture = aperture,
                        shutterSeconds = exposureNs / 1_000_000_000.0,
                        iso = LightMath.effectiveIso(sensitivity, boost),
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
