package io.github.samxu29.flashcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.domain.ApertureResult
import io.github.samxu29.flashcal.domain.PowerFit
import io.github.samxu29.flashcal.domain.PowerResult
import kotlin.math.abs

@Composable
fun apertureAdvice(result: ApertureResult): String {
    if (result.exactAperture < result.widest) {
        return stringResource(R.string.advice_not_enough, formatAperture(result.exactAperture))
    }
    if (result.exactAperture > result.tightest) {
        return stringResource(R.string.advice_too_much, formatAperture(result.exactAperture))
    }
    val nearest = result.nearest.label
    if (abs(result.stopError) < 0.15) {
        return stringResource(R.string.advice_set, nearest)
    }
    val verb = stringResource(if (result.stopError > 0) R.string.verb_open else R.string.verb_close)
    val amount = formatStopAmount(result.stopError)
    val toward = result.toward
    return if (toward != null) {
        stringResource(R.string.advice_bias, nearest, verb, amount, toward.label)
    } else {
        stringResource(R.string.advice_bias_end, nearest, verb, amount)
    }
}

@Composable
fun powerAdvice(result: PowerResult, targetLabel: String): String {
    val produced = formatAperture(result.exactAperture).removeSuffix(".0")
    return when (result.fit) {
        PowerFit.NEEDS_MORE -> stringResource(R.string.power_needs_more, produced)
        PowerFit.STILL_TOO_MUCH -> stringResource(R.string.power_still_hot, produced)
        PowerFit.MATCH -> {
            if (abs(result.stopError) < 0.2) {
                stringResource(R.string.power_match, result.step.label, targetLabel)
            } else {
                stringResource(R.string.power_near, result.step.label, produced, targetLabel)
            }
        }
    }
}
