package io.github.samxu29.flashcal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.data.rememberGearSettings

private enum class AppDestinations(val label: Int) {
    MANUAL(R.string.nav_manual),
    MEASURE(R.string.nav_measure),
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
            when (tab) {
                AppDestinations.MANUAL -> ManualScreen(
                    settings = settings,
                    meters = manualMeters,
                    onMeters = { manualMeters = it },
                    onReady = onReady,
                )
                AppDestinations.MEASURE -> MeasureScreen(settings, onReady = onReady)
                AppDestinations.SETTINGS -> GearScreen(settings, onShowOnboarding = onShowOnboarding)
            }
        }
    }
}

private val Motion = tween<IntOffset>(320, easing = FastOutSlowInEasing)

private fun AppDestinations.icon(): ImageVector = when (this) {
    AppDestinations.MANUAL -> Icons.Outlined.Straighten
    AppDestinations.MEASURE -> Icons.Outlined.PhotoCamera
    AppDestinations.SETTINGS -> Icons.Outlined.Settings
}
