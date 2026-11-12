package io.github.samxu29.flashcal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.ar.ArMeasureView
import io.github.samxu29.flashcal.ar.SharedArCamera
import io.github.samxu29.flashcal.data.rememberGearSettings
import kotlinx.coroutines.delay

private enum class AppDestinations(val label: Int) {
    MANUAL(R.string.nav_manual),
    CAMERA(R.string.nav_camera),
    SETTINGS(R.string.nav_settings),
}

@Composable
fun FlashCalApp(
    onReady: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
) {
    val settings = rememberGearSettings()
    var destination by rememberSaveable { mutableStateOf(AppDestinations.MANUAL) }
    var manualMeters by rememberSaveable { mutableFloatStateOf(2.5f) }
    val haptics = LocalHapticFeedback.current
    val camera = remember { SharedArCamera() }
    val tabStates = rememberSaveableStateHolder()
    // Full frame on the camera tab; kept while other tabs are showing.
    var fullFrame by rememberSaveable { mutableStateOf(false) }
    val onCameraTab = destination == AppDestinations.CAMERA
    // Keep the camera open for a while after leaving its tab, so a quick trip
    // to Manual or Settings does not reopen it (and click) on return.
    var keepCamera by remember { mutableStateOf(onCameraTab) }
    LaunchedEffect(onCameraTab) {
        if (onCameraTab) {
            keepCamera = true
        } else {
            // Layout choices carry over, but a tracked subject does not: back on the
            // camera tab, measuring starts again from the center cross.
            camera.gate.clearSubject()
            camera.gate.pauseMeasurement()
            delay(CAMERA_LINGER_MS)
            keepCamera = false
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { item ->
                item(
                    selected = item == destination,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        destination = item
                    },
                    icon = {
                        Icon(
                            imageVector = item.icon(),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(item.label)) },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (keepCamera) {
                ArMeasureView(
                    gate = camera.gate,
                    torch = camera.torch && onCameraTab,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!onCameraTab) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
            AnimatedContent(
                targetState = destination,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(Motion) { full -> if (forward) full / 5 else -full / 5 } +
                        fadeIn(tween(260, easing = FastOutSlowInEasing))
                    val exit = slideOutHorizontally(Motion) { full -> if (forward) -full / 5 else full / 5 } +
                        fadeOut(tween(180, easing = FastOutSlowInEasing))
                    enter togetherWith exit
                },
                label = "tabs",
            ) { tab ->
                // Each tab keeps its saved state (fixed distance, shutter, frame turn...)
                // while another tab is showing.
                tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        AppDestinations.MANUAL -> ManualScreen(
                            settings = settings,
                            meters = manualMeters,
                            onMeters = { manualMeters = it },
                            onReady = onReady,
                        )
                        AppDestinations.CAMERA -> CameraScreen(
                            settings,
                            camera = camera,
                            onReady = onReady,
                            fullFrame = fullFrame,
                            onFullFrame = { fullFrame = it },
                            // One fixed distance for the Manual and Camera tabs.
                            manualMeters = manualMeters,
                            onManualMeters = { manualMeters = it },
                        )
                        AppDestinations.SETTINGS -> GearScreen(
                            settings,
                            onShowOnboarding = onShowOnboarding,
                            previewField = camera.gate.viewField.value,
                        )
                    }
                }
            }
        }
    }
}

/** How long the camera stays open after leaving the camera tab. */
private const val CAMERA_LINGER_MS = 10_000L

private val Motion = tween<IntOffset>(320, easing = FastOutSlowInEasing)

private fun AppDestinations.icon(): ImageVector = when (this) {
    AppDestinations.MANUAL -> Icons.Outlined.Straighten
    AppDestinations.CAMERA -> Icons.Outlined.PhotoCamera
    AppDestinations.SETTINGS -> Icons.Outlined.Settings
}
