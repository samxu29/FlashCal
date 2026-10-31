package io.github.samxu29.flashcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.domain.DistanceUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

fun formatAperture(value: Double): String = String.format(Locale.US, "%.1f", value)

fun formatOneDecimal(value: Float): String = formatAperture(value.toDouble())

/** Up to two decimals with trailing zeros dropped, for dial limits and steps. */
fun formatDistanceValue(value: Float): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

fun formatGuideNumber(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (abs(rounded - rounded.roundToInt()) < 0.001) {
        rounded.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

fun formatStopAmount(stops: Double): String {
    val rounded = (abs(stops) * 10.0).roundToInt() / 10.0
    val number = if (abs(rounded - rounded.roundToInt()) < 0.001) {
        rounded.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
    return if (rounded == 1.0) "$number stop" else "$number stops"
}

@Composable
fun unitSymbol(unit: DistanceUnit): String =
    stringResource(if (unit == DistanceUnit.FEET) R.string.unit_ft else R.string.unit_m)

/** A reading such as "2.4 m" or "7.9 ft", one decimal in [unit]. */
@Composable
fun distanceText(meters: Float, unit: DistanceUnit): String =
    stringResource(R.string.distance_with_unit, formatOneDecimal(unit.fromMeters(meters)), unitSymbol(unit))

/** A dial value already in [unit], such as "1.5 m" or "12 ft". */
@Composable
fun dialText(value: Float, unit: DistanceUnit): String =
    stringResource(R.string.distance_with_unit, formatDistanceValue(value), unitSymbol(unit))
