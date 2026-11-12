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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Camera
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
import io.github.samxu29.flashcal.ar.SharedArCamera
import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.PowerFit
import com.google.ar.core.TrackingFailureReason

internal val HudWhite = Color.White
internal val Glass = Color.White.copy(alpha = 0.18f)
internal val HeldFill = Color.White.copy(alpha = 0.92f)
internal val WarningAmber = Color(0xFFFFE082)
internal val ReadoutShadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 1f), blurRadius = 8f)

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
internal fun CameraFocusOverlay(regions: List<FocusRegion>) {
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
internal fun GlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = HudWhite,
    fill: Color = Glass,
    hold: Boolean? = null,
) {
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
    if (hold != null) {
        Surface(
            shape = CircleShape,
            color = fill,
            contentColor = tint,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .holdToggle(hold, action = onClick)
                .semantics { role = Role.Button },
            content = content,
        )
    } else {
        Surface(
            onClick = confirmationClick(onClick),
            shape = CircleShape,
            color = fill,
            contentColor = tint,
            modifier = modifier.size(48.dp),
            content = content,
        )
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
                        values = distanceLabels,
                        selectedIndex = scale.indexFor(manualMeters),
                        onSelect = { index -> onManualMeters(scale.metersAt(index)) },
                        icon = Icons.Outlined.Straighten,
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
                    icon = Icons.Outlined.Camera,
                )
            }
        }
    }
}
