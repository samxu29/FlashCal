package io.github.samxu29.flashcal.ar

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.ui.confirmationClick
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.scene.SceneUnderstanding

@Composable
fun ArMeasureView(
    gate: MeasureGate,
    torch: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var requestCamera by remember { mutableStateOf(false) }
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(cameraGranted) {
        if (cameraGranted) gate.setCameraBlocked(false)
    }
    // Deferring permission in onboarding must not trigger another automatic prompt.
    if (!cameraGranted && !requestCamera) {
        LaunchedEffect(gate) { gate.setCameraBlocked(true) }
        Column(
            modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.onboard_camera_optional), color = Color.White)
            Button(onClick = confirmationClick {
                requestCamera = true
                gate.setCameraBlocked(false)
            }) {
                Text(stringResource(R.string.onboard_allow_camera))
            }
        }
        return
    }
    val onFrame = remember(gate) {
        { _: Session, frame: Frame -> gate.onFrame(frame) }
    }
    val onFailure = remember(gate) {
        { reason: TrackingFailureReason? -> gate.setFailure(reason) }
    }
    val onCameraPermission = remember(gate) {
        { state: Any? -> gate.setCameraBlocked(state != null) }
    }
    val onAvailability = remember(gate) {
        { availability: Any? -> gate.setArBlocked(availability != null) }
    }
    val onFailed = remember(gate) {
        { _: Exception -> gate.markSessionFailed() }
    }

    ARSceneView(
        modifier = modifier.onSizeChanged { gate.onSize(it.width, it.height) },
        sessionCameraConfig = null,
        flashMode = if (torch) Config.FlashMode.TORCH else Config.FlashMode.OFF,
        depthMode = Config.DepthMode.AUTOMATIC,
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
        focusMode = Config.FocusMode.AUTO,
        planeRenderer = false,
        sceneUnderstanding = SceneUnderstanding.None,
        onSessionUpdated = onFrame,
        onTrackingFailureChanged = onFailure,
        onCameraPermissionStateChanged = onCameraPermission,
        onARCoreAvailability = onAvailability,
        onSessionFailed = onFailed,
    )
}
