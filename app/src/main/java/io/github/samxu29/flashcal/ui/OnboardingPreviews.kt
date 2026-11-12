/**
 * Picture-first onboarding pages.
 *
 * Each page opens with a small replica of the real screen it describes, drawn
 * with the same shapes and colors as the Camera, Manual, and Settings tabs, so it
 * reads like a cropped screenshot but stays sharp and in step with the UI. The
 * camera previews move: the real needle, light ball and falloff badge animate
 * through sample readings, so each one is understood by watching it.
 * Below it, every feature is one short line next to the control it names.
 */
package io.github.samxu29.flashcal.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.Iso
import androidx.compose.material.icons.outlined.Lens
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShutterSpeed
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
import androidx.compose.ui.geometry.Size
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
import io.github.samxu29.flashcal.ar.LightCue
import io.github.samxu29.flashcal.ar.SpotClip
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.GodoxIm30
import io.github.samxu29.flashcal.domain.PowerFit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ScreenGlass = Color.White.copy(alpha = 0.18f)
private val ScreenScene = Brush.verticalGradient(listOf(Color(0xFF4A5866), Color(0xFF242B33), Color(0xFF15191E)))
private const val SAMPLE_ISO = 400
private const val SAMPLE_METERS = 2.5

/** Camera page: the ring settles on a subject, a tap anchors it, and its distance appears. */
@Composable
fun CameraPagePreview() {
    val time by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
    )
    ScreenFrame(Modifier.height(200.dp)) {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp).size(140.dp),
        )
        // Aiming at the center, then a tap on the subject, then tracked on it.
        val tracked = time >= 0.42f
        Canvas(Modifier.matchParentSize()) {
            val subject = Offset(110.dp.toPx(), size.height / 2f - 18.dp.toPx())
            val ring = 22.dp.toPx()
            val shade = Color.Black.copy(alpha = 0.45f)
            if (!tracked) {
                val pulse = 0.6f + 0.4f * sin(time * 40f).coerceAtLeast(0f)
                drawCircle(shade, ring, center, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White.copy(alpha = pulse), ring, center, style = Stroke(1.dp.toPx()))
            } else {
                drawCircle(Color.White.copy(alpha = 0.45f), 4.dp.toPx(), center)
                drawCircle(shade, ring, subject, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, ring, subject, style = Stroke(2.dp.toPx()))
                drawCircle(Color.White, 3.dp.toPx(), subject)
            }
            if (time in 0.35f..0.6f) {
                val t = (time - 0.35f) / 0.25f
                drawCircle(Color.White.copy(alpha = 0.6f * (1f - t)), 8.dp.toPx() + 30.dp.toPx() * t, subject)
            }
        }
        if (tracked) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 82.dp).offset(y = 44.dp)) {
                DistancePill("2.4")
            }
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
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("ISO $SAMPLE_ISO", "GN 15", "f/1.4–16", "0.5–10 m", "50 mm", stringResource(R.string.auto_meter_over), "30s–1/8000")
                    .forEach { SampleChip(it) }
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

/** Controls page: the bottom bar as on the Camera tab, with the hold button tapped on and off. */
@Composable
fun ControlsPagePreview() {
    val press by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
    )
    ScreenFrame(Modifier.height(200.dp)) {
        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassCircle(Icons.Outlined.FlashlightOff)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.38f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircle(Icons.Outlined.Straighten)
            Spacer(Modifier.width(8.dp))
            GlassCircle(Icons.Outlined.RestartAlt)
            Spacer(Modifier.weight(1f))
            // A tap freezes the reading, another releases it.
            if (press > 0.5f) LockCircle() else GlassCircle(Icons.Outlined.LockOpen)
            Spacer(Modifier.weight(1f))
            LetterCircle("A")
            Spacer(Modifier.width(8.dp))
            GlassCircle(Icons.Outlined.BrightnessAuto)
        }
    }
}

/** Scene page: flash power, the ambient needle, the light ball, falloff and frame lines, all moving. */
@Composable
fun ScenePagePreview() {
    val time by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
    )
    ScreenFrame(Modifier.height(240.dp)) {
        Canvas(Modifier.matchParentSize()) {
            // Vertical frame lines, as on the Camera tab.
            val h = size.height * 0.62f
            val w = h * 2f / 3f
            val top = (size.height - h) / 2f + 22.dp.toPx()
            drawRect(Color.White.copy(alpha = 0.7f), Offset((size.width - w) / 2f, top), Size(w, h), style = Stroke(1.dp.toPx()))
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    "1/8",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium),
                )
            }
            ExposureNeedle(over = 1.6 * sin(time.toDouble()), clip = SpotClip.NONE, disabled = false)
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LightBall(LightCue(cos(time), 0.5f, sin(time) * 0.8f, 0.6f))
            FalloffBadge(1.5 + 1.2 * sin(time.toDouble() * 0.5))
        }
        Box(Modifier.align(Alignment.Center).offset(y = 24.dp)) { CenterCross() }
    }
}

@Composable
fun CameraPageRows() {
    FeatureRow(R.string.onboard_camera_aim) { DarkCross() }
    FeatureRow(Icons.Outlined.TouchApp, R.string.onboard_camera_track)
    FeatureRow(R.string.onboard_camera_untrack) { DarkCross() }
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_camera_hold)
    FeatureRow(Icons.Outlined.Straighten, R.string.onboard_camera_manual)
}

@Composable
fun ProfilePageRows() {
    FeatureRow(Icons.Outlined.TouchApp, R.string.onboard_profile_open)
    FeatureRow(Icons.Outlined.Edit, R.string.onboard_profile_edit)
    FeatureRow(Icons.Outlined.Iso, R.string.onboard_profile_iso)
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_profile_gn)
    FeatureRow(Icons.Outlined.Camera, R.string.onboard_profile_aperture)
    FeatureRow(Icons.Outlined.Straighten, R.string.onboard_profile_distance)
    FeatureRow(Icons.Outlined.Lens, R.string.onboard_profile_lens)
    FeatureRow(Icons.Outlined.BrightnessAuto, R.string.onboard_profile_lean)
    FeatureRow(Icons.Outlined.ShutterSpeed, R.string.onboard_profile_shutter)
}

@Composable
fun DialsPageRows() {
    FeatureRow(Icons.Outlined.Swipe, R.string.onboard_dials_try)
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_dials_power)
    FeatureRow(Icons.Outlined.TouchApp, R.string.onboard_dials_set)
}

@Composable
fun ControlsPageRows() {
    FeatureRow(R.string.onboard_controls_fixed) { GlassCircle(Icons.Outlined.Straighten, dark = true) }
    FeatureRow(R.string.onboard_controls_reset) { GlassCircle(Icons.Outlined.RestartAlt, dark = true) }
    FeatureRow(R.string.onboard_controls_hold) { LockCircle(dark = true) }
    FeatureRow(R.string.onboard_controls_priority) { LetterCircle("A", dark = true) }
    FeatureRow(R.string.onboard_controls_auto) { GlassCircle(Icons.Outlined.BrightnessAuto, dark = true) }
    FeatureRow(R.string.onboard_controls_light) { GlassCircle(Icons.Outlined.FlashlightOff, dark = true) }
    FeatureRow(R.string.onboard_controls_frame) { GlassCircle(Icons.Outlined.SwipeDown, dark = true) }
    FeatureRow(R.string.onboard_controls_frame_back) { GlassCircle(Icons.Outlined.SwipeUp, dark = true) }
    FeatureRow(R.string.onboard_controls_warning, slot = null) {
        WarningPill(stringResource(R.string.onboard_warning_sample))
    }
}

@Composable
fun ScenePageRows() {
    FeatureRow(Icons.Outlined.FlashOn, R.string.onboard_scene_power)
    FeatureRow(Icons.Outlined.Exposure, R.string.onboard_scene_needle)
    FeatureRow(R.string.onboard_scene_light) { LightBall(LightCue(-0.6f, 0.6f, 0.5f, 0.6f)) }
    FeatureRow(R.string.onboard_scene_mark) { LightBall(LightCue(0.7f, 0.5f, 0.3f, 0.7f), marked = true) }
    FeatureRow(R.string.onboard_scene_falloff) { DarkChip { FalloffBadge(2.0) } }
    FeatureRow(Icons.Outlined.CropFree, R.string.onboard_scene_frame)
    FeatureRow(Icons.Outlined.ScreenRotation, R.string.onboard_scene_turn)
    FeatureRow(Icons.Outlined.Lens, R.string.onboard_scene_zoom)
}

@Composable
private fun FeatureRow(icon: ImageVector, text: Int, slot: Dp? = 48.dp) {
    FeatureRow(text, slot) {
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
private fun FeatureRow(text: Int, slot: Dp? = 48.dp, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(if (slot != null) Modifier.width(slot) else Modifier, contentAlignment = Alignment.Center) { leading() }
        // One line per feature: the strings are kept short enough to fit.
        Text(
            stringResource(text),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
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

/** The Meter screen's A/S priority button. */
@Composable
private fun LetterCircle(letter: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Box(
        modifier
            .size(if (dark) 44.dp else 40.dp)
            .background(if (dark) Color(0xFF242B33) else ScreenGlass, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
    }
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

/** The meter's lock in its held state, as on the Meter screen. */
@Composable
private fun LockCircle(dark: Boolean = false) {
    Box(
        Modifier
            .size(if (dark) 44.dp else 36.dp)
            .background(if (dark) Color(0xFF242B33) else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (dark) 32.dp else 36.dp)
                .background(Color.White.copy(alpha = 0.92f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}

/** The center cross on a dark disc, readable on the light onboarding page. */
@Composable
private fun DarkCross() {
    Box(Modifier.size(44.dp).background(Color(0xFF242B33), CircleShape), contentAlignment = Alignment.Center) {
        CenterCross()
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
