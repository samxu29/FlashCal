package io.github.samxu29.flashcal.ar

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.google.ar.core.Frame
import com.google.ar.core.TrackingFailureReason
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

    @Volatile var locked: Boolean = false
    @Volatile var measuring: Boolean = true
    @Volatile private var width: Int = 0
    @Volatile private var height: Int = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val filter = DistanceFilter()
    private var lastTenths = Int.MIN_VALUE
    private var lastSource: DistanceSource? = null
    private var lastFocusAt = 0L

    fun onSize(width: Int, height: Int) {
        this.width = width
        this.height = height
        lastFocusAt = 0L
        postState(focusRegions, emptyList())
    }

    fun onFrame(frame: Frame) {
        if (frame.timestamp > 0L) postState(cameraReady, true)
        if (width == 0 || height == 0) return
        val now = SystemClock.elapsedRealtime()
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
        filter.reset()
        lastTenths = Int.MIN_VALUE
        lastSource = null
        postState(reading, null)
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
}

private class DistanceFilter {
    private var meters: Float? = null
    private var source: DistanceSource = DistanceSource.DEPTH
    private var depthAt: Long = 0L
    private var lastAt: Long = 0L

    fun reset() {
        meters = null
        source = DistanceSource.DEPTH
        depthAt = 0L
        lastAt = 0L
    }

    fun update(sample: DistanceReading?, now: Long): DistanceReading? {
        if (sample == null) {
            if (now - lastAt > HOLD_MS) meters = null
            val held = meters ?: return null
            return DistanceReading(held, source)
        }
        lastAt = now
        if (sample.source == DistanceSource.DEPTH) {
            source = DistanceSource.DEPTH
            depthAt = now
        } else if (now - depthAt > DEPTH_STICK_MS) {
            source = sample.source
        }
        val previous = meters
        meters = if (previous == null) sample.meters else previous + (sample.meters - previous) * 0.4f
        return DistanceReading(meters ?: return null, source)
    }

    private companion object {
        const val HOLD_MS = 600L
        const val DEPTH_STICK_MS = 400L
    }
}
