/**
 * Still copy of a camera preview drawn into a SurfaceView, for holding a reading.
 *
 * SceneView renders the AR camera into a SurfaceView, which the window cannot
 * snapshot, so the frame is read back with PixelCopy and drawn over the live view.
 */
package io.github.samxu29.flashcal.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The current frame of the first visible SurfaceView under this view's root, or null. */
internal suspend fun View.copySurfaceFrame(): Bitmap? {
    val surface = rootView.findSurfaceView() ?: return null
    if (surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) return null
    val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                surface,
                bitmap,
                { result -> continuation.resume(if (result == PixelCopy.SUCCESS) bitmap else null) },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            continuation.resume(null)
        }
    }
}

private fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView && isShown) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findSurfaceView()?.let { return it }
    }
    return null
}
