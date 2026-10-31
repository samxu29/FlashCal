/**
 * Picture-first onboarding pages.
 *
 * Each page opens with a small replica of the real screen it describes, drawn
 * with the same shapes and colors as Measure, Manual, and Settings, so it
 * reads like a cropped screenshot but stays sharp and in step with the UI.
 * Below it, every feature is one short line next to the control it names.
 */
package io.github.samxu29.flashcal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.Iso
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.GodoxIm30
import io.github.samxu29.flashcal.domain.PowerFit

private val ScreenGlass = Color.White.copy(alpha = 0.18f)
private val ScreenScene = Brush.verticalGradient(listOf(Color(0xFF4A5866), Color(0xFF242B33), Color(0xFF15191E)))
private const val SAMPLE_ISO = 400
private const val SAMPLE_METERS = 2.5

/** Camera page: the Measure view aimed at a subject, with a live distance under the cross. */
@Composable
fun CameraPagePreview() {
    ScreenFrame(Modifier.height(200.dp)) {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp).size(140.dp),
        )
        CenterCross(Modifier.align(Alignment.CenterStart).padding(start = 92.dp))
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DistancePill("2.4")
            Text(
                stringResource(R.string.distance_secondary, distanceText(2.4f, DistanceUnit.FEET), stringResource(R.string.source_depth)),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Profile page: the active profile row as it appears in Settings, with what it holds. */
@Composable
fun ProfilePagePreview() {
    Surface(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboard_profile_sample), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.profile_line, SAMPLE_ISO, formatGuideNumber(GodoxIm30.GUIDE_NUMBER_METERS), unitSymbol(DistanceUnit.METERS)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("ISO $SAMPLE_ISO", "GN 15", "f/1.4–16", "0.5–10 m").forEach { SampleChip(it) }
            }
        }
    }
}

/** Dials page: a working aperture dial that recalculates the flash power as it turns. */
@Composable
fun DialsPagePreview() {
    val stops = remember { FlashMath.FULL_STOPS.filter { it.value in 1.4..16.0 } }
    var index by rememberSaveable { mutableIntStateOf(stops.indexOfFirst { it.label == "4" }) }
    val stop = stops[index]
    val power = FlashMath.powerForAperture(GodoxIm30.GUIDE_NUMBER_METERS, SAMPLE_ISO, SAMPLE_METERS, stop.value)
    ScreenFrame {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.flash_power_output),
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    power?.step?.label ?: "—",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    stringResource(R.string.profile_aperture, SAMPLE_ISO, stop.label) + " · ${SAMPLE_METERS} m",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (power != null && power.fit != PowerFit.MATCH) {
                WarningPill(powerAdvice(power, stop.label))
            }
            HorizontalDial(
                label = stringResource(R.string.wanted_aperture),
                values = stops.map { "f/${it.label}" },
                selectedIndex = index,
                onSelect = { index = it },
            )
        }
    }
}

/** Controls page: the Measure screen corners and center, where each control lives. */
@Composable
fun ControlsPagePreview() {
    ScreenFrame(Modifier.height(200.dp)) {
        GlassCircle(Icons.Outlined.Straighten, Modifier.align(Alignment.TopStart).padding(12.dp))
        GlassCircle(Icons.Outlined.FlashlightOff, Modifier.align(Alignment.TopEnd).padding(12.dp))
        CenterCross(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.Center).offset(y = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DistancePill("2.4", held = true)
            Text(
                stringResource(R.string.distance_secondary, distanceText(2.4f, DistanceUnit.FEET), stringResource(R.string.source_held)),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun CameraPageRows() {
    FeatureRow(Icons.Outlined.PhotoCamera, R.string.onboard_camera_aim)
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_camera_hold)
    FeatureRow(Icons.Outlined.Straighten, R.string.onboard_camera_manual)
}

@Composable
fun ProfilePageRows() {
    FeatureRow(Icons.Outlined.Settings, R.string.onboard_profile_open)
    FeatureRow(Icons.Outlined.Iso, R.string.onboard_profile_iso)
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_profile_gn)
    FeatureRow(Icons.Outlined.Camera, R.string.onboard_profile_aperture)
    FeatureRow(Icons.Outlined.Straighten, R.string.onboard_profile_distance)
}

@Composable
fun DialsPageRows() {
    FeatureRow(Icons.Outlined.Swipe, R.string.onboard_dials_try)
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_dials_power)
    FeatureRow(Icons.Outlined.TouchApp, R.string.onboard_dials_set)
}

@Composable
fun ControlsPageRows() {
    val slot = 132.dp
    FeatureRow(R.string.onboard_controls_fixed, slot) { GlassCircle(Icons.Outlined.Straighten, dark = true) }
    FeatureRow(R.string.onboard_controls_hold, slot) { DarkChip { DistancePill("2.4", compact = true, held = true) } }
    FeatureRow(R.string.onboard_controls_light, slot) { GlassCircle(Icons.Outlined.FlashlightOff, dark = true) }
    FeatureRow(R.string.onboard_controls_warning, slot) {
        WarningPill(stringResource(R.string.onboard_warning_sample))
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: Int) {
    FeatureRow(text) {
        Box(
            Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FeatureRow(text: Int, slot: Dp = 44.dp, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(slot), contentAlignment = Alignment.Center) { leading() }
        Text(stringResource(text), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ScreenFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ScreenScene),
        content = content,
    )
}

@Composable
private fun CenterCross(modifier: Modifier = Modifier) {
    Canvas(modifier.size(36.dp)) {
        val stroke = 1.dp.toPx()
        val radius = 14.dp.toPx()
        val split = 5.dp.toPx()
        val shade = Color.Black.copy(alpha = 0.45f)
        drawCircle(shade, radius, style = Stroke(stroke + 2.dp.toPx()))
        drawCircle(Color.White, radius, style = Stroke(stroke))
        drawLine(shade, Offset(center.x - split, center.y), Offset(center.x + split, center.y), stroke + 2.dp.toPx())
        drawLine(Color.White, Offset(center.x - split, center.y), Offset(center.x + split, center.y), stroke)
        drawLine(shade, Offset(center.x, center.y - split), Offset(center.x, center.y + split), stroke + 2.dp.toPx())
        drawLine(Color.White, Offset(center.x, center.y - split), Offset(center.x, center.y + split), stroke)
    }
}

@Composable
private fun DistancePill(meters: String, compact: Boolean = false, held: Boolean = false) {
    Text(
        stringResource(R.string.distance_with_unit, meters, unitSymbol(DistanceUnit.METERS)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (held) Color.White.copy(alpha = 0.92f) else ScreenGlass)
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
        color = if (held) Color.Black else Color.White,
        style = (if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
            .copy(fontFeatureSettings = "tnum", fontWeight = if (held) FontWeight.SemiBold else FontWeight.Medium),
    )
}

@Composable
private fun GlassCircle(icon: ImageVector, modifier: Modifier = Modifier, dark: Boolean = false) {
    Box(
        modifier
            .size(if (dark) 44.dp else 40.dp)
            .background(if (dark) Color(0xFF242B33) else ScreenGlass, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DarkChip(content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF242B33))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SampleChip(text: String) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
    )
}
