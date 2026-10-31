package io.github.samxu29.flashcal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.samxu29.flashcal.R
import io.github.samxu29.flashcal.data.GearSettingsState
import io.github.samxu29.flashcal.data.guideNumberMeters
import io.github.samxu29.flashcal.domain.GodoxIm30
import io.github.samxu29.flashcal.ui.theme.FlashCalTheme

@Composable
fun GearScreen(
    settings: GearSettingsState,
    modifier: Modifier = Modifier,
    onShowOnboarding: () -> Unit = {},
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    if (editingId != null && settings.profiles.none { it.id == editingId }) {
        editingId = null
    }
    BackHandler(enabled = editingId != null) {
        editingId = null
    }
    SystemBarIconStyle(onDarkBackground = false)
    AnimatedContent(
        targetState = editingId,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val opening = targetState != null
            val enter = slideInHorizontally(ProfileMotion) { full -> if (opening) full / 4 else -full / 6 } +
                fadeIn(tween(260, easing = FastOutSlowInEasing))
            val exit = slideOutHorizontally(ProfileMotion) { full -> if (opening) -full / 6 else full / 4 } +
                fadeOut(tween(180, easing = FastOutSlowInEasing))
            enter togetherWith exit
        },
        label = "profile",
    ) { openId ->
        if (openId == null) {
            ProfileList(
                settings = settings,
                onOpen = { id ->
                    settings.selectProfile(id)
                    editingId = id
                },
                onCreate = {
                    settings.createProfile()
                    editingId = settings.activeProfileId
                },
                onShowOnboarding = onShowOnboarding,
            )
        } else {
            ProfileEditor(
                settings = settings,
                onBack = { editingId = null },
                onDelete = {
                    settings.deleteProfile(settings.activeProfileId)
                    editingId = null
                },
            )
        }
    }
}

private val ProfileMotion = tween<IntOffset>(320, easing = FastOutSlowInEasing)

@Composable
private fun ProfileList(
    settings: GearSettingsState,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onShowOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyLarge)
            }
            UnitSettings(settings)
            OutlinedButton(onClick = confirmationClick(onCreate), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_new))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.profiles.forEach { profile ->
                    val selected = profile.id == settings.activeProfileId
                    val name = if (selected) settings.profileName.ifBlank { profile.name } else profile.name
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = confirmationClick { onOpen(profile.id) }),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                val unit = settings.distanceUnit
                                val gnText = if (selected) settings.guideNumberText else profile.guideNumberText
                                Text(
                                    stringResource(
                                        R.string.profile_line,
                                        if (selected) settings.iso else profile.iso,
                                        formatGuideNumber(
                                            guideNumberMeters(gnText, unit)?.let { it / unit.metersPerUnit }
                                                ?: (GodoxIm30.GUIDE_NUMBER_METERS / unit.metersPerUnit),
                                        ),
                                        unitSymbol(unit),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.profile_open, name),
                            )
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = confirmationClick(onShowOnboarding),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.School, contentDescription = null)
            Text(stringResource(R.string.show_onboarding), Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProfileEditor(
    settings: GearSettingsState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = confirmationClick(onBack)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.profile_back),
                )
            }
            Text(
                settings.profileName.ifBlank { stringResource(R.string.profile_name) },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        OutlinedTextField(
            value = settings.profileName,
            onValueChange = { settings.profileName = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_name)) },
            singleLine = true,
        )
        FlashSettings(settings)
        FilmSettings(settings)
        ApertureRangeSettings(settings)
        DistanceRangeSettings(settings)
        if (settings.profiles.size > 1) {
            var confirmDelete by rememberSaveable { mutableStateOf(false) }
            val profileLabel = settings.profileName.ifBlank { stringResource(R.string.profile_name) }
            TextButton(onClick = confirmationClick { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(
                    stringResource(R.string.profile_delete, profileLabel),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.profile_delete_title)) },
                    text = { Text(stringResource(R.string.profile_delete_body, profileLabel)) },
                    confirmButton = {
                        TextButton(
                            onClick = confirmationClick {
                                confirmDelete = false
                                onDelete()
                            },
                        ) {
                            Text(stringResource(R.string.profile_delete_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = confirmationClick { confirmDelete = false }) {
                            Text(stringResource(R.string.profile_delete_cancel))
                        }
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GearScreenPreview() {
    FlashCalTheme {
        GearScreen(
            GearSettingsState(
                iso = 100,
                powerDenominator = 1,
                guideNumberText = "15",
                targetLabel = "4",
                apertureOptions = setOf("1.4", "2", "2.8", "4", "5.6", "8", "11", "16"),
            ),
        )
    }
}
