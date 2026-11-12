/**
 * Profile editor sections.
 *
 * Each setting group is a card with an icon and a short title. Instead of
 * explaining the result in a sentence, a section shows a small dark strip
 * styled like the Measure dial with the marks it will contain, so the effect
 * of a change is visible right away. Hints and errors stay to a few words.
 */
package io.github.samxu29.flashcal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Iso
import androidx.compose.material.icons.outlined.Lens
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.data.LensKind
import io.github.samxu29.flashcal.data.MAX_FOCAL
import io.github.samxu29.flashcal.data.MIN_FOCAL
import io.github.samxu29.flashcal.domain.ApertureStep
import io.github.samxu29.flashcal.domain.ApertureStop
import io.github.samxu29.flashcal.domain.DistanceScale
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.GodoxIm30
import io.github.samxu29.flashcal.domain.LightMath
import io.github.samxu29.flashcal.ar.ViewField
import io.github.samxu29.flashcal.meter.widestFittingFocal
import io.github.samxu29.flashcal.meter.widestPreviewFocal
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.roundToInt

private val DialStrip = Color(0xFF1B2026)

@Composable
fun SettingsSection(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun UnitSettings(settings: GearSettingsState) {
    SettingsSection(Icons.Outlined.SquareFoot, stringResource(R.string.distance_unit)) {
        val units = listOf(DistanceUnit.METERS to R.string.unit_meters_long, DistanceUnit.FEET to R.string.unit_feet_long)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            units.forEachIndexed { index, (unit, label) ->
                SegmentedButton(
                    selected = settings.distanceUnit == unit,
                    onClick = confirmationClick { settings.setDistanceUnit(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index, units.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
fun FlashSettings(settings: GearSettingsState) {
    val unit = settings.distanceUnit
    val symbol = unitSymbol(unit)
    SettingsSection(Icons.Outlined.FlashOn, stringResource(R.string.section_flash)) {
        OutlinedTextField(
            value = settings.guideNumberText,
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '.' }
                if (cleaned.count { it == '.' } <= 1) settings.guideNumberText = cleaned
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.gn_label)) },
            suffix = { Text(symbol) },
            supportingText = {
                Text(
                    if (settings.guideNumberValid) {
                        stringResource(R.string.gn_support)
                    } else {
                        stringResource(
                            R.string.gn_error,
                            formatGuideNumber(GodoxIm30.MIN_GUIDE_NUMBER / unit.metersPerUnit),
                            "${formatGuideNumber(GodoxIm30.MAX_GUIDE_NUMBER / unit.metersPerUnit)} $symbol",
                        )
                    },
                )
            },
            isError = !settings.guideNumberValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

@Composable
fun FilmSettings(settings: GearSettingsState) {
    SettingsSection(Icons.Outlined.Iso, stringResource(R.string.film_iso)) {
        IsoChipRow(settings, showTitle = false)
    }
}

@Composable
fun ApertureRangeSettings(settings: GearSettingsState) {
    val scale = FlashMath.scale(settings.apertureStep)
    SettingsSection(Icons.Outlined.Camera, stringResource(R.string.aperture_range)) {
        SettingMenu(
            label = stringResource(R.string.aperture_increment),
            value = stringResource(stepLabel(settings.apertureStep)),
            options = ApertureStep.entries.map { stringResource(stepLabel(it)) },
            onSelect = { index -> settings.setApertureStep(ApertureStep.entries[index]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ApertureEndpointField(
                label = stringResource(R.string.aperture_widest),
                value = settings.widestAperture,
                other = settings.narrowestAperture,
                widest = true,
                options = scale.filter { it.value <= settings.narrowestAperture + 1e-9 },
                onCommit = settings::setWidestAperture,
                profileId = settings.activeProfileId,
                modifier = Modifier.weight(1f),
            )
            ApertureEndpointField(
                label = stringResource(R.string.aperture_narrowest),
                value = settings.narrowestAperture,
                other = settings.widestAperture,
                widest = false,
                options = scale.filter { it.value + 1e-9 >= settings.widestAperture },
                onCommit = settings::setNarrowestAperture,
                profileId = settings.activeProfileId,
                modifier = Modifier.weight(1f),
            )
        }
        Hint(stringResource(R.string.aperture_type_help), Icons.Outlined.Edit)
        val stops = settings.enabledApertures
        DialPreview(stops.size) { index -> "f/${stops[index].label}" }
    }
}

@Composable
fun ShutterRangeSettings(settings: GearSettingsState) {
    val scale = LightMath.shutterScale(settings.shutterStep)
    val slowest = scale.filter { it.seconds + 1e-9 >= settings.fastestShutter }
    val fastest = scale.filter { it.seconds <= settings.slowestShutter + 1e-9 }
    SettingsSection(Icons.Outlined.ShutterSpeed, stringResource(R.string.shutter_range)) {
        SettingMenu(
            label = stringResource(R.string.aperture_increment),
            value = stringResource(stepLabel(settings.shutterStep)),
            options = ApertureStep.entries.map { stringResource(stepLabel(it)) },
            onSelect = { index -> settings.setShutterStep(ApertureStep.entries[index]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingMenu(
                label = stringResource(R.string.shutter_slowest),
                value = slowest.firstOrNull { LightMath.sameShutter(it.seconds, settings.slowestShutter) }?.label
                    ?: slowest.first().label,
                options = slowest.map { it.label },
                onSelect = { index -> settings.setSlowestShutter(slowest[index].seconds) },
                modifier = Modifier.weight(1f),
            )
            SettingMenu(
                label = stringResource(R.string.shutter_fastest),
                value = fastest.firstOrNull { LightMath.sameShutter(it.seconds, settings.fastestShutter) }?.label
                    ?: fastest.last().label,
                options = fastest.map { it.label },
                onSelect = { index -> settings.setFastestShutter(fastest[index].seconds) },
                modifier = Modifier.weight(1f),
            )
        }
        val marks = settings.enabledShutters
        DialPreview(marks.size) { index -> marks[index].label }
    }
}

/**
 * The camera lens as one focal length on 35 mm film; on a zoom, the one being
 * shot at. The Camera tab draws vertical frame lines for that angle of view.
 * A disclaimer always shows that they are approximate, and a yellow warning
 * appears when the widest setting's frame does not fit the preview. That
 * uses the preview's real field once the Camera tab has run ([previewField]),
 * and an estimate from the camera's sensor before then.
 */
@Composable
fun LensSettings(settings: GearSettingsState, previewField: ViewField? = null) {
    val context = LocalContext.current
    val widest = remember(context, previewField) {
        previewField?.let { widestFittingFocal(it.tanHalfWidth.toDouble(), it.tanHalfHeight.toDouble()) }
            ?: context.resources.displayMetrics.let { widestPreviewFocal(context, it.widthPixels, it.heightPixels) }
    }
    SettingsSection(Icons.Outlined.Lens, stringResource(R.string.lens_section)) {
        FocalField(
            saved = settings.focalShort,
            label = stringResource(R.string.lens_focal),
            key = "${settings.activeProfileId}-focal",
            onCommit = { mm ->
                // One focal length now; a profile saved as a zoom keeps its widest until changed.
                settings.selectLensKind(LensKind.PRIME)
                settings.setFocalShort(mm)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.lens_angle, angleOfView(settings.focalShort)))
        Hint(stringResource(R.string.lens_zoom_hint))
        if (widest != null && settings.focalShort < widest) {
            LensWarning(stringResource(R.string.lens_too_wide, ceil(widest).toInt()))
        }
        Hint(stringResource(R.string.lens_disclaimer), Icons.Outlined.Info)
    }
}

@Composable
private fun LensWarning(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(WarningAmber, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
        Text(text, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Horizontal × vertical angle of view on a 36 × 24 mm frame, in whole degrees. */
private fun angleOfView(focalMm: Int): String {
    fun degrees(halfMm: Double) = Math.toDegrees(2.0 * atan(halfMm / focalMm)).roundToInt()
    return "${degrees(18.0)}° × ${degrees(12.0)}°"
}

@Composable
private fun FocalField(
    saved: Int,
    label: String,
    key: String,
    onCommit: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(key) { mutableStateOf(saved.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(saved, focused) {
        if (!focused) draft = saved.toString()
    }
    val parsed = draft.toIntOrNull()
    val error = when {
        draft.isEmpty() -> null
        parsed == null || parsed !in MIN_FOCAL..MAX_FOCAL -> stringResource(R.string.lens_focal_error, MIN_FOCAL, MAX_FOCAL)
        else -> null
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() }.take(4)
            draft = cleaned
            cleaned.toIntOrNull()?.let { if (it in MIN_FOCAL..MAX_FOCAL) onCommit(it) }
        },
        label = { Text(label) },
        suffix = { Text("mm") },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
fun AutoMeterSettings(settings: GearSettingsState) {
    SettingsSection(Icons.Outlined.Exposure, stringResource(R.string.auto_meter)) {
        val choices = listOf(true to R.string.auto_meter_over, false to R.string.auto_meter_under)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (over, label) ->
                SegmentedButton(
                    selected = settings.preferOver == over,
                    onClick = confirmationClick { settings.preferOver = over },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                ) {
                    Text(stringResource(label))
                }
            }
        }
        Hint(stringResource(R.string.auto_meter_hint))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApertureEndpointField(
    label: String,
    value: Double,
    other: Double,
    widest: Boolean,
    options: List<ApertureStop>,
    onCommit: (Double) -> Boolean,
    profileId: String,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(profileId, label) {
        mutableStateOf(FlashMath.formatApertureLabel(value))
    }
    var expanded by rememberSaveable(profileId, label) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) draft = FlashMath.formatApertureLabel(value)
    }
    val incomplete = draft.endsWith('.')
    val parsed = FlashMath.parseAperture(draft)
    val outOfOrder = parsed != null && !incomplete &&
        if (widest) parsed > other + 1e-9 else parsed + 1e-9 < other
    val invalid = draft.isNotEmpty() && !incomplete && (parsed == null || outOfOrder)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { raw ->
                val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
                val decimals = cleaned.substringAfter('.', "").length
                if (cleaned.count { it == '.' } <= 1 && decimals <= 2 && cleaned.length <= 6) {
                    draft = cleaned
                    expanded = true
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    val wasFocused = focused
                    focused = focus.isFocused
                    if (wasFocused && !focus.isFocused) {
                        val typed = FlashMath.parseAperture(draft)
                        if (typed != null && onCommit(typed)) {
                            draft = FlashMath.formatApertureLabel(typed)
                        } else if (typed == null || !FlashMath.sameAperture(typed, value)) {
                            draft = FlashMath.formatApertureLabel(value)
                        }
                    }
                },
            readOnly = false,
            singleLine = true,
            label = { Text(label) },
            prefix = { Text("f/") },
            isError = invalid,
            supportingText = if (invalid) {
                {
                    Text(
                        stringResource(
                            if (parsed == null) R.string.aperture_value_error else R.string.aperture_order_error,
                        ),
                    )
                }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { stop ->
                DropdownMenuItem(
                    text = { Text("f/${stop.label}") },
                    onClick = confirmationClick {
                        expanded = false
                        draft = stop.label
                        onCommit(stop.value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private fun stepLabel(step: ApertureStep): Int = when (step) {
    ApertureStep.FULL -> R.string.aperture_step_full
    ApertureStep.HALF -> R.string.aperture_step_half
    ApertureStep.THIRD -> R.string.aperture_step_third
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingMenu(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = confirmationClick {
                        expanded = false
                        onSelect(index)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun DistanceRangeSettings(settings: GearSettingsState) {
    val saved = settings.distanceScale
    val unit = saved.unit
    val symbol = unitSymbol(unit)
    var minimum by rememberSaveable(settings.activeProfileId, unit) {
        mutableStateOf(formatDistanceValue(saved.minimum / 100f))
    }
    var maximum by rememberSaveable(settings.activeProfileId, unit) {
        mutableStateOf(formatDistanceValue(saved.maximum / 100f))
    }
    var increment by rememberSaveable(settings.activeProfileId, unit) {
        mutableStateOf(formatDistanceValue(saved.increment / 100f))
    }
    val minValue = DistanceScale.hundredths(minimum, unit)
    val maxValue = DistanceScale.hundredths(maximum, unit)
    val scale = DistanceScale.fromText(minimum, maximum, increment, unit)
    val rangeError = stringResource(R.string.distance_value_error, "0.01", "${unit.maxValue} $symbol")

    fun saveValidScale() {
        DistanceScale.fromText(minimum, maximum, increment, unit)?.let { settings.distanceScale = it }
    }

    SettingsSection(Icons.Outlined.Straighten, stringResource(R.string.distance_range)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DistanceField(
                value = minimum,
                label = stringResource(R.string.distance_minimum),
                symbol = symbol,
                onValueChange = { minimum = it; saveValidScale() },
                error = if (minValue == null) rangeError else null,
                modifier = Modifier.weight(1f),
            )
            DistanceField(
                value = maximum,
                label = stringResource(R.string.distance_maximum),
                symbol = symbol,
                onValueChange = { maximum = it; saveValidScale() },
                error = when {
                    maxValue == null -> rangeError
                    minValue != null && maxValue < minValue -> stringResource(R.string.distance_order_error)
                    else -> null
                },
                modifier = Modifier.weight(1f),
            )
        }
        DistanceField(
            value = increment,
            label = stringResource(R.string.distance_increment),
            symbol = symbol,
            onValueChange = { increment = it; saveValidScale() },
            error = if (DistanceScale.hundredths(increment, unit) == null) rangeError else null,
            modifier = Modifier.fillMaxWidth(),
        )
        val shown = scale ?: saved
        val labels = previewIndices(shown.size).filterNotNull().associateWith { dialText(shown.valueAt(it), unit) }
        DialPreview(shown.size) { index -> labels[index].orEmpty() }
    }
}

@Composable
private fun DistanceField(
    value: String,
    label: String,
    symbol: String,
    onValueChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }.take(12)
            if (cleaned.count { it == '.' } <= 1) onValueChange(cleaned)
        },
        label = { Text(label) },
        suffix = { Text(symbol) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
private fun Hint(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val PREVIEW_SLOTS = 6

/** First three and last two marks with a gap between, once the dial is too long to show whole. */
private fun previewIndices(count: Int): List<Int?> =
    if (count <= PREVIEW_SLOTS) List(count) { it } else listOf(0, 1, 2, null, count - 2, count - 1)

/** A strip styled like the Measure dial, listing what the dial will hold. */
@Composable
private fun DialPreview(count: Int, label: (Int) -> String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = DialStrip,
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dial_preview_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    pluralStringResource(R.plurals.dial_marks, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                previewIndices(count).forEach { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier
                                .size(width = 1.dp, height = 6.dp)
                                .background(Color.White.copy(alpha = if (index == null) 0f else 0.6f)),
                        )
                        Text(
                            if (index == null) "…" else label(index),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
