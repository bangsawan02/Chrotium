package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window

/**
 * GPU Frame Buffer Capture (PixelCopy):
 * Mengambil snapshot hasil render langsung dari GPU Surface secara asynchronous.
 * Metode ini tidak membebankan Main Thread saat membuat gambar pratinjau (thumbnail) pada tampilan tab switcher.
 */
object PixelCopyUtils {

    fun captureGpuFrame(
        view: View,
        window: Window?,
        onCaptured: (Bitmap?) -> Unit
    ) {
        if (view.width <= 0 || view.height <= 0) {
            onCaptured(null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null) {
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val location = IntArray(2)
                view.getLocationInWindow(location)
                val rect = Rect(
                    location[0],
                    location[1],
                    location[0] + view.width,
                    location[1] + view.height
                )
                PixelCopy.request(
                    window,
                    rect,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            onCaptured(bitmap)
                        } else {
                            // Fallback jika Surface GPU tidak dapat diakses langsung
                            onCaptured(captureSoftwareFallback(view))
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                onCaptured(captureSoftwareFallback(view))
            }
        } else {
            onCaptured(captureSoftwareFallback(view))
        }
    }

    private fun captureSoftwareFallback(view: View): Bitmap? {
        return try {
            if (view.width <= 0 || view.height <= 0) return null
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
