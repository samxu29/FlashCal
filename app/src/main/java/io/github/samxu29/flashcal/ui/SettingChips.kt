package io.github.samxu29.flashcal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.domain.GodoxIm30
import io.github.samxu29.flashcal.domain.NikonFm

@Composable
fun IsoChipRow(settings: GearSettingsState, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    ChipRow(title = if (showTitle) stringResource(R.string.film_iso) else null, modifier = modifier) {
        NikonFm.FILM_SPEEDS.forEach { iso ->
            FilterChip(
                selected = settings.iso == iso,
                onClick = confirmationClick { settings.iso = iso },
                label = { Text(iso.toString()) },
            )
        }
    }
}

@Composable
fun PowerChipRow(settings: GearSettingsState, modifier: Modifier = Modifier) {
    ChipRow(title = stringResource(R.string.flash_power), modifier = modifier) {
        GodoxIm30.POWER_STEPS.forEach { step ->
            FilterChip(
                selected = settings.powerDenominator == step.denominator,
                onClick = confirmationClick { settings.powerDenominator = step.denominator },
                label = { Text(step.label) },
            )
        }
    }
}

@Composable
fun ApertureChipRow(settings: GearSettingsState, modifier: Modifier = Modifier) {
    ChipRow(title = stringResource(R.string.wanted_aperture), modifier = modifier) {
        settings.enabledApertures.forEach { stop ->
            FilterChip(
                selected = settings.targetLabel == stop.label,
                onClick = confirmationClick { settings.targetLabel = stop.label },
                label = { Text("f/${stop.label}") },
            )
        }
    }
}

@Composable
private fun ChipRow(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
