package io.github.samxu29.flashcal.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import io.github.samxu29.flashcal.domain.ApertureStep
import io.github.samxu29.flashcal.domain.ApertureStop
import io.github.samxu29.flashcal.domain.DistanceScale
import io.github.samxu29.flashcal.domain.DistanceUnit
import io.github.samxu29.flashcal.domain.FlashMath
import io.github.samxu29.flashcal.domain.GodoxIm30
import io.github.samxu29.flashcal.domain.NikonFm
import io.github.samxu29.flashcal.domain.PowerStep
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log2

private const val PREFS_NAME = "flashcal_gear"
private const val KEY_ISO = "iso"
private const val KEY_POWER = "power"
private const val KEY_GUIDE_NUMBER = "guide_number"
private const val KEY_APERTURE = "target_aperture"
private const val KEY_APERTURE_OPTIONS = "aperture_options"
private const val KEY_PROFILES = "profiles"
private const val KEY_ACTIVE = "active_profile"

private val DEFAULT_APERTURES = setOf("1.4", "2", "2.8", "4", "5.6", "8", "11", "16")

data class FlashProfile(
    val id: String,
    val name: String,
    val iso: Int,
    val powerDenominator: Int,
    val guideNumberText: String,
    val targetLabel: String,
    val apertureOptions: Set<String>,
    val distanceScale: DistanceScale = DistanceScale.DEFAULT,
    val apertureStep: ApertureStep = ApertureStep.FULL,
    val customAperture: String = "",
    val widestAperture: Double = Double.NaN,
    val narrowestAperture: Double = Double.NaN,
)

@Stable
class GearSettingsState(
    iso: Int,
    powerDenominator: Int,
    guideNumberText: String,
    targetLabel: String,
    apertureOptions: Set<String>,
    profiles: List<FlashProfile>? = null,
    activeProfileId: String = "default",
    profileName: String = "Profile 1",
    distanceScale: DistanceScale = DistanceScale.DEFAULT,
    apertureStep: ApertureStep = ApertureStep.FULL,
    customAperture: String = "",
    widestAperture: Double = Double.NaN,
    narrowestAperture: Double = Double.NaN,
) {
    var iso by mutableIntStateOf(iso)
    var powerDenominator by mutableIntStateOf(powerDenominator)
    var guideNumberText by mutableStateOf(guideNumberText)
    var targetLabel by mutableStateOf(targetLabel)
    var apertureStep by mutableStateOf(apertureStep)
        private set
    var widestAperture by mutableDoubleStateOf(
        finiteEndpoint(widestAperture, apertureOptions, apertureStep, wantWidest = true),
    )
        private set
    var narrowestAperture by mutableDoubleStateOf(
        finiteEndpoint(narrowestAperture, apertureOptions, apertureStep, wantWidest = false),
    )
        private set
    var apertureOptions by mutableStateOf(standardLabels(apertureStep, this.widestAperture, this.narrowestAperture))
        private set
    var customAperture by mutableStateOf(customAperture)
        private set
    var distanceScale by mutableStateOf(distanceScale)
    var profileName by mutableStateOf(profileName)
    var activeProfileId by mutableStateOf(activeProfileId)
    var structureTick by mutableIntStateOf(0)
    var profiles by mutableStateOf(
        profiles ?: listOf(
            FlashProfile(
                id = activeProfileId,
                name = profileName,
                iso = iso,
                powerDenominator = powerDenominator,
                guideNumberText = guideNumberText,
                targetLabel = targetLabel,
                apertureOptions = this.apertureOptions,
                distanceScale = distanceScale,
                apertureStep = this.apertureStep,
                customAperture = this.customAperture,
                widestAperture = this.widestAperture,
                narrowestAperture = this.narrowestAperture,
            ),
        ),
    )

    val distanceUnit: DistanceUnit get() = distanceScale.unit

    /** Guide number in meters for the math; [guideNumberText] is in [distanceUnit]. */
    val guideNumber: Double
        get() = guideNumberMeters(guideNumberText, distanceUnit) ?: GodoxIm30.GUIDE_NUMBER_METERS

    val guideNumberValid: Boolean
        get() = guideNumberMeters(guideNumberText, distanceUnit) != null

    /** Switches dial, readings, and guide number to [unit], keeping the same physical values. */
    fun setDistanceUnit(unit: DistanceUnit) {
        val from = distanceUnit
        if (unit == from) return
        guideNumberText.toDoubleOrNull()?.let { typed ->
            guideNumberText = formatOneDecimalTrimmed(typed * from.metersPerUnit / unit.metersPerUnit)
        }
        distanceScale = distanceScale.inUnit(unit)
    }

    val power: PowerStep
        get() = GodoxIm30.POWER_STEPS.firstOrNull { it.denominator == powerDenominator }
            ?: GodoxIm30.POWER_STEPS.first()

    /** Printed marks between the saved limits, before any custom f-number. */
    val standardApertures: List<ApertureStop>
        get() {
            val scale = FlashMath.scale(apertureStep)
            val included = scale.filter { it.label in apertureOptions }
            return included.ifEmpty { listOf(scale.first { it.label == "4" }) }
        }

    val enabledApertures: List<ApertureStop>
        get() = FlashMath.aperturesInRange(apertureStep, widestAperture, narrowestAperture)
            .ifEmpty { listOf(ApertureStop("4", 4.0)) }

    val targetAperture: ApertureStop
        get() = enabledApertures.firstOrNull { it.label == targetLabel } ?: enabledApertures.first()

    fun setApertureStep(step: ApertureStep): Boolean {
        if (step == apertureStep) return false
        apertureStep = step
        apertureOptions = standardLabels(step, widestAperture, narrowestAperture)
        keepOrSnapTarget()
        return true
    }

    fun setWidestAperture(value: Double): Boolean = setEndpoint(value, widest = true)

    fun setNarrowestAperture(value: Double): Boolean = setEndpoint(value, widest = false)

    private fun setEndpoint(value: Double, widest: Boolean): Boolean {
        val next = FlashMath.resolveAperture(FlashMath.formatApertureLabel(value))?.value ?: return false
        val other = if (widest) narrowestAperture else widestAperture
        val ordered = if (widest) next <= other + 1e-9 else next + 1e-9 >= other
        if (!ordered) return false
        val current = if (widest) widestAperture else narrowestAperture
        if (FlashMath.sameAperture(next, current)) return false
        if (widest) widestAperture = next else narrowestAperture = next
        apertureOptions = standardLabels(apertureStep, widestAperture, narrowestAperture)
        keepOrSnapTarget()
        return true
    }

    fun setCustomAperture(raw: String): Boolean {
        if (raw.isBlank()) {
            if (customAperture.isEmpty()) return false
            customAperture = ""
            keepOrSnapTarget()
            return true
        }
        val stop = FlashMath.resolveAperture(raw) ?: return false
        val covered = standardApertures.firstOrNull { FlashMath.sameAperture(it.value, stop.value) }
        if (covered != null) {
            val changed = customAperture.isNotEmpty() || targetLabel != covered.label
            customAperture = ""
            targetLabel = covered.label
            return changed
        }
        val changed = customAperture != stop.label || targetLabel != stop.label
        customAperture = stop.label
        targetLabel = stop.label
        return changed
    }

    private fun keepOrSnapTarget() {
        val stops = enabledApertures
        if (stops.any { it.label == targetLabel }) return
        val preferred = targetLabel.toDoubleOrNull() ?: 4.0
        targetLabel = stops.minBy { abs(log2(it.value) - log2(preferred)) }.label
    }

    fun selectProfile(id: String) {
        val next = profiles.firstOrNull { it.id == id } ?: return
        if (id == activeProfileId) return
        apply(next)
    }

    fun createProfile() {
        val number = generateSequence(profiles.size + 1) { it + 1 }
            .first { index -> profiles.none { it.name == "Profile $index" } }
        val created = snapshot(UUID.randomUUID().toString(), "Profile $number")
        profiles = profiles + created
        apply(created)
        structureTick++
    }

    fun deleteProfile(id: String) {
        if (profiles.size <= 1) return
        val remaining = profiles.filter { it.id != id }
        profiles = remaining
        if (activeProfileId == id) apply(remaining.first())
        structureTick++
    }

    fun snapshot(id: String = activeProfileId, name: String = profileName): FlashProfile = FlashProfile(
        id = id,
        name = name,
        iso = iso,
        powerDenominator = powerDenominator,
        guideNumberText = guideNumberText,
        targetLabel = targetLabel,
        apertureOptions = apertureOptions,
        distanceScale = distanceScale,
        apertureStep = apertureStep,
        customAperture = customAperture,
        widestAperture = widestAperture,
        narrowestAperture = narrowestAperture,
    )

    private fun apply(profile: FlashProfile) {
        activeProfileId = profile.id
        profileName = profile.name
        iso = profile.iso
        powerDenominator = profile.powerDenominator
        guideNumberText = profile.guideNumberText
        targetLabel = profile.targetLabel
        apertureStep = profile.apertureStep
        widestAperture = profile.widestAperture
        narrowestAperture = profile.narrowestAperture
        apertureOptions = standardLabels(profile.apertureStep, profile.widestAperture, profile.narrowestAperture)
        customAperture = profile.customAperture
        distanceScale = profile.distanceScale
    }
}

@Composable
fun rememberGearSettings(): GearSettingsState {
    val context = LocalContext.current
    val prefs = remember {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val state = remember {
        val (loaded, active) = loadProfiles(prefs)
        val current = loaded.first { it.id == active }
        GearSettingsState(
            iso = current.iso,
            powerDenominator = current.powerDenominator,
            guideNumberText = current.guideNumberText,
            targetLabel = current.targetLabel,
            apertureOptions = current.apertureOptions,
            profiles = loaded,
            activeProfileId = current.id,
            profileName = current.name,
            distanceScale = current.distanceScale,
            apertureStep = current.apertureStep,
            customAperture = current.customAperture,
            widestAperture = current.widestAperture,
            narrowestAperture = current.narrowestAperture,
        )
    }
    LaunchedEffect(
        state.iso,
        state.powerDenominator,
        state.guideNumberText,
        state.targetLabel,
        state.apertureStep,
        state.widestAperture,
        state.narrowestAperture,
        state.apertureOptions,
        state.customAperture,
        state.distanceScale,
        state.profileName,
        state.activeProfileId,
        state.structureTick,
    ) {
        val snapshot = state.snapshot()
        val updated = if (state.profiles.any { it.id == state.activeProfileId }) {
            state.profiles.map { if (it.id == state.activeProfileId) snapshot else it }
        } else {
            state.profiles + snapshot
        }
        if (updated != state.profiles) state.profiles = updated
        prefs.edit {
            putString(KEY_PROFILES, encodeProfiles(updated))
            putString(KEY_ACTIVE, state.activeProfileId)
        }
    }
    return state
}

fun guideNumberMeters(text: String, unit: DistanceUnit): Double? =
    text.toDoubleOrNull()
        ?.let { it * unit.metersPerUnit }
        ?.takeIf { it in GodoxIm30.MIN_GUIDE_NUMBER - 0.05..GodoxIm30.MAX_GUIDE_NUMBER + 0.05 }

private fun formatOneDecimalTrimmed(value: Double): String =
    String.format(Locale.US, "%.1f", value).removeSuffix(".0")

private fun loadProfiles(prefs: android.content.SharedPreferences): Pair<List<FlashProfile>, String> {
    decodeProfiles(prefs.getString(KEY_PROFILES, null))?.let { loaded ->
        val active = prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> loaded.any { it.id == id } }
            ?: loaded.first().id
        return loaded to active
    }
    val migrated = legacyProfile(prefs)
    return listOf(migrated) to migrated.id
}

private fun legacyProfile(prefs: android.content.SharedPreferences): FlashProfile {
    val apertures = continuousOptions(decodeOptions(
        prefs.getString(KEY_APERTURE_OPTIONS, null),
        FlashMath.FULL_STOPS.map { it.label }.toSet(),
        DEFAULT_APERTURES,
    ), FlashMath.FULL_STOPS.map { it.label }, "4")
    val savedIso = prefs.getInt(KEY_ISO, 100)
    val savedPower = prefs.getInt(KEY_POWER, 1)
    val savedAperture = prefs.getString(KEY_APERTURE, "4") ?: "4"
    return FlashProfile(
        id = "default",
        name = "Profile 1",
        iso = savedIso.takeIf { it in NikonFm.FILM_SPEEDS } ?: 100,
        powerDenominator = GodoxIm30.POWER_STEPS
            .firstOrNull { it.denominator == savedPower }
            ?.denominator
            ?: 1,
        guideNumberText = prefs.getString(KEY_GUIDE_NUMBER, "15") ?: "15",
        targetLabel = savedAperture.takeIf { it in apertures } ?: apertures.first(),
        apertureOptions = apertures,
        apertureStep = ApertureStep.FULL,
        widestAperture = endpointFromLabels(apertures, ApertureStep.FULL, wantWidest = true),
        narrowestAperture = endpointFromLabels(apertures, ApertureStep.FULL, wantWidest = false),
    )
}

private fun encodeProfiles(profiles: List<FlashProfile>): String {
    val array = JSONArray()
    profiles.forEach { profile ->
        val minLabel = FlashMath.formatApertureLabel(profile.widestAperture)
        val maxLabel = FlashMath.formatApertureLabel(profile.narrowestAperture)
        array.put(
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("iso", profile.iso)
                .put("power", profile.powerDenominator)
                .put("gn", profile.guideNumberText)
                .put("aperture", profile.targetLabel)
                .put("aperture_min", minLabel)
                .put("aperture_max", maxLabel)
                .put("aperture_step", profile.apertureStep.stored())
                .put("aperture_custom", profile.customAperture)
                .put("distance_unit", profile.distanceScale.unit.stored())
                .put("distance_min_cm", profile.distanceScale.minimum)
                .put("distance_max_cm", profile.distanceScale.maximum)
                .put("distance_increment_cm", profile.distanceScale.increment),
        )
    }
    return array.toString()
}

private fun decodeProfiles(raw: String?): List<FlashProfile>? {
    if (raw.isNullOrBlank()) return null
    val parsed = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                sanitizeProfile(item)?.let { add(it) }
            }
        }
    }.getOrNull().orEmpty()
    return parsed.takeIf { it.isNotEmpty() }
}

private fun sanitizeProfile(item: JSONObject): FlashProfile? {
    val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
    val step = ApertureStep.fromStored(item.optString("aperture_step"))
    val parsedLow = item.optString("aperture_min").toDoubleOrNull()
    val parsedHigh = item.optString("aperture_max").toDoubleOrNull()
    val apertures: Set<String>
    val widest: Double
    val narrowest: Double
    if (
        parsedLow != null && parsedHigh != null &&
        parsedLow in FlashMath.MIN_APERTURE..FlashMath.MAX_APERTURE &&
        parsedHigh in FlashMath.MIN_APERTURE..FlashMath.MAX_APERTURE &&
        parsedLow <= parsedHigh
    ) {
        widest = parsedLow
        narrowest = parsedHigh
        apertures = standardLabels(step, widest, narrowest)
    } else {
        apertures = readOptionRange(
            item, "aperture_min", "aperture_max", "apertures",
            FlashMath.scale(step).map { it.label },
            DEFAULT_APERTURES,
        )
        widest = endpointFromLabels(apertures, step, wantWidest = true)
        narrowest = endpointFromLabels(apertures, step, wantWidest = false)
    }
    val custom = sanitizeCustom(item.optString("aperture_custom"))
    val iso = item.optInt("iso", 100).takeIf { it in NikonFm.FILM_SPEEDS } ?: 100
    val power = item.optInt("power", 1)
    val aperture = item.optString("aperture", "4")
    val dial = FlashMath.aperturesInRange(step, widest, narrowest).map { it.label }.toSet()
    val allowed = if (custom.isEmpty()) dial else dial + custom
    return FlashProfile(
        id = id,
        name = item.optString("name").ifBlank { "Profile" },
        iso = iso,
        powerDenominator = GodoxIm30.POWER_STEPS
            .firstOrNull { it.denominator == power }
            ?.denominator
            ?: 1,
        guideNumberText = item.optString("gn", "15").ifBlank { "15" },
        targetLabel = aperture.takeIf { it in allowed } ?: dial.first(),
        apertureOptions = apertures,
        widestAperture = widest,
        narrowestAperture = narrowest,
        apertureStep = step,
        customAperture = custom,
        distanceScale = runCatching {
            // Keys keep their legacy "_cm" names; values are hundredths of distance_unit.
            DistanceScale(
                minimum = item.optInt("distance_min_cm", DistanceScale.DEFAULT.minimum),
                maximum = item.optInt("distance_max_cm", DistanceScale.DEFAULT.maximum),
                increment = item.optInt("distance_increment_cm", DistanceScale.DEFAULT.increment),
                unit = DistanceUnit.fromStored(item.optString("distance_unit")),
            )
        }.getOrDefault(DistanceScale.DEFAULT),
    )
}

private fun readOptionRange(
    item: JSONObject,
    minimumKey: String,
    maximumKey: String,
    legacyKey: String,
    order: List<String>,
    fallback: Set<String>,
): Set<String> {
    val first = order.indexOf(item.optString(minimumKey))
    val last = order.indexOf(item.optString(maximumKey))
    return if (first >= 0 && last >= 0) {
        order.subList(minOf(first, last), maxOf(first, last) + 1).toSet()
    } else {
        continuousOptions(decodeOptions(item.optString(legacyKey), order.toSet(), fallback), order, fallback.first())
    }
}

private fun finiteEndpoint(
    explicit: Double,
    options: Set<String>,
    step: ApertureStep,
    wantWidest: Boolean,
): Double {
    if (explicit.isFinite() && explicit in FlashMath.MIN_APERTURE..FlashMath.MAX_APERTURE) return explicit
    return endpointFromLabels(options, step, wantWidest)
}

private fun endpointFromLabels(options: Set<String>, step: ApertureStep, wantWidest: Boolean): Double {
    val included = FlashMath.scale(step).filter { it.label in options }
    val stop = if (wantWidest) included.firstOrNull() else included.lastOrNull()
    return stop?.value ?: if (wantWidest) 1.4 else 16.0
}

private fun standardLabels(step: ApertureStep, low: Double, high: Double): Set<String> =
    FlashMath.scale(step)
        .filter { it.value + 1e-9 >= low && it.value - 1e-9 <= high }
        .map { it.label }
        .toSet()

private fun sanitizeCustom(raw: String): String {
    if (raw.isBlank()) return ""
    return FlashMath.resolveAperture(raw)?.label.orEmpty()
}

private fun continuousOptions(options: Set<String>, order: List<String>, fallback: String): Set<String> {
    val indices = order.indices.filter { order[it] in options }
    if (indices.isEmpty()) return setOf(fallback)
    return order.subList(indices.first(), indices.last() + 1).toSet()
}

private fun decodeOptions(raw: String?, valid: Set<String>, fallback: Set<String>): Set<String> {
    val parsed = raw
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it in valid }
        ?.toSet()
        .orEmpty()
    return parsed.ifEmpty { fallback }
}
