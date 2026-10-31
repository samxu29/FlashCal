package io.github.samxu29.flashcal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Android's normal confirmation feedback respects the user's system haptic settings. */
@Composable
fun confirmationClick(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onClick()
    }
}
