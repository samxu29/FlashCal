/**
 * Turns per-frame center distances into the reading shown on screen.
 *
 * Readings are the median of the last 300 ms of samples from one source, so single-frame spikes are dropped and a new scene takes
 * over within a few frames instead of being averaged with the old one.
 *
 * If ARCore tracked and then stays lost for 4 s (typically
 * after walking somewhere new), [sessionGeneration] is bumped so the AR view
 * rebuilds its session. Low light and featureless surfaces are left alone;
 * a new session would not track there either. [resetMeasurement] does the
 * same restart on demand from the reset button.
 */
package io.github.samxu29.flashcal.ar

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.ar.core.Frame
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlin.math.roundToInt

@Stable
class MeasureGate {
    val reading = mutableStateOf<DistanceReading?>(null)
    val focusRegions = mutableStateOf<List<FocusRegion>>(emptyList())
    val failure = mutableStateOf<TrackingFailureReason?>(null)
    val cameraBlocked = mutableStateOf(false)
    val arBlocked = mutableStateOf(false)
    val sessionFailed = mutableStateOf(false)
    val cameraReady = mutableStateOf(false)
    val sessionGeneration = mutableIntStateOf(0)

    @Volatile var locked: Boolean = false
    @Volatile var measuring: Boolean = true
    @Volatile private var width: Int = 0
    @Volatile private var height: Int = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val filter = DistanceFilter()
    private var lastTenths = Int.MIN_VALUE
    private var lastSource: DistanceSource? = null
    private var lastFocusAt = 0L
    private var hasTracked = false
    private var lostSince = 0L

    fun onSize(width: Int, height: Int) {
        this.width = width
        this.height = height
        lastFocusAt = 0L
        postState(focusRegions, emptyList())
    }

    fun onFrame(frame: Frame) {
        if (frame.timestamp > 0L) postState(cameraReady, true)
        val now = SystemClock.elapsedRealtime()
        if (restartIfLost(frame, now)) return
        if (width == 0 || height == 0) return
        // Camera AF keeps running while distance is fixed or held.
        if (now - lastFocusAt >= 100L || frame.hasDisplayGeometryChanged()) {
            lastFocusAt = now
            postState(focusRegions, CameraFocus.read(frame))
        }
        if (!measuring || locked) return
        val sample = CenterDistance.read(frame, width / 2f, height / 2f)
        if (!measuring) return
        publish(filter.update(sample, SystemClock.elapsedRealtime()))
    }

    fun pauseMeasurement() {
        measuring = false
        clearReading()
    }

    fun resumeMeasurement() {
        measuring = true
    }

    fun setFailure(reason: TrackingFailureReason?) {
        postState(failure, reason)
    }

    fun setCameraBlocked(blocked: Boolean) {
        if (blocked) postState(focusRegions, emptyList())
        postState(cameraBlocked, blocked)
    }

    fun setArBlocked(blocked: Boolean) {
        if (blocked) postState(focusRegions, emptyList())
        postState(arBlocked, blocked)
    }

    fun markSessionFailed() {
        postState(focusRegions, emptyList())
        postState(sessionFailed, true)
    }

    private fun restartIfLost(frame: Frame, now: Long): Boolean {
        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            hasTracked = true
            lostSince = 0L
            return false
        }
        if (!hasTracked || !measuring || locked || camera.trackingFailureReason in NOT_RESTARTABLE) {
            lostSince = 0L
            return false
        }
        if (lostSince == 0L) lostSince = now
        if (now - lostSince < RESTART_AFTER_MS) return false
        resetMeasurement()
        return true
    }

    /** Drops the current reading and starts a fresh ARCore session. */
    fun resetMeasurement() {
        locked = false
        hasTracked = false
        lostSince = 0L
        clearReading()
        postState(focusRegions, emptyList())
        postState(failure, null)
        postState(sessionGeneration, sessionGeneration.intValue + 1)
    }

    private fun clearReading() {
        filter.reset()
        lastTenths = Int.MIN_VALUE
        lastSource = null
        postState(reading, null)
    }

    private fun publish(sample: DistanceReading?) {
        if (sample == null) {
            if (lastTenths != Int.MIN_VALUE || lastSource != null) {
                lastTenths = Int.MIN_VALUE
                lastSource = null
                postState(reading, null)
            }
            return
        }
        val tenths = (sample.meters * 10f).roundToInt()
        if (tenths == lastTenths && sample.source == lastSource) return
        lastTenths = tenths
        lastSource = sample.source
        postState(reading, DistanceReading(tenths / 10f, sample.source))
    }

    private fun <T> postState(state: androidx.compose.runtime.MutableState<T>, value: T) {
        if (state.value == value) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            state.value = value
        } else {
            mainHandler.post { state.value = value }
        }
    }

    private companion object {
        const val RESTART_AFTER_MS = 4_000L
        val NOT_RESTARTABLE = setOf(
            TrackingFailureReason.INSUFFICIENT_LIGHT,
            TrackingFailureReason.INSUFFICIENT_FEATURES,
            TrackingFailureReason.CAMERA_UNAVAILABLE,
        )
    }
}

internal class DistanceFilter {
    private class Sample(val at: Long, val meters: Float)

    private val window = ArrayDeque<Sample>()
    private var source: DistanceSource? = null
    private var held: DistanceReading? = null
    private var depthAt = 0L
    private var lastAt = 0L

    fun reset() {
        window.clear()
        source = null
        held = null
        depthAt = 0L
        lastAt = 0L
    }

    fun update(sample: DistanceReading?, now: Long): DistanceReading? {
        if (sample == null) {
            if (now - lastAt > HOLD_MS) held = null
            return held
        }
        if (sample.source == DistanceSource.DEPTH) {
            depthAt = now
        } else if (source == DistanceSource.DEPTH && now - depthAt <= DEPTH_STICK_MS) {
            return held
        }
        lastAt = now
        if (sample.source != source) {
            window.clear()
            source = sample.source
        }
        window.addLast(Sample(now, sample.meters))
        while (window.first().at < now - WINDOW_MS) window.removeFirst()
        val sorted = window.map { it.meters }.sorted()
        return DistanceReading(sorted[sorted.size / 2], sample.source).also { held = it }
    }

    private companion object {
        const val WINDOW_MS = 300L
        const val HOLD_MS = 600L
        const val DEPTH_STICK_MS = 400L
    }
}
