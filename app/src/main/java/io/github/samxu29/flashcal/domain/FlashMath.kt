/**
 * Guide-number flash math for a manual camera.
 *
 * f-number = GN / distance, with distance in meters.
 * GN = baseGn × √(ISO / 100) × √(power fraction).
 *
 * The built-in kit is a Nikon FM and a Godox iM30:
 * the FM syncs electronic flash at 1/125 s and slower (not the FM2),
 * and the iM30 is about GN 15 m at ISO 100, full power, in seven steps
 * from full down to 1/64. Those steps follow the iM30 printed chart.
 *
 * Aperture lists are the conventional rounded marks (full, half, and third
 * stops) from f/0.7 through f/128, not exact powers of √2. A typed f-number
 * in that range is also accepted. The calculation uses the printed f-number.
 */
package io.github.samxu29.flashcal.domain

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ApertureStop(val label: String, val value: Double)

/** Click spacing on a lens aperture ring. */
enum class ApertureStep {
    FULL,
    HALF,
    THIRD,
    ;

    fun stored(): String = name.lowercase()

    companion object {
        fun fromStored(raw: String?): ApertureStep = when (raw?.trim()?.lowercase()) {
            "half" -> HALF
            "third" -> THIRD
            else -> FULL
        }
    }
}

data class PowerStep(val label: String, val denominator: Int) {
    val fraction: Double get() = 1.0 / denominator
}

object NikonFm {
    val FILM_SPEEDS = listOf(25, 50, 100, 125, 200, 400, 800, 1600, 3200)
}

object GodoxIm30 {
    const val GUIDE_NUMBER_METERS = 15.0
    const val MIN_GUIDE_NUMBER = 1.0
    const val MAX_GUIDE_NUMBER = 80.0
    val POWER_STEPS = listOf(
        PowerStep("Full", 1),
        PowerStep("1/2", 2),
        PowerStep("1/4", 4),
        PowerStep("1/8", 8),
        PowerStep("1/16", 16),
        PowerStep("1/32", 32),
        PowerStep("1/64", 64),
    )
}

object FlashMath {
    private const val FIT_SLACK = 1.259921 // one-third stop of power

    /** Typed f-numbers outside this range are rejected. */
    const val MIN_APERTURE = 0.7
    const val MAX_APERTURE = 128.0

    val FULL_STOPS = listOf(
        ApertureStop("0.7", 0.7),
        ApertureStop("1", 1.0),
        ApertureStop("1.4", 1.4),
        ApertureStop("2", 2.0),
        ApertureStop("2.8", 2.8),
        ApertureStop("4", 4.0),
        ApertureStop("5.6", 5.6),
        ApertureStop("8", 8.0),
        ApertureStop("11", 11.0),
        ApertureStop("16", 16.0),
        ApertureStop("22", 22.0),
        ApertureStop("32", 32.0),
        ApertureStop("45", 45.0),
        ApertureStop("64", 64.0),
        ApertureStop("90", 90.0),
        ApertureStop("128", 128.0),
    )

    /** Half-stop marks from f/0.7 through f/128. */
    val HALF_STOPS = listOf(
        ApertureStop("0.7", 0.7),
        ApertureStop("0.8", 0.8),
        ApertureStop("1", 1.0),
        ApertureStop("1.2", 1.2),
        ApertureStop("1.4", 1.4),
        ApertureStop("1.7", 1.7),
        ApertureStop("2", 2.0),
        ApertureStop("2.4", 2.4),
        ApertureStop("2.8", 2.8),
        ApertureStop("3.3", 3.3),
        ApertureStop("4", 4.0),
        ApertureStop("4.8", 4.8),
        ApertureStop("5.6", 5.6),
        ApertureStop("6.7", 6.7),
        ApertureStop("8", 8.0),
        ApertureStop("9.5", 9.5),
        ApertureStop("11", 11.0),
        ApertureStop("13", 13.0),
        ApertureStop("16", 16.0),
        ApertureStop("19", 19.0),
        ApertureStop("22", 22.0),
        ApertureStop("27", 27.0),
        ApertureStop("32", 32.0),
        ApertureStop("38", 38.0),
        ApertureStop("45", 45.0),
        ApertureStop("54", 54.0),
        ApertureStop("64", 64.0),
        ApertureStop("76", 76.0),
        ApertureStop("90", 90.0),
        ApertureStop("107", 107.0),
        ApertureStop("128", 128.0),
    )

    /** Third-stop marks from f/0.7 through f/128. */
    val THIRD_STOPS = listOf(
        ApertureStop("0.7", 0.7),
        ApertureStop("0.8", 0.8),
        ApertureStop("0.9", 0.9),
        ApertureStop("1", 1.0),
        ApertureStop("1.1", 1.1),
        ApertureStop("1.2", 1.2),
        ApertureStop("1.4", 1.4),
        ApertureStop("1.6", 1.6),
        ApertureStop("1.8", 1.8),
        ApertureStop("2", 2.0),
        ApertureStop("2.2", 2.2),
        ApertureStop("2.5", 2.5),
        ApertureStop("2.8", 2.8),
        ApertureStop("3.2", 3.2),
        ApertureStop("3.5", 3.5),
        ApertureStop("4", 4.0),
        ApertureStop("4.5", 4.5),
        ApertureStop("5", 5.0),
        ApertureStop("5.6", 5.6),
        ApertureStop("6.3", 6.3),
        ApertureStop("7.1", 7.1),
        ApertureStop("8", 8.0),
        ApertureStop("9", 9.0),
        ApertureStop("10", 10.0),
        ApertureStop("11", 11.0),
        ApertureStop("13", 13.0),
        ApertureStop("14", 14.0),
        ApertureStop("16", 16.0),
        ApertureStop("18", 18.0),
        ApertureStop("20", 20.0),
        ApertureStop("22", 22.0),
        ApertureStop("25", 25.0),
        ApertureStop("29", 29.0),
        ApertureStop("32", 32.0),
        ApertureStop("36", 36.0),
        ApertureStop("40", 40.0),
        ApertureStop("45", 45.0),
        ApertureStop("51", 51.0),
        ApertureStop("57", 57.0),
        ApertureStop("64", 64.0),
        ApertureStop("72", 72.0),
        ApertureStop("80", 80.0),
        ApertureStop("90", 90.0),
        ApertureStop("101", 101.0),
        ApertureStop("114", 114.0),
        ApertureStop("128", 128.0),
    )

    fun scale(step: ApertureStep): List<ApertureStop> = when (step) {
        ApertureStep.FULL -> FULL_STOPS
        ApertureStep.HALF -> HALF_STOPS
        ApertureStep.THIRD -> THIRD_STOPS
    }

    fun sameAperture(left: Double, right: Double): Boolean = abs(left - right) < 0.02

    fun parseAperture(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf { it in MIN_APERTURE..MAX_APERTURE }

    /** A printed mark when [value] lands on a full, half, or third stop. */
    fun knownStop(value: Double): ApertureStop? =
        (FULL_STOPS.asSequence() + HALF_STOPS.asSequence() + THIRD_STOPS.asSequence())
            .firstOrNull { sameAperture(it.value, value) }

    fun resolveAperture(text: String): ApertureStop? {
        val value = parseAperture(text) ?: return null
        return knownStop(value) ?: ApertureStop(formatApertureLabel(value), hundredths(value))
    }

    /**
     * Stops of [step] inside the lens limits, plus [custom] when it is not already listed.
     * [low] is the widest f-number and [high] is the smallest.
     */
    fun aperturesInRange(
        step: ApertureStep,
        low: Double,
        high: Double,
        custom: Double? = null,
    ): List<ApertureStop> {
        val start = minOf(low, high)
        val end = maxOf(low, high)
        val included = scale(step).filter { it.value >= start - 1e-9 && it.value <= end + 1e-9 }.toMutableList()
        fun add(value: Double) {
            if (value !in MIN_APERTURE..MAX_APERTURE) return
            if (included.any { sameAperture(it.value, value) }) return
            included += knownStop(value) ?: ApertureStop(formatApertureLabel(value), hundredths(value))
        }
        custom?.let(::add)
        add(start)
        add(end)
        return included.sortedBy { it.value }
    }

    /** Nearest printed marks on [step] for a lens that currently spans [low] to [high]. */
    fun snappedLabels(step: ApertureStep, low: Double, high: Double): Pair<String, String> {
        val ordered = scale(step)
        val widest = nearestStop(minOf(low, high), ordered)
        val smallest = nearestStop(maxOf(low, high), ordered)
        val start = ordered.indexOf(widest)
        val end = ordered.indexOf(smallest)
        return ordered[minOf(start, end)].label to ordered[maxOf(start, end)].label
    }

    fun formatApertureLabel(value: Double): String {
        val hundredths = (value * 100.0).roundToInt().coerceAtLeast(0)
        val whole = hundredths / 100
        val fraction = hundredths % 100
        return when {
            fraction == 0 -> whole.toString()
            fraction % 10 == 0 -> "$whole.${fraction / 10}"
            else -> "$whole.${fraction.toString().padStart(2, '0')}"
        }
    }

    private fun hundredths(value: Double) = (value * 100.0).roundToInt() / 100.0

    val TARGET_STOPS = FULL_STOPS.filter { it.value in 1.4..22.0 }

    val CHART_STOPS = listOf("2", "2.8", "4", "5.6", "8", "11", "16").map { label ->
        FULL_STOPS.first { it.label == label }
    }

    fun effectiveGuideNumber(baseGn: Double, iso: Int, powerFraction: Double): Double {
        return baseGn * sqrt(iso / 100.0) * sqrt(powerFraction)
    }

    fun nearestStop(aperture: Double, stops: List<ApertureStop> = FULL_STOPS): ApertureStop {
        return stops.minBy { abs(log2(it.value) - log2(aperture)) }
    }

    /** Positive when [nearest] is a smaller hole than [exact] (the frame would be darker). */
    fun stopError(exact: Double, nearest: Double): Double = 2.0 * log2(nearest / exact)

    fun apertureFor(
        baseGn: Double,
        iso: Int,
        powerFraction: Double,
        distanceMeters: Double,
        stops: List<ApertureStop> = FULL_STOPS,
    ): ApertureResult? {
        if (baseGn <= 0.0 || iso <= 0 || powerFraction <= 0.0 || distanceMeters <= 0.0 || stops.isEmpty()) {
            return null
        }
        val guideNumber = effectiveGuideNumber(baseGn, iso, powerFraction)
        val exact = guideNumber / distanceMeters
        if (!exact.isFinite() || exact <= 0.0) return null
        val ordered = stops.sortedBy { it.value }
        val nearest = nearestStop(exact, ordered)
        return ApertureResult(
            effectiveGuideNumber = guideNumber,
            exactAperture = exact,
            nearest = nearest,
            toward = toward(nearest, exact, ordered),
            stopError = stopError(exact, nearest.value),
            widest = ordered.first().value,
            tightest = ordered.last().value,
        )
    }

    fun powerForAperture(
        baseGn: Double,
        iso: Int,
        distanceMeters: Double,
        aperture: Double,
        steps: List<PowerStep> = GodoxIm30.POWER_STEPS,
    ): PowerResult? {
        if (baseGn <= 0.0 || iso <= 0 || distanceMeters <= 0.0 || aperture <= 0.0 || steps.isEmpty()) {
            return null
        }
        val guideAtFull = effectiveGuideNumber(baseGn, iso, 1.0)
        if (guideAtFull <= 0.0) return null
        val needed = aperture * distanceMeters
        val fraction = (needed / guideAtFull) * (needed / guideAtFull)
        val minimum = steps.minOf { it.fraction }
        val fit = when {
            fraction > FIT_SLACK -> PowerFit.NEEDS_MORE
            fraction < minimum / FIT_SLACK -> PowerFit.STILL_TOO_MUCH
            else -> PowerFit.MATCH
        }
        val clamped = fraction.coerceIn(minimum, 1.0)
        val step = steps.minBy { abs(log2(it.fraction) - log2(clamped)) }
        val produced = effectiveGuideNumber(baseGn, iso, step.fraction) / distanceMeters
        return PowerResult(
            step = step,
            fit = fit,
            exactFraction = fraction,
            exactAperture = produced,
            stopError = stopError(produced, aperture),
        )
    }

    private fun toward(nearest: ApertureStop, exact: Double, stops: List<ApertureStop>): ApertureStop? {
        val index = stops.indexOfFirst { it.label == nearest.label }
        if (index < 0) return null
        val next = when {
            exact < nearest.value - 1e-6 -> index - 1
            exact > nearest.value + 1e-6 -> index + 1
            else -> return null
        }
        return stops.getOrNull(next)
    }
}

data class ApertureResult(
    val effectiveGuideNumber: Double,
    val exactAperture: Double,
    val nearest: ApertureStop,
    val toward: ApertureStop?,
    val stopError: Double,
    val widest: Double,
    val tightest: Double,
) {
    val withinLensScale: Boolean get() = exactAperture in widest..tightest
}

enum class PowerFit {
    MATCH,
    NEEDS_MORE,
    STILL_TOO_MUCH,
}

data class PowerResult(
    val step: PowerStep,
    val fit: PowerFit,
    val exactFraction: Double,
    val exactAperture: Double,
    val stopError: Double,
)
