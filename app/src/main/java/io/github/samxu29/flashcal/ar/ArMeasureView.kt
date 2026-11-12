package io.github.samxu29.flashcal.ar

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    // Re-check on resume: another screen may have been granted the camera meanwhile.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val cameraGranted = remember(resumes) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
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

    // The activity handles rotation itself, so the camera stays open when the
    // phone turns. SceneView updates ARCore only when the view changes size,
    // which misses a flip from one landscape side to the other.
    val session = remember { arrayOfNulls<Session>(1) }
    val viewSize = remember { IntArray(2) }
    val onSessionCreated = remember(gate) {
        { created: Session ->
            session[0] = created
            gate.onSessionCreated()
        }
    }
    val hostView = LocalView.current
    DisposableEffect(hostView) {
        val displays = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                val display = hostView.display ?: return
                if (display.displayId != displayId || viewSize[0] <= 0 || viewSize[1] <= 0) return
                try {
                    session[0]?.setDisplayGeometry(display.rotation, viewSize[0], viewSize[1])
                } catch (_: Exception) {
                }
            }
        }
        displays?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displays?.unregisterDisplayListener(listener) }
    }

    val generation by gate.sessionGeneration
    key(generation) {
        ARSceneView(
            modifier = modifier.onSizeChanged {
                viewSize[0] = it.width
                viewSize[1] = it.height
                gate.onSize(it.width, it.height)
            },
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
            onSessionCreated = onSessionCreated,
        )
    }
}
