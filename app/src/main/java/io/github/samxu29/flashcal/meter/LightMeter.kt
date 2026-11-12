/**
 * Reflected light meter on the rear or front camera.
 *
 * Reads the exposure the camera settled on (shutter, ISO, aperture) and reports
 * EV at ISO 100 for the meter screen. It does not use ARCore. Close it before
 * another screen opens a camera.
 *
 * [setPaused] stops the repeating capture, so the preview keeps its last frame
 * while a reading is held, and restarts it on release.
 *
 * [setFrame] meters across the profile lens's 35 mm frame instead of a small
 * center spot, and sets the zoom the screen asked for. Wide angles are found
 * at run time, never assumed: some phones zoom the main camera
 * below 1× (Android 11+) and switch to the ultrawide themselves; phones that
 * list the ultrawide as its own camera get that camera opened instead, only
 * while the main one cannot go wide enough ([BackCameras]); phones with
 * neither stay on the main camera. On the back, zoom is always relative to
 * the main camera's field, so [PreviewLayout.field] is the main camera's even
 * while the ultrawide is open, and [PreviewLayout.minCameraZoom] is the widest
 * any back camera reaches. The stream uses the sensor's own aspect.
 */
package io.github.samxu29.flashcal.meter

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import io.github.samxu29.flashcal.domain.EvWindow
import io.github.samxu29.flashcal.domain.LightMath
import kotlin.math.roundToInt

internal data class MeterReading(val ev100: Double)

internal data class Viewport(val width: Int, val height: Int, val rotation: Int)

internal data class PreviewLayout(
    val contentWidth: Int,
    val contentHeight: Int,
    val rotationDegrees: Int,
    val field: LensField? = null,
    val minCameraZoom: Float = 1f,
)

/**
 * The widest profile lens whose vertical frame lines fit a [widthPx] ×
 * [heightPx] screen, in mm, or null when the camera does not say. An estimate
 * for before the camera tab has run: it shows ARCore's view of the main back
 * camera, unzoomed.
 */
internal fun widestPreviewFocal(context: Context, widthPx: Int, heightPx: Int): Double? {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
    val cameras = findBackCameras(manager) ?: return null
    val visible = visibleField(cameras.main.field, cameras.main.sensorOrientation, widthPx, heightPx) ?: return null
    return widestFittingFocal(visible.tanHalfWidth, visible.tanHalfHeight)
}

/** Back-facing cameras apps can use, main first, or null without one that reports its field. */
internal fun findBackCameras(manager: CameraManager): BackCameras? = try {
    val back = manager.cameraIdList.mapNotNull { id ->
        val characteristics = manager.getCameraCharacteristics(id)
        if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
            return@mapNotNull null
        }
        val compatible = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
        val field = LightMeter.sensorField(characteristics) ?: return@mapNotNull null
        val zoom = LightMeter.zoomRange(characteristics)
        val option = CameraOption(
            id = id,
            field = field,
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            minZoom = zoom.lower,
            maxZoom = zoom.upper,
        )
        option to compatible
    }
    val main = (back.firstOrNull { it.second } ?: back.firstOrNull())?.first
    main?.let { BackCameras.of(it, back.filter { (_, compatible) -> compatible }.map { it.first }) }
} catch (_: Exception) {
    null
}

internal class LightMeter(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("light-meter").also { it.start() }
    private val handler = Handler(thread.looper)
    private val filter = EvWindow()

    val reading = mutableStateOf<MeterReading?>(null)
    val unavailable = mutableStateOf(false)
    val canFlip = mutableStateOf(false)
    val viewport = mutableStateOf(Viewport(0, 0, Surface.ROTATION_0))
    val previewLayout = mutableStateOf<PreviewLayout?>(null)

    @Volatile private var useFront: Boolean = false
    @Volatile private var running = false
    @Volatile private var released = false
    @Volatile private var texture: SurfaceTexture? = null
    @Volatile private var viewWidth = 0
    @Volatile private var viewHeight = 0
    @Volatile private var displayRotation = Surface.ROTATION_0
    @Volatile private var paused = false
    @Volatile private var frameFocal: Double? = null
    @Volatile private var cameraZoom = 1f
    @Volatile private var backCameras: BackCameras? = null

    private var generation = 0
    private var retries = 0
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var applied: Opened? = null
    private var restart: (() -> Boolean)? = null
    private var fixedAperture = 0.0
    private var emptyFrames = 0
    private var lastTenth = Int.MIN_VALUE

    fun start() {
        if (released || running) return
        running = true
        handler.post {
            backCameras = findBackCameras(manager)
            val back = cameraId(false) != null
            val frontCamera = cameraId(true) != null
            main.post { canFlip.value = back && frontCamera }
            if (useFront && !frontCamera) useFront = false
            openIfReady()
        }
    }

    fun stop() {
        running = false
        handler.post {
            generation++
            closeDevice()
        }
        main.post {
            reading.value = null
            unavailable.value = false
            previewLayout.value = null
        }
    }

    fun release() {
        if (released) return
        released = true
        running = false
        handler.post {
            generation++
            closeDevice()
            thread.quitSafely()
        }
    }

    fun setFront(value: Boolean) {
        if (useFront == value) return
        useFront = value
        if (!running) return
        handler.post { openIfReady() }
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        handler.post {
            val current = session ?: return@post
            if (value) {
                try {
                    current.stopRepeating()
                } catch (_: Exception) {
                }
            } else if (restart?.invoke() == false) {
                scheduleRetry(generation)
            }
        }
    }

    /**
     * Meters across a 35 mm frame at [focalMm], or a small center spot when
     * null, with the camera at [zoom] (clamped to what it supports).
     */
    fun setFrame(focalMm: Double?, zoom: Float = 1f) {
        if (frameFocal == focalMm && cameraZoom == zoom) return
        frameFocal = focalMm
        cameraZoom = zoom
        handler.post {
            val wanted = if (useFront) null else backCameras?.pick(zoom)?.first?.id
            if (wanted != null && wanted != applied?.cameraId) {
                if (running) openIfReady()
            } else if (session != null && !paused && restart?.invoke() == false) {
                scheduleRetry(generation)
            }
        }
    }

    /** The zoom ratio this camera should use, in its own terms. */
    private fun ownZoom(id: String, characteristics: CameraCharacteristics): Float {
        val cameras = backCameras
        if (useFront || cameras == null) return zoomRange(characteristics).clamp(cameraZoom)
        val (option, ratio) = cameras.pick(cameraZoom)
        return if (option.id == id) ratio else zoomRange(characteristics).clamp(cameraZoom)
    }

    fun attach(surfaceTexture: SurfaceTexture, width: Int, height: Int, rotation: Int) {
        texture = surfaceTexture
        viewWidth = width
        viewHeight = height
        displayRotation = rotation
        main.post { viewport.value = Viewport(width, height, rotation) }
        if (running) handler.post { openIfReady() }
    }

    fun detach() {
        texture = null
        if (!running) return
        handler.post {
            generation++
            closeDevice()
        }
    }

    private fun openIfReady(retry: Boolean = false) {
        if (!retry) retries = 0
        if (!running || released) return
        val currentTexture = texture ?: return
        if (!hasPermission()) return
        val cameras = if (useFront) null else backCameras
        val id = cameras?.pick(cameraZoom)?.first?.id ?: cameraId(useFront) ?: run {
            main.post { unavailable.value = true }
            return
        }
        val characteristics = try {
            manager.getCameraCharacteristics(id)
        } catch (_: CameraAccessException) {
            main.post { unavailable.value = true }
            return
        }
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val display = displayDegrees(displayRotation)
        val rotation = relativeRotation(sensorOrientation, display, useFront)
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.map { it.width to it.height }
            .orEmpty()
        val sensorField = sensorField(characteristics)
        val sensorAspect = sensorField?.let { it.tanHalfWidth / it.tanHalfHeight } ?: (4.0 / 3.0)
        val longSide = (viewWidth * sensorAspect).roundToInt()
        val size = if (rotation % 180 != 0) {
            choosePreviewSize(sizes, viewWidth, longSide, rotation)
        } else {
            choosePreviewSize(sizes, longSide, viewWidth, rotation)
        }
            ?: sizes.maxByOrNull { it.first * it.second }
            ?: (1280 to 720)
        val desired = Opened(id, size.first, size.second, rotation, useFront)
        val content = uprightContentSize(size.first, size.second, sensorOrientation)
        val reference = cameras?.main
        val field = (reference?.field ?: sensorField)?.let {
            uprightField(bufferField(it, size.first, size.second), reference?.sensorOrientation ?: sensorOrientation)
        }
        val layout = PreviewLayout(
            content.first,
            content.second,
            previewViewRotation(display),
            field,
            cameras?.floorZoom ?: zoomRange(characteristics).lower,
        )
        main.post { previewLayout.value = layout }
        if (desired == applied && device != null) return

        val gen = ++generation
        closeDevice()
        filter.reset()
        emptyFrames = 0
        lastTenth = Int.MIN_VALUE
        fixedAperture = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull { it > 0f }
            ?.toDouble()
            ?: 0.0
        main.post { reading.value = null }
        try {
            currentTexture.setDefaultBufferSize(size.first, size.second)
            surface = Surface(currentTexture)
            openDevice(id, gen, characteristics, surface!!, desired)
        } catch (_: Exception) {
            scheduleRetry(gen)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openDevice(
        id: String,
        gen: Int,
        characteristics: CameraCharacteristics,
        target: Surface,
        desired: Opened,
    ) {
        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (gen != generation || !running) {
                    camera.close()
                    return
                }
                retries = 0
                device = camera
                createSession(camera, gen, characteristics, target, desired)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                if (device == camera) device = null
                scheduleRetry(gen)
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                if (device == camera) device = null
                scheduleRetry(gen)
            }
        }, handler)
    }

    @Suppress("DEPRECATION")
    private fun createSession(
        camera: CameraDevice,
        gen: Int,
        characteristics: CameraCharacteristics,
        target: Surface,
        desired: Opened,
    ) {
        try {
            camera.createCaptureSession(listOf(target), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(captureSession: CameraCaptureSession) {
                    if (gen != generation || !running) {
                        captureSession.close()
                        return
                    }
                    session = captureSession
                    val fps = meteringFps(characteristics)
                    val continuous = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                        ?.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true
                    val repeat = {
                        val zoom = zoomControl(characteristics, ownZoom(camera.id, characteristics))
                        val region = meteringRegion(characteristics, frameFocal, zoom.regionZoom)
                        startRepeating(captureSession, camera, target, region, fps, continuous, zoom, gen) ||
                            startRepeating(captureSession, camera, target, null, fps, continuous, zoom, gen)
                    }
                    restart = repeat
                    if (repeat()) {
                        applied = desired
                        main.post { unavailable.value = false }
                    } else {
                        scheduleRetry(gen)
                    }
                }

                override fun onConfigureFailed(captureSession: CameraCaptureSession) {
                    scheduleRetry(gen)
                }
            }, handler)
        } catch (_: Exception) {
            scheduleRetry(gen)
        }
    }

    private fun startRepeating(
        captureSession: CameraCaptureSession,
        camera: CameraDevice,
        target: Surface,
        region: MeteringRectangle?,
        fps: Range<Int>?,
        continuous: Boolean,
        zoom: ZoomControl,
        gen: Int,
    ): Boolean = try {
        captureSession.setRepeatingRequest(
            request(camera, target, region, fps, continuous, zoom),
            captureCallback(gen),
            handler,
        )
        true
    } catch (_: Exception) {
        false
    }

    private fun request(
        camera: CameraDevice,
        target: Surface,
        region: MeteringRectangle?,
        fps: Range<Int>?,
        continuousFocus: Boolean,
        zoom: ZoomControl,
    ): CaptureRequest {
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(target)
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
        if (continuousFocus) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        if (fps != null) builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
        if (region != null) builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
        if (zoom.ratio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom.ratio)
        }
        if (zoom.crop != null) builder.set(CaptureRequest.SCALER_CROP_REGION, zoom.crop)
        return builder.build()
    }

    private fun captureCallback(gen: Int) = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            captureSession: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            if (gen != generation || !running) return
            if (paused) {
                // A session opened while held shows one frame, then stops.
                try {
                    captureSession.stopRepeating()
                } catch (_: Exception) {
                }
                return
            }
            val state = result.get(CaptureResult.CONTROL_AE_STATE)
            if (state == CaptureResult.CONTROL_AE_STATE_SEARCHING ||
                state == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
            ) {
                return
            }
            val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
            val sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY)
            if (exposureNs == null || sensitivity == null) {
                emptyFrames++
                if (emptyFrames == EMPTY_FRAME_LIMIT) main.post { unavailable.value = true }
                return
            }
            emptyFrames = 0
            val boost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
            val aperture = result.get(CaptureResult.LENS_APERTURE)?.toDouble()?.takeIf { it > 0.0 }
                ?: fixedAperture.takeIf { it > 0.0 }
                ?: return
            val ev = LightMath.ev100(
                aperture = aperture,
                shutterSeconds = exposureNs / 1_000_000_000.0,
                iso = LightMath.effectiveIso(sensitivity, boost),
            ) ?: return
            val median = filter.add(ev, SystemClock.elapsedRealtime())
            val tenth = (median * 10.0).roundToInt()
            if (tenth == lastTenth) return
            lastTenth = tenth
            main.post {
                unavailable.value = false
                reading.value = MeterReading(median)
            }
        }
    }

    private fun scheduleRetry(gen: Int) {
        if (gen != generation || !running) return
        applied = null
        if (retries >= MAX_RETRIES) {
            closeDevice()
            main.post { unavailable.value = true }
            return
        }
        retries++
        handler.postDelayed({
            if (gen == generation && running) openIfReady(retry = true)
        }, RETRY_MS)
    }

    private fun closeDevice() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        restart = null
        try {
            surface?.release()
        } catch (_: Exception) {
        }
        surface = null
        try {
            device?.close()
        } catch (_: Exception) {
        }
        device = null
        applied = null
    }

    private fun cameraId(wantFront: Boolean): String? {
        val want = if (wantFront) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val ids = try {
            manager.cameraIdList
        } catch (_: CameraAccessException) {
            return null
        }
        val matching = ids.mapNotNull { id ->
            val characteristics = try {
                manager.getCameraCharacteristics(id)
            } catch (_: CameraAccessException) {
                return@mapNotNull null
            }
            if (characteristics.get(CameraCharacteristics.LENS_FACING) != want) return@mapNotNull null
            id to characteristics
        }
        val compatible = matching.firstOrNull { (_, characteristics) ->
            characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
        }
        return compatible?.first ?: matching.firstOrNull()?.first
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * How a zoom is applied: the zoom ratio control where the camera supports
     * that value (metering regions then follow the zoomed view), otherwise a
     * center crop for values above 1 (regions stay in full-sensor terms).
     */
    internal data class ZoomControl(val ratio: Float?, val crop: Rect?, val regionZoom: Float)

    private data class Opened(
        val cameraId: String,
        val bufferWidth: Int,
        val bufferHeight: Int,
        val rotationDegrees: Int,
        val front: Boolean,
    )

    internal companion object {
        const val MAX_RETRIES = 5
        const val RETRY_MS = 250L
        const val EMPTY_FRAME_LIMIT = 45

        fun displayDegrees(rotation: Int): Int = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        fun meteringFps(characteristics: CameraCharacteristics): Range<Int>? {
            val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: return null
            val lowest = ranges.minByOrNull { it.lower } ?: return null
            return ranges.filter { it.lower == lowest.lower }.maxByOrNull { it.upper }
        }

        const val SPOT_FRACTION = 0.22

        /** Active sensor area as half-angle tangents, long side first. */
        fun sensorField(characteristics: CameraCharacteristics): LensField? {
            val physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val focal = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull { it > 0f } ?: return null
            val pixels = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val active = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val widthMm = if (pixels != null && active != null && pixels.width > 0) {
                physical.width * active.width() / pixels.width
            } else {
                physical.width
            }
            val heightMm = if (pixels != null && active != null && pixels.height > 0) {
                physical.height * active.height() / pixels.height
            } else {
                physical.height
            }
            val long = maxOf(widthMm, heightMm) / 2.0 / focal
            val short = minOf(widthMm, heightMm) / 2.0 / focal
            return if (long > 0.0 && short > 0.0) LensField(long, short) else null
        }

        fun zoomControl(characteristics: CameraCharacteristics, zoom: Float): ZoomControl {
            val range = zoomRange(characteristics)
            if (zoom == 1f) return ZoomControl(null, null, 1f)
            if (range.lower < range.upper && zoom in range) return ZoomControl(zoom, null, zoom)
            if (zoom <= 1f) return ZoomControl(null, null, 1f)
            val area = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return ZoomControl(null, null, 1f)
            val most = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            val applied = zoom.coerceAtMost(most)
            if (applied <= 1f) return ZoomControl(null, null, 1f)
            val width = (area.width() / applied).roundToInt()
            val height = (area.height() / applied).roundToInt()
            val left = (area.width() - width) / 2
            val top = (area.height() - height) / 2
            return ZoomControl(null, Rect(left, top, left + width, top + height), 1f)
        }

        /** Zoom ratios the camera accepts; just 1 before Android 11 or when it reports none. */
        fun zoomRange(characteristics: CameraCharacteristics): Range<Float> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Range(1f, 1f)
            return characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) ?: Range(1f, 1f)
        }

        fun meteringRegion(characteristics: CameraCharacteristics, focalMm: Double?, zoom: Float): MeteringRectangle? {
            val maxRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
            if (maxRegions <= 0) return null
            val area = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val field = sensorField(characteristics)
            val (fractionWide, fractionHigh) = if (focalMm != null && field != null) {
                frameRegion(field, focalMm, sensorOrientation, zoom)
            } else {
                SPOT_FRACTION to SPOT_FRACTION
            }
            val width = (area.width() * fractionWide).toInt().coerceAtLeast(1)
            val height = (area.height() * fractionHigh).toInt().coerceAtLeast(1)
            val left = (area.centerX() - width / 2).coerceIn(area.left, (area.right - 1).coerceAtLeast(area.left))
            val top = (area.centerY() - height / 2).coerceIn(area.top, (area.bottom - 1).coerceAtLeast(area.top))
            val right = (left + width).coerceAtMost(area.right).coerceAtLeast(left + 1)
            val bottom = (top + height).coerceAtMost(area.bottom).coerceAtLeast(top + 1)
            return MeteringRectangle(left, top, right - left, bottom - top, MeteringRectangle.METERING_WEIGHT_MAX)
        }
    }
}
