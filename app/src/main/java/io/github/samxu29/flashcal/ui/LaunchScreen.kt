package io.github.samxu29.flashcal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.core.content.edit
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay
import io.github.samxu29.flashcal.R

private val LaunchBackground = Color(0xFF122132)

@Composable
fun FlashCalLaunchHost() {
    val context = LocalContext.current
    val preferences = remember { context.applicationContext.getSharedPreferences("flashcal_onboarding", Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(preferences.getBoolean("completed", false)) }
    var launching by rememberSaveable { mutableStateOf(onboarded) }
    if (!onboarded) {
        OnboardingScreen(onFinish = {
            preferences.edit { putBoolean("completed", true) }
            launching = true
            onboarded = true
        })
        return
    }
    // An unavailable or slow camera must not trap the calculator behind the launch artwork.
    LaunchedEffect(launching) {
        if (launching) {
            delay(5_000L)
            launching = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().then(
                if (launching) Modifier.clearAndSetSemantics { } else Modifier,
            ),
        ) {
            FlashCalApp(
                onReady = { launching = false },
                onShowOnboarding = { onboarded = false },
            )
        }
        AnimatedVisibility(
            visible = launching,
            enter = EnterTransition.None,
            exit = fadeOut(tween(220)),
        ) {
            LaunchScreen()
        }
    }
}

@Composable
private fun LaunchScreen() {
    SystemBarIconStyle(onDarkBackground = true)
    val description = stringResource(R.string.launch_loading)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(LaunchBackground)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Fit the supplied portrait without cropping its logo, title, or subtitle.
        val artworkWidth = minOf(maxWidth, maxHeight * (941f / 1672f))
        val artworkHeight = artworkWidth * (1672f / 941f)
        Box(
            Modifier
                .size(artworkWidth, artworkHeight)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Image(
                painter = painterResource(R.drawable.launch_art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // Replace the artwork's baked-in half-full bar with genuine indeterminate motion.
            Box(
                Modifier
                    .offset(x = artworkWidth * (282f / 941f), y = artworkHeight * (1435f / 1672f))
                    .size(artworkWidth * (378f / 941f), artworkHeight * (21f / 1672f))
                    .background(LaunchBackground),
                contentAlignment = Alignment.Center,
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(artworkHeight * (10f / 1672f)),
                    color = Color(0xFFFFBC4D),
                    trackColor = Color(0xFF405063),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}
