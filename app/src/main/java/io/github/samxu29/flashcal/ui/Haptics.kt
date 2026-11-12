package io.github.samxu29.flashcal.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

/** Android's normal confirmation feedback respects the user's system haptic settings. */
@Composable
fun confirmationClick(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onClick()
    }
}

/** How long a finger has to stay down before [holdToggle] engages. */
private const val HOLD_MS = 600L

/**
 * A toggle for holding a reading. Engaging it takes a touch and hold of
 * [HOLD_MS]; it vibrates and acts while the finger is still down, so the held
 * reading and frame come from before lifting the finger moves the phone. A
 * quick tap does nothing then. Once [held], a tap releases it at touch-down.
 * Accessibility services still get a normal click action.
 */
@Composable
fun Modifier.holdToggle(held: Boolean, label: String? = null, action: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(action)
    val isHeld by rememberUpdatedState(held)
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                if (!isHeld) {
                    val lifted = withTimeoutOrNull(HOLD_MS) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (lifted != null) return@awaitEachGesture
                }
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                current()
                waitForUpOrCancellation()
            }
        }
        .semantics {
            onClick(label) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                current()
                true
            }
        }
}
