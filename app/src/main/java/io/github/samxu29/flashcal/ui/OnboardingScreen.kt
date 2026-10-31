package io.github.samxu29.flashcal.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.samxu29.flashcal.R

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var page by rememberSaveable { mutableIntStateOf(0) }
    var permissionResults by rememberSaveable { mutableIntStateOf(0) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        permissionResults++
    }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = page > 0) { page-- }
    SystemBarIconStyle(onDarkBackground = false)
    val titles = listOf(R.string.onboard_camera_title, R.string.onboard_profile_title, R.string.onboard_dials_title, R.string.onboard_controls_title)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.onboard_step, page + 1, titles.size), style = MaterialTheme.typography.labelLarge)
            AnimatedContent(targetState = page, modifier = Modifier.weight(1f), label = "onboarding") { step ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (step) {
                        0 -> CameraPagePreview()
                        1 -> ProfilePagePreview()
                        2 -> DialsPagePreview()
                        else -> ControlsPagePreview()
                    }
                    Text(stringResource(titles[step]), style = MaterialTheme.typography.headlineSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        when (step) {
                            0 -> CameraPageRows()
                            1 -> ProfilePageRows()
                            2 -> DialsPageRows()
                            else -> ControlsPageRows()
                        }
                    }
                    if (step == 0) {
                        val permanentlyDenied = permissionResults > 0 && !granted && context.onboardingActivity()?.let {
                            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
                        } == true
                        Button(
                            enabled = !granted,
                            onClick = confirmationClick {
                                if (permanentlyDenied) {
                                    context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                } else {
                                    permission.launch(Manifest.permission.CAMERA)
                                }
                            },
                        ) {
                            Text(stringResource(when {
                                granted -> R.string.onboard_camera_granted
                                permanentlyDenied -> R.string.onboard_open_settings
                                else -> R.string.onboard_allow_camera
                            }))
                        }
                        if (permissionResults > 0 && !granted) {
                            Text(stringResource(R.string.onboard_camera_denied), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = confirmationClick { page-- }, enabled = page > 0) {
                    Text(stringResource(R.string.onboard_back))
                }
                Button(onClick = confirmationClick {
                    if (page == titles.lastIndex) onFinish() else page++
                }) {
                    Text(stringResource(if (page == titles.lastIndex) R.string.onboard_start else R.string.onboard_next))
                }
            }
        }
    }
}

private tailrec fun Context.onboardingActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.onboardingActivity()
    else -> null
}
