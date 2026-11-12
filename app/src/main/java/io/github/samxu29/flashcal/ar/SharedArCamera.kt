package io.github.samxu29.flashcal.ar

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The app's one ARCore session. The app draws a single [ArMeasureView]
 * behind the camera tab and keeps it alive briefly after leaving, so a quick
 * trip to another tab does not close and reopen the camera.
 */
@Stable
class SharedArCamera {
    val gate = MeasureGate()
    internal val meter: ArLightMeter get() = gate.meter
    var torch by mutableStateOf(false)
}
