package com.example.engine

import android.view.Choreographer
import android.view.View

/**
 * V-Sync & Frame Rate Sync (Choreographer):
 * Menyelaraskan siklus gambar WebView dengan refresh rate panel layar (seperti 60Hz atau 120Hz)
 * untuk mengeliminasi screen tearing dan frame drop.
 */
class VSyncFrameSynchronizer(private val view: View) : Choreographer.FrameCallback {
    private var isSubscribed = false

    fun start() {
        if (!isSubscribed) {
            isSubscribed = true
            try {
                Choreographer.getInstance().postFrameCallback(this)
            } catch (e: Exception) {
                isSubscribed = false
            }
        }
    }

    fun stop() {
        if (isSubscribed) {
            isSubscribed = false
            try {
                Choreographer.getInstance().removeFrameCallback(this)
            } catch (_: Exception) {}
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isSubscribed) return
        if (view.isShown && view.isDirty) {
            view.invalidate()
        }
        try {
            Choreographer.getInstance().postFrameCallback(this)
        } catch (_: Exception) {
            isSubscribed = false
        }
    }
}
