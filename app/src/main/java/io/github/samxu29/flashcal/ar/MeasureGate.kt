/**
 * Reads each ARCore frame for the camera screen: subject distance, the spot
 * meter, background distance, light direction and the preview's field.
 *
 * The subject is under the center reticle, or a point the user tapped, which
 * is anchored in the world so the distance and spot reading stay on it while
 * the phone moves ([placeSubject]). An anchored subject's distance is the
 * camera-to-anchor distance, which holds steady once placed. Aiming away,
 * so the subject sits off screen or at its very edge for [AWAY_MS], lets the
 * subject go and returns to the center reticle.
 *
 * The light cue is ARCore's own estimate of the main light, unless the user
 * marked their light ([markLight]) by aiming the cross at it: the cue is then
 * that direction, or with a tapped subject and a light ARCore could anchor,
 * the true direction from the subject to the light, every frame. A new session drops the subject and the light ([onSessionCreated]).
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
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A tapped subject on screen, in view pixels; [onScreen] is false while it is out of view. */
data class SubjectMark(val x: Float, val y: Float, val onScreen: Boolean)

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
    val subject = mutableStateOf<SubjectMark?>(null)
    val backgroundMeters = mutableStateOf<Float?>(null)
    val light = mutableStateOf<LightCue?>(null)
    val viewField = mutableStateOf<ViewField?>(null)
    val lightMarked = mutableStateOf(false)
    internal val meter = ArLightMeter()

    @Volatile var locked: Boolean = false
    @Volatile var measuring: Boolean = true

    /** The camera screen is showing; metering and scene cues run only then. */
    @Volatile var visible: Boolean = false
        set(value) {
            field = value
            meter.active = value
        }

    /** Spot meter radius in view pixels, matching the drawn reticle. */
    @Volatile var spotRadiusPx: Float = 60f
    @Volatile private var width: Int = 0
    @Volatile private var height: Int = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val filter = DistanceFilter()
    private var lastTenths = Int.MIN_VALUE
    private var lastSource: DistanceSource? = null
    private var lastFocusAt = 0L
    private var hasTracked = false
    private var lostSince = 0L
    private var sessionRetries = 0
    private var retryPosted = false
    private var lastCuesAt = 0L
    private var anchor: Anchor? = null
    private var anchorSource = DistanceSource.DEPTH
    private var awaySince = 0L
    private var lightAnchor: Anchor? = null
    private var lightRay: FloatArray? = null
    @Volatile private var markLightRequest = false
    @Volatile private var clearLightRequest = false
    private val backgroundSamples = ArrayDeque<Float>()
    private var backgroundMisses = 0
    @Volatile private var pendingTap: Pair<Float, Float>? = null
    @Volatile private var clearAnchor = false
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val world = FloatArray(4)
    private val clip = FloatArray(4)
    private val retrySession = Runnable {
        retryPosted = false
        clearSubject()
        hasTracked = false
        lostSince = 0L
        clearReading()
        postState(focusRegions, emptyList())
        postState(failure, null)
        postState(sessionFailed, false)
        postState(sessionGeneration, sessionGeneration.intValue + 1)
    }

    fun onSize(width: Int, height: Int) {
        this.width = width
        this.height = height
        lastFocusAt = 0L
        postState(focusRegions, emptyList())
    }

    /** Anchors the subject at the surface under ([x], [y]) on the next frame. */
    fun placeSubject(x: Float, y: Float) {
        clearAnchor = false
        pendingTap = x to y
    }

    /** Anchors the light under the center cross on the next frame. */
    fun markLight() {
        clearLightRequest = false
        markLightRequest = true
    }

    /** Back to ARCore's own light estimate. */
    fun clearLight() {
        markLightRequest = false
        clearLightRequest = true
        postState(lightMarked, false)
    }

    /** A fresh ARCore session: anchors from the old one are gone. */
    fun onSessionCreated() {
        clearSubject()
        clearLight()
    }

    /** Back to the center reticle. */
    fun clearSubject() {
        pendingTap = null
        clearAnchor = true
        postState(subject, null)
    }

    fun onFrame(frame: Frame) {
        if (frame.timestamp > 0L) {
            sessionRetries = 0
            postState(cameraReady, true)
        }
        val now = SystemClock.elapsedRealtime()
        if (restartIfLost(frame, now)) return
        if (width == 0 || height == 0) return
        // Camera AF keeps running while distance is fixed or held.
        if (now - lastFocusAt >= 100L || frame.hasDisplayGeometryChanged()) {
            lastFocusAt = now
            postState(focusRegions, CameraFocus.read(frame))
        }
        if (clearLightRequest) {
            clearLightRequest = false
            try {
                lightAnchor?.detach()
            } catch (_: Exception) {
            }
            lightAnchor = null
            lightRay = null
        }
        if (markLightRequest) {
            markLightRequest = false
            anchorLight(frame)
        }
        if (locked) return
        if (clearAnchor) {
            clearAnchor = false
            dropAnchor()
            filter.reset()
        }
        pendingTap?.let { (x, y) ->
            pendingTap = null
            anchorAt(frame, x, y)
        }
        val mark = trackSubject(frame)
        val spotX = mark?.takeIf { it.onScreen }?.x ?: (width / 2f)
        val spotY = mark?.takeIf { it.onScreen }?.y ?: (height / 2f)
        if (visible) {
            meter.onFrame(frame, spotX, spotY, spotRadiusPx)
            // The light every frame: cheap, and ARCore's estimate changes when it changes.
            postLight(markedLight(frame, mark) ?: SceneCues.light(frame))
            if (now - lastCuesAt >= CUES_MS) {
                lastCuesAt = now
                postState(backgroundMeters, steadyBackground(SceneCues.backgroundMeters(frame)))
                SceneCues.viewField(frame)?.let { field ->
                    val old = viewField.value
                    if (old == null || abs(old.tanHalfWidth - field.tanHalfWidth) > 1e-3f ||
                        abs(old.tanHalfHeight - field.tanHalfHeight) > 1e-3f
                    ) {
                        postState(viewField, field)
                    }
                }
            }
        }
        if (!measuring) return
        val sample = if (anchor != null) anchorDistance(frame) else CenterDistance.read(frame, width / 2f, height / 2f)
        if (!measuring) return
        publish(filter.update(sample, SystemClock.elapsedRealtime()))
    }

    /**
     * The median of the last [BACKGROUND_SAMPLES] background distances (about
     * 2 s), so one noisy depth map does not move it; a few missing readings
     * in a row keep the last value.
     */
    private fun steadyBackground(sample: Float?): Float? {
        if (sample == null) {
            backgroundMisses++
            if (backgroundMisses >= BACKGROUND_SAMPLES) backgroundSamples.clear()
        } else {
            backgroundMisses = 0
            backgroundSamples.addLast(sample)
            while (backgroundSamples.size > BACKGROUND_SAMPLES) backgroundSamples.removeFirst()
        }
        if (backgroundSamples.isEmpty()) return null
        val sorted = backgroundSamples.sorted()
        return sorted[sorted.size / 2]
    }

    private fun postLight(cue: LightCue?) {
        val old = light.value
        if (cue == null || old == null ||
            abs(cue.x - old.x) > 0.01f || abs(cue.y - old.y) > 0.01f ||
            abs(cue.z - old.z) > 0.01f || abs(cue.contrast - old.contrast) > 0.02f
        ) {
            postState(light, cue)
        }
    }

    /**
     * Marks the light under the center cross. Its direction from here is
     * always kept ([lightRay]); when ARCore finds a surface there, the light's
     * position is anchored too ([lightAnchor]), for lights near the subject.
     */
    private fun anchorLight(frame: Frame) {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return
        val view = camera.displayOrientedPose
        val hit = try {
            frame.hitTest(width / 2f, height / 2f).firstOrNull { it.distance.isFinite() && it.distance in 0.08f..25f }
        } catch (_: Exception) {
            null
        }
        try {
            lightAnchor?.detach()
        } catch (_: Exception) {
        }
        lightAnchor = try {
            hit?.createAnchor()
        } catch (_: Exception) {
            null
        }
        lightRay = view.rotateVector(floatArrayOf(0f, 0f, -1f))
        postState(lightMarked, true)
    }

    /**
     * The marked light as the subject sees it, in view terms, or null without
     * a mark. With a tapped subject and an anchored light, it is the true
     * direction from the subject to the light. Otherwise the light is taken
     * as far away, in the direction it was marked; the subject's position
     * then does not matter, and aiming at the light reads as backlight.
     */
    private fun markedLight(frame: Frame, subjectMark: SubjectMark?): LightCue? {
        val ray = lightRay ?: return null
        val view = frame.camera.displayOrientedPose
        val subjectPose = anchor?.takeIf { subjectMark != null && it.trackingState == TrackingState.TRACKING }?.pose
        val lightPose = lightAnchor?.takeIf { it.trackingState == TrackingState.TRACKING }?.pose
        val toLight = if (subjectPose != null && lightPose != null) {
            floatArrayOf(lightPose.tx() - subjectPose.tx(), lightPose.ty() - subjectPose.ty(), lightPose.tz() - subjectPose.tz())
                .takeIf { it[0] * it[0] + it[1] * it[1] + it[2] * it[2] > MIN_LIGHT_GAP * MIN_LIGHT_GAP }
                ?: ray
        } else {
            ray
        }
        val local = view.inverse().rotateVector(toLight)
        val length = sqrt(local[0] * local[0] + local[1] * local[1] + local[2] * local[2])
        if (length <= 1e-4f) return null
        return LightCue(local[0] / length, local[1] / length, local[2] / length, MARKED_CONTRAST)
    }

    private fun anchorAt(frame: Frame, x: Float, y: Float) {
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        val hits = try {
            frame.hitTest(x, y)
        } catch (_: Exception) {
            return
        }
        val usable = hits.filter { it.distance.isFinite() && it.distance in 0.08f..25f }
        val hit = usable.firstOrNull { it.trackable is DepthPoint }
            ?: usable.firstOrNull { (it.trackable as? Plane)?.isPoseInPolygon(it.hitPose) == true }
            ?: usable.firstOrNull { it.trackable is Point }
            ?: return
        val created = try {
            hit.createAnchor()
        } catch (_: Exception) {
            return
        }
        dropAnchor()
        anchor = created
        awaySince = 0L
        anchorSource = when (hit.trackable) {
            is DepthPoint -> DistanceSource.DEPTH
            is Plane -> DistanceSource.PLANE
            else -> DistanceSource.FEATURE
        }
        filter.reset()
    }

    private fun dropAnchor() {
        try {
            anchor?.detach()
        } catch (_: Exception) {
        }
        anchor = null
    }

    /** Projects the anchored subject onto the view, or null without one. */
    private fun trackSubject(frame: Frame): SubjectMark? {
        val current = anchor ?: return null
        if (current.trackingState == TrackingState.STOPPED) {
            clearSubject()
            return null
        }
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return subject.value
        val pose = current.pose
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 100f)
        Matrix.multiplyMM(viewProjection, 0, projectionMatrix, 0, viewMatrix, 0)
        world[0] = pose.tx()
        world[1] = pose.ty()
        world[2] = pose.tz()
        world[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProjection, 0, world, 0)
        val mark = if (clip[3] <= 0f) {
            SubjectMark(width / 2f, height / 2f, onScreen = false)
        } else {
            val x = (clip[0] / clip[3] + 1f) / 2f * width
            val y = (1f - clip[1] / clip[3]) / 2f * height
            SubjectMark(x, y, onScreen = x in 0f..width.toFloat() && y in 0f..height.toFloat())
        }
        val away = !mark.onScreen ||
            abs(mark.x - width / 2f) > width * AWAY_FRACTION ||
            abs(mark.y - height / 2f) > height * AWAY_FRACTION
        val now = SystemClock.elapsedRealtime()
        if (!away) {
            awaySince = 0L
        } else if (awaySince == 0L) {
            awaySince = now
        } else if (now - awaySince >= AWAY_MS) {
            dropAnchor()
            filter.reset()
            postState(subject, null)
            return null
        }
        val old = subject.value
        if (old == null || old.onScreen != mark.onScreen || abs(old.x - mark.x) > 1f || abs(old.y - mark.y) > 1f) {
            postState(subject, mark)
        }
        return mark
    }

    private fun anchorDistance(frame: Frame): DistanceReading? {
        val current = anchor ?: return null
        if (current.trackingState != TrackingState.TRACKING) return null
        val from = frame.camera.pose
        val to = current.pose
        val dx = to.tx() - from.tx()
        val dy = to.ty() - from.ty()
        val dz = to.tz() - from.tz()
        val meters = sqrt(dx * dx + dy * dy + dz * dz)
        return if (meters.isFinite() && meters in 0.08f..25f) DistanceReading(meters, anchorSource) else null
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
        // The light meter may still be releasing the camera. Retry before showing a failure.
        if (sessionRetries >= 2) {
            postState(focusRegions, emptyList())
            postState(sessionFailed, true)
            return
        }
        if (retryPosted) return
        sessionRetries++
        retryPosted = true
        mainHandler.postDelayed(retrySession, 400)
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
        clearSubject()
        clearLight()
        mainHandler.removeCallbacks(retrySession)
        retryPosted = false
        sessionRetries = 0
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
        const val CUES_MS = 200L

        /** A light closer than this to the subject gives no usable direction. */
        const val MIN_LIGHT_GAP = 0.15f

        /** A marked light is treated as a clear key light. */
        const val MARKED_CONTRAST = 0.7f
        const val BACKGROUND_SAMPLES = 10

        /** Farther than this fraction of the view from center, on either axis, counts as aimed away. */
        const val AWAY_FRACTION = 0.4f
        const val AWAY_MS = 500L
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
