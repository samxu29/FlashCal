/**
 * The fixed-distance dial for one profile.
 *
 * Limits and increment are integer hundredths of [unit] (centimeters for
 * meters, hundredths of a foot for feet), so a 0.5 ft or 0.25 m step stays
 * exact. Flash math always receives meters through [metersAt].
 */
package io.github.samxu29.flashcal.domain

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

enum class DistanceUnit(val metersPerUnit: Double, val maxValue: Int) {
    METERS(1.0, 100),
    FEET(0.3048, 330),
    ;

    val maxHundredths: Int get() = maxValue * 100

    fun fromMeters(meters: Float): Float = (meters / metersPerUnit).toFloat()

    fun toMeters(value: Float): Float = (value * metersPerUnit).toFloat()

    fun stored(): String = name.lowercase()

    /** The unit shown second, beside the primary reading. */
    val other: DistanceUnit get() = if (this == METERS) FEET else METERS

    companion object {
        fun fromStored(raw: String?): DistanceUnit =
            if (raw?.trim()?.lowercase() == "feet") FEET else METERS
    }
}

data class DistanceScale(
    val minimum: Int,
    val maximum: Int,
    val increment: Int,
    val unit: DistanceUnit = DistanceUnit.METERS,
) {
    init {
        require(minimum in 1..unit.maxHundredths)
        require(maximum in minimum..unit.maxHundredths)
        require(increment in 1..unit.maxHundredths)
    }

    val size: Int get() = (maximum - minimum) / increment + 1

    /** Dial value at [index], in [unit]. */
    fun valueAt(index: Int): Float = (minimum + index.coerceIn(0, size - 1) * increment) / 100f

    fun metersAt(index: Int): Float = unit.toMeters(valueAt(index))

    fun indexFor(meters: Float): Int =
        if (meters.isFinite()) {
            ((unit.fromMeters(meters) * 100f - minimum) / increment).roundToInt().coerceIn(0, size - 1)
        } else {
            0
        }

    fun snap(meters: Float): Float = metersAt(indexFor(meters))

    /**
     * The same physical range in [target], rounded to marks a person would
     * pick: whole feet or tenths of a meter, and a familiar step size.
     */
    fun inUnit(target: DistanceUnit): DistanceScale {
        if (target == unit) return this
        val limitStep = if (target == DistanceUnit.FEET) 100 else 10
        fun limit(hundredths: Int): Int {
            val meters = unit.toMeters(hundredths / 100f)
            val converted = target.fromMeters(meters) * 100f
            return ((converted / limitStep).roundToInt() * limitStep)
                .coerceIn(limitStep, target.maxHundredths)
        }
        val exactStep = target.fromMeters(unit.toMeters(increment / 100f)) * 100f
        val niceSteps = if (target == DistanceUnit.FEET) FEET_STEPS else METER_STEPS
        val step = niceSteps.minBy { abs(ln(it.toFloat()) - ln(exactStep)) }
        val low = limit(minimum)
        return DistanceScale(low, limit(maximum).coerceAtLeast(low), step, target)
    }

    companion object {
        val DEFAULT = DistanceScale(100, 1_000, 50)
        private val METER_STEPS = listOf(5, 10, 25, 50, 100, 200, 500)
        private val FEET_STEPS = listOf(25, 50, 100, 200, 500, 1_000)

        /** Typed text as hundredths of [unit], or null when it is not a usable dial value. */
        fun hundredths(text: String, unit: DistanceUnit): Int? = runCatching {
            BigDecimal(text.trim().replace(',', '.')).movePointRight(2).intValueExact()
        }.getOrNull()?.takeIf { it in 1..unit.maxHundredths }

        fun fromText(minimum: String, maximum: String, increment: String, unit: DistanceUnit): DistanceScale? {
            val min = hundredths(minimum, unit) ?: return null
            val max = hundredths(maximum, unit) ?: return null
            val step = hundredths(increment, unit) ?: return null
            return if (max >= min) DistanceScale(min, max, step, unit) else null
        }
    }
}
