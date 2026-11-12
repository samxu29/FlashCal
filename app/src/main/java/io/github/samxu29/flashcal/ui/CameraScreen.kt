/**
 * The one camera tab: flash power from the subject distance and ambient
 * exposure from a spot reading, both off the same ARCore session.
 *
 * Aim the reticle at the subject, or tap the subject to anchor it; the
 * distance and spot reading then stay on it while the phone moves. A tap on
 * the marker or on the center cross goes back to the cross. Frame lines show the profile lens on 35 mm film;
 * pinch to change focal length on a zoom.
 *
 * Top: flash power, and the ambient exposure as a meter needle under it.
 * Top left: where the main light comes from, drawn as a lit ball, and how
 * much darker the background falls under flash. Top right: phone light.
 * Bottom, in thumb reach: fixed distance and restart, hold (a plain tap) in
 * the middle, auto priority and auto, then the dials.
 *
 * The screen stays in portrait. Held sideways, the round buttons and small
 * badges turn in place to stay upright, as in a camera app. The frame lines
 * are always a vertical shot on the screen; turn the phone for a horizontal one.
 *
 * Pulling the dial panel down (full frame) hides everything but the preview
 * and the frame lines; a swipe up, a tap or Back brings it all back. The tab
 * bar stays, and the choice is kept while other tabs are showing.
 */
package io.github.samxu29.flashcal.ui

import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.ar.core.TrackingFailureReason
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.ar.DistanceSource
import io.github.samxu29.flashcal.ar.LightCue
import io.github.samxu29.flashcal.ar.MeasureGate
import io.github.samxu29.flashcal.ar.SubjectMark
import io.github.samxu29.flashcal.ar.SharedArCamera
import io.github.samxu29.flashcal.ar.SpotClip
import io.github.samxu29.flashcal.ar.ViewField
import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.LightMath
import io.github.samxu29.flashcal.domain.PowerFit
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Reticle and spot meter radius. */
private val SpotRadius = 22.dp

@Composable
fun CameraScreen(
    settings: GearSettingsState,
    camera: SharedArCamera,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    fullFrame: Boolean = false,
    onFullFrame: (Boolean) -> Unit = {},
    manualMeters: Float = 2.5f,
    onManualMeters: (Float) -> Unit = {},
) {
    val gate = camera.gate
    val meter = camera.meter
    var locked by remember { mutableStateOf(false) }
    gate.locked = locked
    var manual by rememberSaveable { mutableStateOf(false) }
    var heldEv by remember { mutableStateOf<Double?>(null) }
    val torch = camera.torch

    val reading by gate.reading
    val failure by gate.failure
    val cameraBlocked by gate.cameraBlocked
    val arBlocked by gate.arBlocked
    val sessionFailed by gate.sessionFailed
    val cameraReady by gate.cameraReady
    val viewField by gate.viewField
    val meterReading by meter.reading
    val clip by meter.clip
    val meterUnavailable by meter.unavailable

    val currentOnReady by rememberUpdatedState(onReady)
    LaunchedEffect(cameraReady, cameraBlocked, arBlocked, sessionFailed) {
        if (cameraReady || cameraBlocked || arBlocked || sessionFailed) currentOnReady()
    }
    DisposableEffect(gate) {
        gate.visible = true
        onDispose { gate.visible = false }
    }
    val spotRadiusPx = with(LocalDensity.current) { SpotRadius.toPx() }
    gate.spotRadiusPx = spotRadiusPx
    LaunchedEffect(manual) {
        if (manual) gate.pauseMeasurement() else gate.resumeMeasurement()
    }

    val view = LocalView.current
    var frozen by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(locked) {
        frozen = if (locked) view.copySurfaceFrame()?.asImageBitmap() else null
    }

    // Distance and flash power.
    val distanceScale = settings.distanceScale
    val fixedMeters = distanceScale.snap(manualMeters)
    LaunchedEffect(settings.activeProfileId, distanceScale) {
        onManualMeters(distanceScale.snap(manualMeters))
    }
    val usedMeters = if (manual) fixedMeters else reading?.meters
    val aperture = settings.targetAperture
    val power = usedMeters?.let {
        FlashMath.powerForAperture(settings.guideNumber, settings.iso, it.toDouble(), aperture.value)
    }

    // Ambient exposure. The phone light would brighten the scene, so it is not metered then.
    val liveEv = if (torch) null else meterReading?.ev100
    val ev = if (locked) heldEv else liveEv
    val shutterSpeeds = settings.enabledShutters
    var shutterSeconds by rememberSaveable { mutableDoubleStateOf(1.0 / 125.0) }
    val shutterIndex = LightMath.indexNearest(shutterSpeeds, shutterSeconds)
    val shutter = shutterSpeeds.getOrNull(shutterIndex)
    val over = ev?.let { scene -> shutter?.let { LightMath.overexposure(scene, aperture.value, it.seconds, settings.iso) } }
    var spinCount by remember { mutableIntStateOf(0) }
    var shutterSpin by remember { mutableStateOf<DialSpin?>(null) }
    var apertureSpin by remember { mutableStateOf<DialSpin?>(null) }
    val autoMeter = {
        val apertures = settings.enabledApertures
        val pick = ev?.let {
            LightMath.autoExposure(
                sceneEv = it,
                iso = settings.iso,
                apertures = apertures.map { stop -> stop.value },
                shutters = shutterSpeeds.map { speed -> speed.seconds },
                currentAperture = aperture.value,
                currentShutter = shutter?.seconds ?: shutterSeconds,
                preferOver = settings.preferOver,
                shutterPriority = settings.shutterPriority,
            )
        }
        if (pick != null) {
            spinCount++
            apertureSpin = DialSpin(pick.first, spinCount)
            shutterSpin = DialSpin(pick.second, spinCount)
        }
    }


    // Lens frame lines.
    // The frame lines follow the profile's lens; on a zoom, its focal length is set there.
    val focalShown = settings.focalShort

    val showCamera = !cameraBlocked && !arBlocked && !sessionFailed
    // How the phone is held: 90 with its left side up, 270 with its right side up.
    val held = rememberHeldQuarter()
    val turn by animateFloatAsState(
        when (held) {
            90 -> -90f
            270 -> 90f
            else -> 0f
        },
        tween(300),
        label = "buttonTurn",
    )
    val upright = Modifier.graphicsLayer { rotationZ = turn }

    // Where the controls sit, so the frame lines can stop short of them.
    val chrome = remember { mutableStateMapOf<String, Rect>() }
    fun Modifier.chrome(key: String) = onGloballyPositioned {
        val bounds = it.boundsInParent()
        if (chrome[key] != bounds) chrome[key] = bounds
    }
    val subjectDescription = stringResource(R.string.subject_marker)

    SystemBarIconStyle(onDarkBackground = true)
    BackHandler(enabled = fullFrame) { onFullFrame(false) }
    val showDescription = stringResource(R.string.full_frame_off)

    Box(
        modifier
            .fillMaxSize()
            .semantics {
                if (fullFrame) {
                    customActions = listOf(CustomAccessibilityAction(showDescription) { onFullFrame(false); true })
                }
            }
            // One handler for every gesture on the preview, so they never fight:
            // one finger taps (subject, cross, or back from full frame) or, in full
            // frame, swipes up; two fingers do nothing. Controls on top consume
            // their own touches first.
            .pointerInput(showCamera, fullFrame) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    var multiTouch = false
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            if (!multiTouch && !moved) {
                                when {
                                    fullFrame -> onFullFrame(false)
                                    // Taps on a control's background are not taps on the preview.
                                    chrome.values.any { it.contains(down.position) } -> Unit
                                    showCamera -> {
                                        val tap = down.position
                                        val mark = gate.subject.value
                                        val reach = 48.dp.toPx()
                                        val onCross = hypot(tap.x - size.width / 2f, tap.y - size.height / 2f) < reach
                                        val onMark = mark != null && hypot(tap.x - mark.x, tap.y - mark.y) < reach
                                        // The cross or the marker: back to the center cross.
                                        if (onCross || onMark) gate.clearSubject() else gate.placeSubject(tap.x, tap.y)
                                    }
                                }
                            }
                            break
                        }
                        if (pressed.size >= 2) {
                            multiTouch = true
                        } else if (!multiTouch) {
                            val change = pressed.first()
                            val travel = change.position - down.position
                            if (travel.getDistance() > slop) moved = true
                            if (fullFrame && travel.y < -PULL_DP.dp.toPx()) {
                                change.consume()
                                onFullFrame(false)
                                break
                            }
                        }
                    }
                }
            },
    ) {
        frozen?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showCamera) {
            viewField?.let { FrameLines(it, focalShown, avoid = { if (fullFrame) emptyList() else chrome.values }) }
        }
        if (showCamera && !fullFrame) {
            CameraFocusOverlay(gate.focusRegions.value)
            Aim(
                subjectState = gate.subject,
                searching = !manual && reading == null,
                estimate = !manual && reading?.source == DistanceSource.FEATURE,
                meters = if (manual) null else reading?.meters,
                unitLabel = { distanceText(it, distanceScale.unit) },
                locked = locked,
                warning = if (!manual && reading == null) trackingMessage(failure) else null,
                subjectDescription = subjectDescription,
                labelModifier = upright,
                turn = { turn },
            )
        }

        // Everything but the preview and frame lines. Pulling the dial panel down
        // hides it (full frame); a swipe up or a tap on the preview brings it back.
        AnimatedVisibility(
            visible = !fullFrame,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top center: flash power over the ambient needle.
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 10.dp, start = 76.dp, end = 76.dp)
                        .chrome("readout"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val powerDescription = stringResource(R.string.flash_power_output)
                    Row(
                        Modifier.semantics { contentDescription = "$powerDescription ${power?.step?.label ?: ""}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Outlined.FlashOn, contentDescription = null, tint = HudWhite, modifier = Modifier.size(26.dp))
                        Text(
                            power?.step?.label ?: "—",
                            color = HudWhite,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.Medium,
                                shadow = ReadoutShadow,
                            ),
                            maxLines = 1,
                        )
                    }
                    ExposureNeedle(over = over, clip = if (locked) SpotClip.NONE else clip, disabled = torch || meterUnavailable)
                    power?.takeIf { it.fit != PowerFit.MATCH }?.let {
                        WarningPill(powerAdvice(it, aperture.label))
                    }
                }

                // Top left: scene cues.
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 16.dp)
                        .chrome("cues"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showCamera) {
                        GateLightBall(gate)
                        GateFalloffBadge(gate, usedMeters, upright)
                    }
                }

                // Top right: phone light and restart.
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 16.dp)
                        .chrome("corner"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showCamera) {
                        GlassButton(
                            onClick = { camera.torch = !torch },
                            icon = if (torch) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                            description = stringResource(if (torch) R.string.torch_off else R.string.torch_on),
                            tint = if (torch) Color.Black else HudWhite,
                            fill = if (torch) Color.White.copy(alpha = 0.88f) else Glass,
                            modifier = upright,
                        )
                    }
                }

                // Bottom: controls in thumb reach, then the dials.
                val hideDescription = stringResource(R.string.full_frame_on)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(tween(220)) { it },
                            exit = slideOutVertically(tween(220)) { it },
                        )
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .chrome("dials")
                        .pointerInput(showCamera) {
                            if (!showCamera) return@pointerInput
                            var pulled = 0f
                            detectVerticalDragGestures(
                                onDragStart = { pulled = 0f },
                                onVerticalDrag = { change, amount ->
                                    pulled += amount
                                    if (pulled > PULL_DP.dp.toPx()) {
                                        change.consume()
                                        onFullFrame(true)
                                    }
                                },
                            )
                        }
                        .semantics {
                            customActions = listOf(CustomAccessibilityAction(hideDescription) { onFullFrame(true); true })
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.38f),
                    contentColor = HudWhite,
                ) {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        // Grab handle: pull the panel down for full frame.
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 6.dp, bottom = 4.dp)
                                .size(width = 32.dp, height = 4.dp)
                                .background(HudWhite.copy(alpha = 0.45f), RoundedCornerShape(2.dp)),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                GlassButton(
                                    onClick = {
                                        if (!manual) reading?.meters?.let { onManualMeters(distanceScale.snap(it)) }
                                        manual = !manual
                                    },
                                    icon = Icons.Outlined.Straighten,
                                    description = stringResource(if (manual) R.string.use_camera_distance else R.string.use_fixed_distance),
                                    tint = if (manual) Color.Black else HudWhite,
                                    fill = if (manual) Color.White.copy(alpha = 0.88f) else Glass,
                                    modifier = upright,
                                )
                                GlassButton(
                                    onClick = {
                                        locked = false
                                        heldEv = null
                                        gate.resetMeasurement()
                                    },
                                    icon = Icons.Outlined.RestartAlt,
                                    description = stringResource(R.string.reset_measurement),
                                    modifier = upright,
                                )
                            }
                            GlassButton(
                                onClick = {
                                    if (locked) {
                                        locked = false
                                        heldEv = null
                                    } else if (liveEv != null || reading != null || manual) {
                                        heldEv = liveEv
                                        locked = true
                                    }
                                },
                                icon = if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                description = stringResource(if (locked) R.string.release_reading else R.string.hold_reading),
                                tint = if (locked) Color.Black else HudWhite,
                                fill = if (locked) HeldFill else Glass,
                                modifier = upright,
                            )
                            Row(
                                Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            ) {
                                PriorityButton(
                                    shutterPriority = settings.shutterPriority,
                                    onToggle = { settings.shutterPriority = !settings.shutterPriority },
                                    modifier = upright,
                                )
                                GlassButton(
                                    onClick = autoMeter,
                                    icon = Icons.Outlined.BrightnessAuto,
                                    description = stringResource(R.string.meter_auto),
                                    tint = if (ev != null) HudWhite else HudWhite.copy(alpha = 0.4f),
                                    modifier = upright,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = manual,
                            enter = expandVertically() + fadeIn(tween(180)),
                            exit = shrinkVertically() + fadeOut(tween(120)),
                        ) {
                            val scale = settings.distanceScale
                            key(settings.activeProfileId, scale) {
                                val symbol = unitSymbol(scale.unit)
                                val labels = remember(scale, symbol) {
                                    List(scale.size) { index -> "${formatDistanceValue(scale.valueAt(index))} $symbol" }
                                }
                                HorizontalDial(
                                    label = stringResource(R.string.manual_distance),
                                    values = labels,
                                    selectedIndex = scale.indexFor(fixedMeters),
                                    onSelect = { index -> onManualMeters(scale.metersAt(index)) },
                                    icon = Icons.Outlined.Straighten,
                                )
                            }
                        }
                        key(settings.activeProfileId, settings.shutterStep, settings.slowestShutter, settings.fastestShutter) {
                            HorizontalDial(
                                label = stringResource(R.string.meter_shutter),
                                values = shutterSpeeds.map { it.label },
                                selectedIndex = shutterIndex,
                                onSelect = { index ->
                                    shutterSeconds = shutterSpeeds[index].seconds
                                    shutterSpin = null
                                },
                                spin = shutterSpin,
                                icon = Icons.Outlined.ShutterSpeed,
                            )
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
                                selectedIndex = apertures.indexOfFirst { it.label == aperture.label }.coerceAtLeast(0),
                                onSelect = { index ->
                                    settings.targetLabel = apertures[index].label
                                    apertureSpin = null
                                },
                                spin = apertureSpin,
                                icon = Icons.Outlined.Camera,
                            )
                        }
                    }
                }
            }
        }
        if (fullFrame) {
            // A faint handle where the panel was: swipe up to bring it back.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(HudWhite.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * The center reticle, or the tracked subject's marker, with the distance or
 * a tracking warning under it, "under" meaning down as the phone is held. The reticle pulses while searching and turns amber on a rough
 * estimate; its ring is the spot meter's area.
 */
@Composable
private fun BoxScope.Aim(
    subjectState: State<SubjectMark?>,
    searching: Boolean,
    estimate: Boolean,
    meters: Float?,
    unitLabel: @Composable (Float) -> String,
    locked: Boolean,
    warning: String?,
    subjectDescription: String,
    labelModifier: Modifier = Modifier,
    turn: () -> Float = { 0f },
) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    )
    val subject = subjectState.value?.let { Offset(it.x, it.y) to it.onScreen }
    val baseInk = if (estimate && !locked) WarningAmber else HudWhite
    val pulsing = searching && subject == null
    var size by remember { mutableStateOf(Size.Zero) }
    val (target, onScreen) = subject ?: (Offset(size.width / 2f, size.height / 2f) to true)
    val x by animateFloatAsState(target.x, spring(stiffness = 1400f), label = "aimX")
    val y by animateFloatAsState(target.y, spring(stiffness = 1400f), label = "aimY")
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { if (subject != null) contentDescription = subjectDescription },
    ) {
        // Read the pulse here, in drawing, so it repaints without recomposing.
        val ink = if (pulsing) baseInk.copy(alpha = pulse) else baseInk
        val radius = SpotRadius.toPx()
        val stroke = 1.dp.toPx()
        val shade = Color.Black.copy(alpha = 0.45f)
        if (subject != null) {
            // A faint center cross still shows where the phone points.
            drawCross(this.center, 5.dp.toPx(), stroke, HudWhite.copy(alpha = 0.45f), shade.copy(alpha = 0.2f))
            val point = Offset(
                x.coerceIn(radius, this.size.width - radius),
                y.coerceIn(radius, this.size.height - radius),
            )
            val alpha = if (onScreen) 1f else 0.45f
            drawCircle(shade, radius, point, style = Stroke(stroke + 2.dp.toPx()))
            drawCircle(ink.copy(alpha = alpha), radius, point, style = Stroke(2.dp.toPx()))
            drawCircle(ink.copy(alpha = alpha), 3.dp.toPx(), point)
        } else {
            val point = Offset(x, y)
            drawCircle(shade, radius, point, style = Stroke(stroke + 2.dp.toPx()))
            drawCircle(ink, radius, point, style = Stroke(stroke))
            drawCross(point, 6.dp.toPx(), stroke, ink, shade)
        }
    }
    Column(
        Modifier
            .offset { IntOffset((x - size.width / 2f).roundToInt(), (y - size.height / 2f).roundToInt()) }
            .align(Alignment.Center)
            // Below the cross as the phone is held: screen-down in portrait,
            // toward a screen side when the phone is turned.
            .offset {
                val distance = (SpotRadius + 26.dp).toPx()
                val angle = Math.toRadians(turn().toDouble())
                IntOffset((-sin(angle) * distance).roundToInt(), (cos(angle) * distance).roundToInt())
            }
            .then(labelModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (meters != null) {
            Text(
                unitLabel(meters),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (locked) HeldFill else Glass)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                color = if (locked) Color.Black else HudWhite,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = if (locked) FontWeight.SemiBold else FontWeight.Medium,
                    shadow = if (locked) null else ReadoutShadow,
                ),
            )
        } else if (warning != null) {
            WarningPill(warning)
        }
    }
}

private fun DrawScope.drawCross(center: Offset, split: Float, stroke: Float, ink: Color, shade: Color) {
    val wide = stroke + 2.dp.toPx()
    drawLine(shade, Offset(center.x - split, center.y), Offset(center.x + split, center.y), wide)
    drawLine(ink, Offset(center.x - split, center.y), Offset(center.x + split, center.y), stroke)
    drawLine(shade, Offset(center.x, center.y - split), Offset(center.x, center.y + split), wide)
    drawLine(ink, Offset(center.x, center.y - split), Offset(center.x, center.y + split), stroke)
}

/**
 * Ambient exposure as a camera-style meter: ticks from −3 to +3 stops, a
 * needle that glides to the reading, and the number beside it. Green when
 * within a third of a stop. A clipped spot shows a hollow needle, since the
 * true value is further out than shown.
 */
@Composable
internal fun ExposureNeedle(over: Double?, clip: SpotClip, disabled: Boolean) {
    val shown = over?.coerceIn(-3.4, 3.4)?.toFloat() ?: 0f
    val position by animateFloatAsState(shown, spring(dampingRatio = 0.7f, stiffness = 300f), label = "needle")
    val onMeter = over != null && abs(over) <= 0.34
    val needleColor = when {
        over == null || disabled -> HudWhite.copy(alpha = 0.3f)
        onMeter -> OnMeterGreen
        else -> HudWhite
    }
    val description = if (over != null && !disabled) {
        stringResource(R.string.exposure_needle, LightMath.formatStops(over))
    } else {
        stringResource(R.string.meter_searching)
    }
    Row(
        Modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(width = 168.dp, height = 26.dp)) {
            val left = 6.dp.toPx()
            val right = size.width - 6.dp.toPx()
            val baseline = 12.dp.toPx()
            val perStop = (right - left) / 6.8f
            val mid = (left + right) / 2f
            val shade = Color.Black.copy(alpha = 0.4f)
            for (third in -9..9) {
                val tx = mid + third / 3f * perStop
                val full = third % 3 == 0
                val tall = when {
                    third == 0 -> 11.dp.toPx()
                    full -> 8.dp.toPx()
                    else -> 4.dp.toPx()
                }
                val alpha = if (full) 0.9f else 0.5f
                drawLine(shade, Offset(tx, baseline - tall), Offset(tx, baseline), 3.dp.toPx())
                drawLine(HudWhite.copy(alpha = alpha), Offset(tx, baseline - tall), Offset(tx, baseline), 1.dp.toPx())
            }
            val nx = mid + position * perStop
            val tri = Path().apply {
                moveTo(nx, baseline + 2.dp.toPx())
                lineTo(nx - 6.dp.toPx(), baseline + 12.dp.toPx())
                lineTo(nx + 6.dp.toPx(), baseline + 12.dp.toPx())
                close()
            }
            drawPath(tri, shade, style = Stroke(3.dp.toPx()))
            if (clip == SpotClip.NONE) {
                drawPath(tri, needleColor)
            } else {
                drawPath(tri, needleColor, style = Stroke(1.5.dp.toPx()))
            }
        }
        Text(
            if (over != null && !disabled) LightMath.formatStops(over) else "—",
            modifier = Modifier.width(44.dp),
            color = needleColor.copy(alpha = 1f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.Medium,
                shadow = ReadoutShadow,
            ),
            maxLines = 1,
        )
    }
}

/**
 * The main light as a lit ball, shaded like a real sphere seen from the
 * phone: light from behind the photographer lights the middle, light from a
 * side lights that side, and light behind the subject leaves the ball dark
 * with a glowing rim. Harder light gives a darker shadow side.
 *
 * With [onClick], a tap marks the light under the cross, and a marked light
 * gets an amber ring; another tap goes back to ARCore's estimate. Without a
 * cue yet, the ball is lit flat from the front and dimmed.
 */
@Composable
internal fun LightBall(cue: LightCue?, marked: Boolean = false, onClick: (() -> Unit)? = null) {
    val shown = cue ?: LightCue(0f, 0f, 1f, 0f)
    val x by animateFloatAsState(shown.x, tween(BALL_TWEEN_MS), label = "lightX")
    val y by animateFloatAsState(shown.y, tween(BALL_TWEEN_MS), label = "lightY")
    val z by animateFloatAsState(shown.z, tween(BALL_TWEEN_MS), label = "lightZ")
    val contrast by animateFloatAsState(shown.contrast, tween(BALL_TWEEN_MS), label = "lightContrast")
    val description = stringResource(if (marked) R.string.light_marked else R.string.light_direction)
    val bitmap = remember { android.graphics.Bitmap.createBitmap(BALL_PX, BALL_PX, android.graphics.Bitmap.Config.ARGB_8888) }
    val pixels = remember { IntArray(BALL_PX * BALL_PX) }
    val image = remember(x, y, z, contrast) {
        shadeBall(pixels, x, y, z, contrast)
        bitmap.setPixels(pixels, 0, BALL_PX, 0, 0, BALL_PX, BALL_PX)
        bitmap.asImageBitmap()
    }
    val tap = onClick?.let { confirmationClick(it) }
    Canvas(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Glass)
            .then(if (tap != null) Modifier.clickable(onClick = tap) else Modifier)
            .semantics { contentDescription = description }
            .graphicsLayer { alpha = if (cue == null && !marked) 0.5f else 1f },
    ) {
        val inset = 5.dp.toPx()
        val side = size.minDimension - inset * 2f
        drawImage(
            image,
            dstOffset = IntOffset(inset.roundToInt(), inset.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(side.roundToInt(), side.roundToInt()),
        )
        if (marked) {
            drawCircle(WarningAmber, size.minDimension / 2f - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
    }
}

private const val BALL_TWEEN_MS = 180

private const val BALL_PX = 64

/**
 * Lambert shading of a unit sphere facing the viewer (+z toward the phone)
 * under a light toward ([lx], [ly], [lz]), plus a rim term for backlight.
 */
private fun shadeBall(out: IntArray, lx: Float, ly: Float, lz: Float, contrast: Float) {
    val ambient = 0.32f - 0.22f * contrast
    val back = (-lz).coerceAtLeast(0f)
    val half = BALL_PX / 2f
    for (py in 0 until BALL_PX) {
        for (px in 0 until BALL_PX) {
            val nx = (px + 0.5f - half) / half
            val ny = -(py + 0.5f - half) / half
            val d2 = nx * nx + ny * ny
            if (d2 > 1f) {
                out[py * BALL_PX + px] = 0
                continue
            }
            val nz = kotlin.math.sqrt(1f - d2)
            val diffuse = (nx * lx + ny * ly + nz * lz).coerceAtLeast(0f)
            val rim = (1f - nz).let { it * it } * back
            val value = (ambient + (1f - ambient) * diffuse + 0.9f * rim).coerceIn(0f, 1f)
            val level = (30 + 225 * value).roundToInt()
            // Soften the outline over the last pixel.
            val edge = ((1f - kotlin.math.sqrt(d2)) * half).coerceIn(0f, 1f)
            val alpha = (255 * edge).roundToInt()
            out[py * BALL_PX + px] = (alpha shl 24) or (level shl 16) or (level shl 8) or level
        }
    }
}

/**
 * How much darker the background falls than the subject under flash: a
 * bright subject dot in front of a backdrop shaded by that many stops.
 */
@Composable
internal fun FalloffBadge(stops: Double, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(stops.toFloat(), tween(300), label = "falloff")
    val label = "−" + String.format(java.util.Locale.US, "%.1f", stops)
    val description = stringResource(R.string.falloff_description, String.format(java.util.Locale.US, "%.1f", stops))
    Column(
        modifier.semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            val level = 2.0.pow(-shown.toDouble()).toFloat().coerceIn(0.03f, 1f)
            val tone = 0.12f + 0.78f * level
            drawRect(Color(tone, tone, tone))
            val c = center
            drawCircle(Color.Black.copy(alpha = 0.35f), 9.dp.toPx(), Offset(c.x, c.y + 4.dp.toPx()))
            drawCircle(Color.White, 8.dp.toPx(), Offset(c.x, c.y + 4.dp.toPx()))
            drawCircle(Color.White, 4.dp.toPx(), Offset(c.x, c.y - 9.dp.toPx()))
        }
        Text(
            label,
            color = HudWhite,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum", shadow = ReadoutShadow),
        )
    }
}

/**
 * The profile lens on 35 mm film as a vertical shot on the screen: 24 mm
 * across, 36 mm down. Turn the phone to frame a horizontal shot. The area
 * outside is dimmed, and the frame is a thin outline with bold corners. The
 * lines stop short of the controls in [avoid] instead of running under them.
 * A lens wider than the preview runs past the edges and is left undimmed.
 */
@Composable
private fun FrameLines(field: ViewField, focalMm: Int, avoid: () -> Collection<Rect>) {
    val description = stringResource(R.string.frame_lines, focalMm)
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
        val frameWidth = size.width * (FILM_HALF_SHORT / focalMm) / field.tanHalfWidth
        val frameHeight = size.height * (FILM_HALF_LONG / focalMm) / field.tanHalfHeight
        val rect = Rect(
            center.x - frameWidth / 2f,
            center.y - frameHeight / 2f,
            center.x + frameWidth / 2f,
            center.y + frameHeight / 2f,
        )
        if (frameWidth < size.width && frameHeight < size.height) {
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRect(rect)
            }
            drawPath(mask, Color.Black.copy(alpha = 0.4f))
        }
        val gap = 8.dp.toPx()
        val holes = Path().apply {
            avoid().forEach { area ->
                if (area.width > 0f && area.height > 0f) {
                    addRoundRect(RoundRect(area.inflate(gap), CornerRadius(gap + 16.dp.toPx())))
                }
            }
        }
        clipPath(holes, ClipOp.Difference) {
            val shade = Color.Black.copy(alpha = 0.3f)
            val thin = 1.dp.toPx()
            drawRect(shade, rect.topLeft, rect.size, style = Stroke(thin + 2.dp.toPx()))
            drawRect(Color.White.copy(alpha = 0.6f), rect.topLeft, rect.size, style = Stroke(thin))
            val arm = minOf(18.dp.toPx(), rect.width / 4f, rect.height / 4f)
            val thick = 3.dp.toPx()
            listOf(
                rect.topLeft to Offset(1f, 1f),
                rect.topRight to Offset(-1f, 1f),
                rect.bottomLeft to Offset(1f, -1f),
                rect.bottomRight to Offset(-1f, -1f),
            ).forEach { (corner, dir) ->
                val corners = Path().apply {
                    moveTo(corner.x + dir.x * arm, corner.y)
                    lineTo(corner.x, corner.y)
                    lineTo(corner.x, corner.y + dir.y * arm)
                }
                drawPath(corners, shade, style = Stroke(thick + 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(corners, Color.White, style = Stroke(thick, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}


/** The camera-dial letter for auto: A keeps the aperture, S keeps the shutter. */
@Composable
internal fun PriorityButton(shutterPriority: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (shutterPriority) R.string.meter_shutter_priority else R.string.meter_aperture_priority)
    Surface(
        onClick = confirmationClick(onToggle),
        shape = CircleShape,
        color = Glass,
        contentColor = HudWhite,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (shutterPriority) "S" else "A",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/** The light ball for [gate]: reads the light here, so only the ball updates. */
@Composable
private fun GateLightBall(gate: MeasureGate) {
    val marked = gate.lightMarked.value
    LightBall(
        cue = gate.light.value,
        marked = marked,
        onClick = { if (marked) gate.clearLight() else gate.markLight() },
    )
}

/** Background falloff under flash: inverse square from subject to background. */
@Composable
private fun GateFalloffBadge(gate: MeasureGate, subjectMeters: Float?, modifier: Modifier) {
    val background = gate.backgroundMeters.value
    val falloff = steadyFalloff(
        if (subjectMeters != null && background != null && background > subjectMeters) {
            (2.0 * ln(background.toDouble() / subjectMeters) / ln(2.0)).coerceIn(0.0, 8.0)
        } else {
            null
        },
    )
    falloff?.let { FalloffBadge(it, modifier) }
}

/**
 * The falloff as shown: it moves only when the reading has changed by a
 * third of a stop, and stays up through gaps shorter than [FALLOFF_HOLD_MS].
 */
@Composable
private fun steadyFalloff(raw: Double?): Double? {
    var shown by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(raw) {
        if (raw == null) {
            delay(FALLOFF_HOLD_MS)
            shown = null
        } else {
            val current = shown
            if (current == null || abs(raw - current) >= 1.0 / 3.0) shown = raw
        }
    }
    return shown
}

private const val FALLOFF_HOLD_MS = 1500L

@Composable
private fun trackingMessage(reason: TrackingFailureReason?): String? = when (reason) {
    null, TrackingFailureReason.NONE -> null
    TrackingFailureReason.INSUFFICIENT_LIGHT -> stringResource(R.string.track_dark)
    TrackingFailureReason.EXCESSIVE_MOTION -> stringResource(R.string.track_motion)
    TrackingFailureReason.INSUFFICIENT_FEATURES -> stringResource(R.string.track_features)
    TrackingFailureReason.CAMERA_UNAVAILABLE -> stringResource(R.string.track_camera)
    else -> stringResource(R.string.track_lost)
}

/** How far a pull or swipe must travel to hide or show the controls. */
private const val PULL_DP = 48

private val OnMeterGreen = Color(0xFFA5D6A7)
private const val FILM_HALF_LONG = 18f
private const val FILM_HALF_SHORT = 12f

/**
 * The phone's physical orientation, snapped to 0, 90 or 270 degrees (left or
 * right side up) once it is within 30° of one. Upside down keeps the last.
 */
@Composable
private fun rememberHeldQuarter(): Int {
    val context = LocalContext.current
    var quarter by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return
                val snapped = when (degrees) {
                    in 0..30, in 330..359 -> 0
                    in 60..120 -> 90
                    in 240..300 -> 270
                    else -> return
                }
                if (snapped != quarter) quarter = snapped
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return quarter
}
