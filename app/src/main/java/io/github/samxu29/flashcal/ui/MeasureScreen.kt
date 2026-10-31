package io.github.samxu29.flashcal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.ar.ArMeasureView
import io.github.samxu29.flashcal.ar.DistanceSource
import io.github.samxu29.flashcal.ar.FocusRegion
import io.github.samxu29.flashcal.ar.MeasureGate
import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.PowerFit
import com.google.ar.core.TrackingFailureReason

private val HudWhite = Color.White
private val Glass = Color.White.copy(alpha = 0.18f)
private val HeldFill = Color.White.copy(alpha = 0.92f)
private val WarningAmber = Color(0xFFFFE082)
private val ReadoutShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 1f), blurRadius = 8f)

@Composable
fun MeasureScreen(
    settings: GearSettingsState,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
) {
    val gate = remember { MeasureGate() }
    var locked by remember { mutableStateOf(false) }
    gate.locked = locked
    var manual by rememberSaveable { mutableStateOf(false) }
    var manualMeters by rememberSaveable { mutableFloatStateOf(2.5f) }
    var torch by rememberSaveable { mutableStateOf(false) }

    val reading by gate.reading
    val focusRegions by gate.focusRegions
    val failure by gate.failure
    val cameraBlocked by gate.cameraBlocked
    val arBlocked by gate.arBlocked
    val sessionFailed by gate.sessionFailed
    val cameraReady by gate.cameraReady
    val currentOnReady by rememberUpdatedState(onReady)
    LaunchedEffect(cameraReady, cameraBlocked, arBlocked, sessionFailed) {
        if (cameraReady || cameraBlocked || arBlocked || sessionFailed) currentOnReady()
    }
    val showChrome = !cameraBlocked && !arBlocked && !sessionFailed
    val distanceScale = settings.distanceScale
    val fixedMeters = distanceScale.snap(manualMeters)
    val usedMeters = if (manual) fixedMeters else reading?.meters
    val readout = shotReadout(settings, usedMeters)
    LaunchedEffect(settings.activeProfileId, distanceScale) {
        manualMeters = distanceScale.snap(manualMeters)
    }
    LaunchedEffect(manual) {
        if (manual) {
            locked = false
            gate.pauseMeasurement()
        } else {
            gate.resumeMeasurement()
        }
    }

    SystemBarIconStyle(onDarkBackground = true)

    Box(modifier.fillMaxSize()) {
        ArMeasureView(
            gate = gate,
            torch = torch,
            modifier = Modifier.fillMaxSize(),
        )
        if (showChrome) {
            CameraFocusOverlay(focusRegions)
            ArMeasureIndicator(
                meters = reading?.meters,
                unit = distanceScale.unit,
                source = reading?.source,
                locked = locked,
                status = if (!manual && reading?.meters == null) {
                    trackingMessage(failure) ?: stringResource(R.string.measure_searching)
                } else {
                    null
                },
                warning = if (!manual && reading?.meters == null) trackingMessage(failure) else null,
                showReading = !manual,
                onToggleHold = { locked = !locked },
            )
            GlassButton(
                onClick = { torch = !torch },
                icon = if (torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                description = stringResource(if (torch) R.string.torch_off else R.string.torch_on),
                tint = if (torch) Color.Black else HudWhite,
                fill = if (torch) Color.White.copy(alpha = 0.88f) else Glass,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 16.dp),
            )
            if (!manual) {
                GlassButton(
                    onClick = {
                        locked = false
                        gate.resetMeasurement()
                    },
                    icon = Icons.Outlined.Refresh,
                    description = stringResource(R.string.reset_measurement),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 72.dp, end = 16.dp),
                )
            }
        }
        GlassButton(
            onClick = {
                if (!manual) {
                    reading?.meters?.let { meters ->
                        manualMeters = distanceScale.snap(meters)
                    }
                }
                manual = !manual
            },
            icon = Icons.Outlined.Straighten,
            description = stringResource(
                if (manual) R.string.use_camera_distance else R.string.use_fixed_distance,
            ),
            tint = if (manual) Color.Black else HudWhite,
            fill = if (manual) Color.White.copy(alpha = 0.88f) else Glass,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp),
        )
        Readout(
            readout = readout,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        MeasureDials(
            settings = settings,
            manual = manual,
            manualMeters = fixedMeters,
            onManualMeters = { manualMeters = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Flash power from a dialed distance alone; the camera is never opened here. */
@Composable
fun ManualScreen(
    settings: GearSettingsState,
    meters: Float,
    onMeters: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
) {
    val currentOnReady by rememberUpdatedState(onReady)
    LaunchedEffect(Unit) { currentOnReady() }
    val distanceScale = settings.distanceScale
    val fixedMeters = distanceScale.snap(meters)
    LaunchedEffect(settings.activeProfileId, distanceScale) {
        onMeters(distanceScale.snap(meters))
    }

    SystemBarIconStyle(onDarkBackground = true)

    Column(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Readout(shotReadout(settings, fixedMeters))
        }
        MeasureDials(
            settings = settings,
            manual = true,
            manualMeters = fixedMeters,
            onManualMeters = onMeters,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CameraFocusOverlay(regions: List<FocusRegion>) {
    val focusColor = Color(0xFFA5D6A7)
    val description = stringResource(R.string.camera_focus_area)
    Canvas(
        Modifier.fillMaxSize().clipToBounds().semantics { contentDescription = description },
    ) {
        regions.forEach { region ->
            val origin = Offset(region.left * size.width, region.top * size.height)
            val extent = Size(
                (region.right - region.left) * size.width,
                (region.bottom - region.top) * size.height,
            )
            drawRect(Color.Black.copy(alpha = 0.5f), origin, extent, style = Stroke(3.dp.toPx()))
            drawRect(focusColor, origin, extent, style = Stroke(1.dp.toPx()))
        }
    }
}

private data class ShotReadout(
    val label: String,
    val headline: String,
    val caption: String,
    val warning: String?,
)

@Composable
private fun shotReadout(
    settings: GearSettingsState,
    usedMeters: Float?,
): ShotReadout {
    val powerResult = if (usedMeters != null) {
        FlashMath.powerForAperture(
            baseGn = settings.guideNumber,
            iso = settings.iso,
            distanceMeters = usedMeters.toDouble(),
            aperture = settings.targetAperture.value,
        )
    } else {
        null
    }
    return ShotReadout(
        label = stringResource(R.string.flash_power_output),
        headline = powerResult?.step?.label ?: "—",
        caption = stringResource(R.string.profile_aperture, settings.iso, settings.targetAperture.label),
        warning = powerResult
            ?.takeIf { it.fit != PowerFit.MATCH }
            ?.let { powerAdvice(it, settings.targetAperture.label) },
    )
}

@Composable
private fun Readout(
    readout: ShotReadout,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, start = 72.dp, end = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            readout.label,
            color = HudWhite.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelLarge.copy(shadow = ReadoutShadow),
        )
        Text(
            readout.headline,
            color = HudWhite,
            style = MaterialTheme.typography.displaySmall.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Medium,
                shadow = ReadoutShadow,
            ),
            maxLines = 1,
        )
        Text(
            readout.caption,
            color = HudWhite.copy(alpha = 0.92f),
            style = MaterialTheme.typography.bodyLarge.copy(shadow = ReadoutShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        readout.warning?.let { warning ->
            WarningPill(warning, Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
internal fun WarningPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(WarningAmber)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            color = Color.Black,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun GlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = HudWhite,
    fill: Color = Glass,
) {
    Surface(
        onClick = confirmationClick(onClick),
        shape = CircleShape,
        color = fill,
        contentColor = tint,
        modifier = modifier.size(48.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun MeasureDials(
    settings: GearSettingsState,
    manual: Boolean,
    manualMeters: Float,
    onManualMeters: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.38f),
        contentColor = HudWhite,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = manual,
                enter = expandVertically() + fadeIn(tween(180)),
                exit = shrinkVertically() + fadeOut(tween(120)),
            ) {
                val scale = settings.distanceScale
                key(settings.activeProfileId, scale) {
                    val symbol = unitSymbol(scale.unit)
                    val distanceLabels = remember(scale, symbol) {
                        List(scale.size) { index -> "${formatDistanceValue(scale.valueAt(index))} $symbol" }
                    }
                    HorizontalDial(
                        label = stringResource(R.string.manual_distance),
                        caption = distanceText(manualMeters, scale.unit.other),
                        values = distanceLabels,
                        selectedIndex = scale.indexFor(manualMeters),
                        onSelect = { index -> onManualMeters(scale.metersAt(index)) },
                    )
                }
            }
            key(
                settings.activeProfileId,
                settings.apertureStep,
                settings.apertureOptions,
                settings.widestAperture,
                settings.narrowestAperture,
                settings.customAperture,
            ) {
                val apertures = settings.enabledApertures
                HorizontalDial(
                    label = stringResource(R.string.wanted_aperture),
                    values = apertures.map { "f/${it.label}" },
                    selectedIndex = apertures.indexOfFirst { it.label == settings.targetAperture.label },
                    onSelect = { index -> settings.targetLabel = apertures[index].label },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ArMeasureIndicator(
    meters: Float?,
    unit: DistanceUnit,
    source: DistanceSource?,
    locked: Boolean,
    status: String?,
    warning: String?,
    showReading: Boolean,
    onToggleHold: () -> Unit,
) {
    val searching = showReading && meters == null
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    val markColor = if (showReading && source == DistanceSource.FEATURE && !locked) Color(0xFFFFE082) else HudWhite
    val description = if (!showReading) {
        stringResource(R.string.center_mark)
    } else if (meters != null) {
        stringResource(
            R.string.distance_secondary,
            distanceText(meters, unit.other),
            distanceCaption(source, locked),
        ).let { caption ->
            stringResource(R.string.measure_reading, distanceText(meters, unit), caption)
        }
    } else {
        status ?: stringResource(R.string.center_mark)
    }
    Canvas(
        Modifier
            .align(Alignment.Center)
            .size(36.dp)
            .semantics { contentDescription = description },
    ) {
        val stroke = 1.dp.toPx()
        val radius = 14.dp.toPx()
        val split = 5.dp.toPx()
        val center = this.center
        val ink = markColor.copy(alpha = if (searching) pulse else markColor.alpha)
        // Centered distance aiming guide; the camera's AF regions are shown separately.
        drawCircle(Color.Black.copy(alpha = 0.45f), radius, style = Stroke(stroke + 2.dp.toPx()))
        drawCircle(ink, radius, style = Stroke(stroke))
        drawLine(
            Color.Black.copy(alpha = 0.45f),
            Offset(center.x - split, center.y),
            Offset(center.x + split, center.y),
            stroke + 2.dp.toPx(),
        )
        drawLine(ink, Offset(center.x - split, center.y), Offset(center.x + split, center.y), stroke)
        drawLine(
            Color.Black.copy(alpha = 0.45f),
            Offset(center.x, center.y - split),
            Offset(center.x, center.y + split),
            stroke + 2.dp.toPx(),
        )
        drawLine(ink, Offset(center.x, center.y - split), Offset(center.x, center.y + split), stroke)
    }
    if (!showReading) return
    val holdAction = stringResource(if (locked) R.string.release_distance else R.string.hold_distance)
    Column(
        Modifier
            .align(Alignment.Center)
            .offset(y = 72.dp)
            .widthIn(max = 260.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (meters != null) {
            Text(
                text = distanceText(meters, unit),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (locked) HeldFill else Glass)
                    .clickable(onClick = confirmationClick(onToggleHold))
                    .semantics {
                        role = Role.Button
                        contentDescription = "$holdAction. $description"
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (locked) Color.Black else HudWhite,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = if (locked) FontWeight.SemiBold else FontWeight.Medium,
                    shadow = if (locked) null else ReadoutShadow,
                ),
            )
            Text(
                text = stringResource(
                    R.string.distance_secondary,
                    distanceText(meters, unit.other),
                    distanceCaption(source, locked),
                ),
                color = HudWhite.copy(alpha = if (locked) 1f else 0.92f),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (locked) FontWeight.SemiBold else null,
                    shadow = ReadoutShadow,
                ),
                textAlign = TextAlign.Center,
            )
        } else if (warning != null) {
            WarningPill(warning)
        } else if (status != null) {
            Text(
                text = status,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .graphicsLayer { alpha = pulse }
                    .clip(RoundedCornerShape(12.dp))
                    .background(Glass)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = HudWhite,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    shadow = ReadoutShadow,
                ),
            )
        }
    }
}

@Composable
private fun distanceCaption(source: DistanceSource?, locked: Boolean): String {
    if (locked) return stringResource(R.string.source_held)
    return when (source) {
        DistanceSource.DEPTH -> stringResource(R.string.source_depth)
        DistanceSource.PLANE -> stringResource(R.string.source_plane)
        DistanceSource.FEATURE -> stringResource(R.string.source_feature)
        null -> ""
    }
}

@Composable
private fun trackingMessage(reason: TrackingFailureReason?): String? = when (reason) {
    null, TrackingFailureReason.NONE -> null
    TrackingFailureReason.INSUFFICIENT_LIGHT -> stringResource(R.string.track_dark)
    TrackingFailureReason.EXCESSIVE_MOTION -> stringResource(R.string.track_motion)
    TrackingFailureReason.INSUFFICIENT_FEATURES -> stringResource(R.string.track_features)
    TrackingFailureReason.CAMERA_UNAVAILABLE -> stringResource(R.string.track_camera)
    else -> stringResource(R.string.track_lost)
}
