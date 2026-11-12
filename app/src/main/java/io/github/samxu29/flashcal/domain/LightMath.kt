/**
 * Reflected-light exposure from the camera's chosen shutter, ISO, and aperture.
 *
 * EV100 = log2(N² / t) − log2(ISO / 100), with t in seconds.
 * The shutter for another aperture and ISO is t = N² / (2^EV100 × ISO / 100).
 * Shutter labels are the conventional full, half, and third-stop series.
 * Phone auto-exposure aims at a manufacturer middle gray, so treat the result
 * as a starting point rather than a calibrated incident reading.
 */
package io.github.samxu29.flashcal.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

data class ShutterSpeed(val seconds: Double, val label: String)

enum class ExposureLimit {
    INSIDE,
    TOO_BRIGHT,
    TOO_DARK,
}

object LightMath {
    val SPEEDS = listOf(
        ShutterSpeed(30.0, "30 s"),
        ShutterSpeed(15.0, "15 s"),
        ShutterSpeed(8.0, "8 s"),
        ShutterSpeed(4.0, "4 s"),
        ShutterSpeed(2.0, "2 s"),
        ShutterSpeed(1.0, "1 s"),
        ShutterSpeed(1.0 / 2, "1/2"),
        ShutterSpeed(1.0 / 4, "1/4"),
        ShutterSpeed(1.0 / 8, "1/8"),
        ShutterSpeed(1.0 / 15, "1/15"),
        ShutterSpeed(1.0 / 30, "1/30"),
        ShutterSpeed(1.0 / 60, "1/60"),
        ShutterSpeed(1.0 / 125, "1/125"),
        ShutterSpeed(1.0 / 250, "1/250"),
        ShutterSpeed(1.0 / 500, "1/500"),
        ShutterSpeed(1.0 / 1000, "1/1000"),
        ShutterSpeed(1.0 / 2000, "1/2000"),
        ShutterSpeed(1.0 / 4000, "1/4000"),
        ShutterSpeed(1.0 / 8000, "1/8000"),
    )

    /** Slowest and fastest marks on every shutter scale. */
    val SLOWEST_SECONDS = SPEEDS.first().seconds
    val FASTEST_SECONDS = SPEEDS.last().seconds

    /** Half-stop marks from 30 s through 1/8000. */
    val HALF_SPEEDS = listOf(
        ShutterSpeed(30.0, "30 s"),
        ShutterSpeed(20.0, "20 s"),
        ShutterSpeed(15.0, "15 s"),
        ShutterSpeed(10.0, "10 s"),
        ShutterSpeed(8.0, "8 s"),
        ShutterSpeed(6.0, "6 s"),
        ShutterSpeed(4.0, "4 s"),
        ShutterSpeed(3.0, "3 s"),
        ShutterSpeed(2.0, "2 s"),
        ShutterSpeed(1.5, "1.5 s"),
        ShutterSpeed(1.0, "1 s"),
        ShutterSpeed(0.7, "0.7 s"),
        ShutterSpeed(1.0 / 2, "1/2"),
        ShutterSpeed(1.0 / 3, "1/3"),
        ShutterSpeed(1.0 / 4, "1/4"),
        ShutterSpeed(1.0 / 6, "1/6"),
        ShutterSpeed(1.0 / 8, "1/8"),
        ShutterSpeed(1.0 / 10, "1/10"),
        ShutterSpeed(1.0 / 15, "1/15"),
        ShutterSpeed(1.0 / 20, "1/20"),
        ShutterSpeed(1.0 / 30, "1/30"),
        ShutterSpeed(1.0 / 45, "1/45"),
        ShutterSpeed(1.0 / 60, "1/60"),
        ShutterSpeed(1.0 / 90, "1/90"),
        ShutterSpeed(1.0 / 125, "1/125"),
        ShutterSpeed(1.0 / 180, "1/180"),
        ShutterSpeed(1.0 / 250, "1/250"),
        ShutterSpeed(1.0 / 350, "1/350"),
        ShutterSpeed(1.0 / 500, "1/500"),
        ShutterSpeed(1.0 / 750, "1/750"),
        ShutterSpeed(1.0 / 1000, "1/1000"),
        ShutterSpeed(1.0 / 1500, "1/1500"),
        ShutterSpeed(1.0 / 2000, "1/2000"),
        ShutterSpeed(1.0 / 3000, "1/3000"),
        ShutterSpeed(1.0 / 4000, "1/4000"),
        ShutterSpeed(1.0 / 6000, "1/6000"),
        ShutterSpeed(1.0 / 8000, "1/8000"),
    )

    /** Third-stop marks from 30 s through 1/8000. */
    val THIRD_SPEEDS = listOf(
        ShutterSpeed(30.0, "30 s"),
        ShutterSpeed(25.0, "25 s"),
        ShutterSpeed(20.0, "20 s"),
        ShutterSpeed(15.0, "15 s"),
        ShutterSpeed(13.0, "13 s"),
        ShutterSpeed(10.0, "10 s"),
        ShutterSpeed(8.0, "8 s"),
        ShutterSpeed(6.0, "6 s"),
        ShutterSpeed(5.0, "5 s"),
        ShutterSpeed(4.0, "4 s"),
        ShutterSpeed(3.2, "3.2 s"),
        ShutterSpeed(2.5, "2.5 s"),
        ShutterSpeed(2.0, "2 s"),
        ShutterSpeed(1.6, "1.6 s"),
        ShutterSpeed(1.3, "1.3 s"),
        ShutterSpeed(1.0, "1 s"),
        ShutterSpeed(0.8, "0.8 s"),
        ShutterSpeed(0.6, "0.6 s"),
        ShutterSpeed(0.5, "1/2"),
        ShutterSpeed(0.4, "0.4 s"),
        ShutterSpeed(0.3, "0.3 s"),
        ShutterSpeed(1.0 / 4, "1/4"),
        ShutterSpeed(1.0 / 5, "1/5"),
        ShutterSpeed(1.0 / 6, "1/6"),
        ShutterSpeed(1.0 / 8, "1/8"),
        ShutterSpeed(1.0 / 10, "1/10"),
        ShutterSpeed(1.0 / 13, "1/13"),
        ShutterSpeed(1.0 / 15, "1/15"),
        ShutterSpeed(1.0 / 20, "1/20"),
        ShutterSpeed(1.0 / 25, "1/25"),
        ShutterSpeed(1.0 / 30, "1/30"),
        ShutterSpeed(1.0 / 40, "1/40"),
        ShutterSpeed(1.0 / 50, "1/50"),
        ShutterSpeed(1.0 / 60, "1/60"),
        ShutterSpeed(1.0 / 80, "1/80"),
        ShutterSpeed(1.0 / 100, "1/100"),
        ShutterSpeed(1.0 / 125, "1/125"),
        ShutterSpeed(1.0 / 160, "1/160"),
        ShutterSpeed(1.0 / 200, "1/200"),
        ShutterSpeed(1.0 / 250, "1/250"),
        ShutterSpeed(1.0 / 320, "1/320"),
        ShutterSpeed(1.0 / 400, "1/400"),
        ShutterSpeed(1.0 / 500, "1/500"),
        ShutterSpeed(1.0 / 640, "1/640"),
        ShutterSpeed(1.0 / 800, "1/800"),
        ShutterSpeed(1.0 / 1000, "1/1000"),
        ShutterSpeed(1.0 / 1250, "1/1250"),
        ShutterSpeed(1.0 / 1600, "1/1600"),
        ShutterSpeed(1.0 / 2000, "1/2000"),
        ShutterSpeed(1.0 / 2500, "1/2500"),
        ShutterSpeed(1.0 / 3200, "1/3200"),
        ShutterSpeed(1.0 / 4000, "1/4000"),
        ShutterSpeed(1.0 / 5000, "1/5000"),
        ShutterSpeed(1.0 / 6400, "1/6400"),
        ShutterSpeed(1.0 / 8000, "1/8000"),
    )

    /** Sensor ISO times the post-raw boost. A boost of 100, or an absent one, changes nothing. */
    fun effectiveIso(sensitivity: Int, boostPercent: Int): Int {
        if (sensitivity <= 0) return 0
        if (boostPercent <= 0) return sensitivity
        return (sensitivity.toLong() * boostPercent / 100L).toInt().coerceAtLeast(1)
    }

    fun ev100(aperture: Double, shutterSeconds: Double, iso: Int): Double? {
        if (aperture <= 0.0 || shutterSeconds <= 0.0 || iso <= 0 ||
            !aperture.isFinite() || !shutterSeconds.isFinite()
        ) {
            return null
        }
        val ev = log2(aperture * aperture / shutterSeconds) - log2(iso / 100.0)
        return ev.takeIf { it.isFinite() }
    }

    fun shutterSeconds(ev100: Double, aperture: Double, iso: Int): Double? {
        if (aperture <= 0.0 || iso <= 0 || !aperture.isFinite() || !ev100.isFinite()) return null
        val seconds = aperture * aperture / (2.0.pow(ev100) * (iso / 100.0))
        return seconds.takeIf { it.isFinite() && it > 0.0 }
    }

    fun nearestShutter(seconds: Double): ShutterSpeed? {
        if (seconds <= 0.0 || !seconds.isFinite()) return null
        val target = log2(seconds)
        return SPEEDS.minBy { abs(log2(it.seconds) - target) }
    }

    fun shutterScale(step: ApertureStep): List<ShutterSpeed> = when (step) {
        ApertureStep.FULL -> SPEEDS
        ApertureStep.HALF -> HALF_SPEEDS
        ApertureStep.THIRD -> THIRD_SPEEDS
    }

    /** Nearest printed mark on [step]. A non-positive time becomes 1/125. */
    fun canonicalShutter(seconds: Double, step: ApertureStep): Double {
        val scale = shutterScale(step)
        if (seconds <= 0.0 || !seconds.isFinite()) {
            return scale.first { it.label == "1/125" }.seconds
        }
        val target = log2(seconds)
        return scale.minBy { abs(log2(it.seconds) - target) }.seconds
    }

    fun sameShutter(left: Double, right: Double): Boolean {
        if (left <= 0.0 || right <= 0.0 || !left.isFinite() || !right.isFinite()) return false
        return abs(log2(left) - log2(right)) < 0.08
    }

    /** Marks of [step] from the slower endpoint through the faster one, inclusive. */
    fun shuttersInRange(step: ApertureStep, slowest: Double, fastest: Double): List<ShutterSpeed> {
        val scale = shutterScale(step)
        val slow = canonicalShutter(maxOf(slowest, fastest), step)
        val fast = canonicalShutter(minOf(slowest, fastest), step)
        val start = scale.indexOfFirst { sameShutter(it.seconds, slow) }.coerceAtLeast(0)
        val end = scale.indexOfFirst { sameShutter(it.seconds, fast) }.let { if (it < 0) scale.lastIndex else it }
        return scale.subList(minOf(start, end), maxOf(start, end) + 1)
    }

    /** Shutter to set for this scene, aperture, and ISO, snapped to [speeds]. */
    fun shutterForScene(ev100: Double, aperture: Double, iso: Int, speeds: List<ShutterSpeed>): ShutterSpeed? {
        val seconds = shutterSeconds(ev100, aperture, iso) ?: return null
        if (speeds.isEmpty()) return null
        val target = log2(seconds)
        return speeds.minBy { abs(log2(it.seconds) - target) }
    }

    /** Whether [seconds] falls outside [slowest] and [fastest] by more than a third stop. */
    fun shutterLimit(seconds: Double, slowest: Double, fastest: Double): ExposureLimit {
        if (seconds <= 0.0 || !seconds.isFinite() || slowest <= 0.0 || fastest <= 0.0) return ExposureLimit.INSIDE
        val third = 2.0.pow(1.0 / 3.0)
        val slow = maxOf(slowest, fastest)
        val fast = minOf(slowest, fastest)
        return when {
            seconds > slow * third -> ExposureLimit.TOO_DARK
            seconds < fast / third -> ExposureLimit.TOO_BRIGHT
            else -> ExposureLimit.INSIDE
        }
    }

    fun indexNearest(speeds: List<ShutterSpeed>, seconds: Double): Int {
        if (speeds.isEmpty()) return 0
        if (seconds <= 0.0 || !seconds.isFinite()) {
            return speeds.indexOfFirst { it.label == "1/125" }.coerceAtLeast(0)
        }
        val target = log2(seconds)
        return speeds.indices.minBy { abs(log2(speeds[it].seconds) - target) }
    }

    /** Outside the labeled series by more than a third stop. */
    fun exposureLimit(seconds: Double): ExposureLimit {
        if (seconds <= 0.0 || !seconds.isFinite()) return ExposureLimit.INSIDE
        val third = 2.0.pow(1.0 / 3.0)
        val slowest = SPEEDS.first().seconds
        val fastest = SPEEDS.last().seconds
        return when {
            seconds > slowest * third -> ExposureLimit.TOO_DARK
            seconds < fastest / third -> ExposureLimit.TOO_BRIGHT
            else -> ExposureLimit.INSIDE
        }
    }

    fun formatEv(ev100: Double): String = String.format(Locale.US, "%.1f", ev100)

    /**
     * Stops to add to the chosen shutter and aperture so they match [sceneEv].
     * Positive means open up or use a slower shutter.
     */
    fun exposureOffset(sceneEv: Double, aperture: Double, shutterSeconds: Double, iso: Int): Double? {
        val chosen = ev100(aperture, shutterSeconds, iso) ?: return null
        if (!sceneEv.isFinite()) return null
        return (chosen - sceneEv).takeIf { it.isFinite() }
    }

    /**
     * Stops of extra light the chosen shutter and aperture give compared with
     * the metered [sceneEv]. Positive is overexposed, negative is underexposed.
     */
    fun overexposure(sceneEv: Double, aperture: Double, shutterSeconds: Double, iso: Int): Double? =
        exposureOffset(sceneEv, aperture, shutterSeconds, iso)?.let { -it }

    /**
     * Indices of the aperture and shutter that come closest to [sceneEv].
     *
     * When the scene falls between two marks, the pick rounds toward the side
     * the film forgives: over by default, since modern negative film holds
     * highlights better than shadows, or under when [preferOver] is false.
     * If no pair lands on that side, the nearest pair either way is used.
     *
     * In aperture priority (the default) the aperture stays where it is and the
     * shutter moves to the closest mark on that side, up to one shutter step
     * away. Only when no shutter in range gets that close does the aperture
     * move, by as little as possible, to a pair within a sixth of a stop of the
     * best match. [shutterPriority] swaps the roles of the two dials.
     */
    fun autoExposure(
        sceneEv: Double,
        iso: Int,
        apertures: List<Double>,
        shutters: List<Double>,
        currentAperture: Double,
        currentShutter: Double,
        preferOver: Boolean = true,
        shutterPriority: Boolean = false,
    ): Pair<Int, Int>? {
        if (apertures.isEmpty() || shutters.isEmpty() || !sceneEv.isFinite()) return null
        val pairs = apertures.indices.flatMap { a ->
            shutters.indices.mapNotNull { s ->
                overexposure(sceneEv, apertures[a], shutters[s], iso)?.let { Triple(a, s, it) }
            }
        }
        if (pairs.isEmpty()) return null
        fun stops(from: Double, to: Double): Double =
            if (from <= 0.0 || to <= 0.0) 0.0 else abs(log2(to / from))
        val step = if (shutterPriority) {
            apertures.zipWithNext { a, b -> 2.0 * stops(a, b) }.maxOrNull() ?: 1.0
        } else {
            shutters.zipWithNext { a, b -> stops(a, b) }.maxOrNull() ?: 1.0
        }
        val leaning = pairs.filter { if (preferOver) it.third >= -ON_METER else it.third <= ON_METER }
        val pool = leaning.ifEmpty { pairs }
        val reach = if (leaning.isEmpty()) step / 2.0 else step
        val apertureMove = { pick: Triple<Int, Int, Double> -> stops(currentAperture, apertures[pick.first]) }
        val shutterMove = { pick: Triple<Int, Int, Double> -> stops(currentShutter, shutters[pick.second]) }
        val kept = if (shutterPriority) shutterMove else apertureMove
        val adjusted = if (shutterPriority) apertureMove else shutterMove
        val keptIndex = { pick: Triple<Int, Int, Double> -> if (shutterPriority) pick.second else pick.first }

        val home = keptIndex(pairs.minBy(kept))
        val inPlace = pool.filter { keptIndex(it) == home && abs(it.third) <= reach + 0.01 }
        if (inPlace.isNotEmpty()) {
            return inPlace.minWith(compareBy<Triple<Int, Int, Double>> { abs(it.third) }.thenBy(adjusted))
                .let { it.first to it.second }
        }
        val best = pool.minOf { abs(it.third) }
        return pool
            .filter { abs(it.third) <= best + 1.0 / 6.0 }
            .minWith(compareBy(kept).thenBy(adjusted))
            .let { it.first to it.second }
    }

    /** Printed marks are rounded (1/125 is really 1/128), so this much either way reads as correct. */
    private const val ON_METER = 0.1

    fun formatStops(stops: Double): String {
        if (!stops.isFinite()) return "0"
        val tenths = (stops * 10.0).roundToInt()
        if (tenths == 0) return "0"
        return String.format(Locale.US, "%+.1f", tenths / 10.0)
    }
}

/** Median of recent EV samples so one odd frame does not move the reading. */
internal class EvWindow {
    private val samples = ArrayDeque<Pair<Long, Double>>()

    fun reset() {
        samples.clear()
    }

    fun add(ev: Double, now: Long): Double {
        samples.addLast(now to ev)
        while (samples.first().first < now - WINDOW_MS) samples.removeFirst()
        val sorted = samples.map { it.second }.sorted()
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val WINDOW_MS = 400L
    }
}
