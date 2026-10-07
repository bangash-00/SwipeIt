package com.example.swipeit

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

enum class SwipeDir { UP, DOWN, LEFT, RIGHT }

class SwipeService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SwipeService? = null

        // Vertical swipes, as a fraction of screen height (0 = top, 1 = bottom).
        // UP starts higher so it avoids the carousel dots and bottom controls.
        private const val UP_START = 0.58f
        private const val UP_END = 0.18f
        private const val DOWN_START = 0.28f
        private const val DOWN_END = 0.68f

        // Horizontal swipes: height of the swipe line and start/end as a fraction of width
        private const val SIDE_Y = 0.42f
        private const val SIDE_FAR = 0.85f
        private const val SIDE_NEAR = 0.15f

        private const val SWIPE_MS = 120L
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun swipe(dir: SwipeDir) {
        main.post {
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()
            val p = Path()
            when (dir) {
                SwipeDir.UP -> { p.moveTo(w / 2, h * UP_START); p.lineTo(w / 2, h * UP_END) }
                SwipeDir.DOWN -> { p.moveTo(w / 2, h * DOWN_START); p.lineTo(w / 2, h * DOWN_END) }
                SwipeDir.LEFT -> { p.moveTo(w * SIDE_FAR, h * SIDE_Y); p.lineTo(w * SIDE_NEAR, h * SIDE_Y) }
                SwipeDir.RIGHT -> { p.moveTo(w * SIDE_NEAR, h * SIDE_Y); p.lineTo(w * SIDE_FAR, h * SIDE_Y) }
            }
            val stroke = GestureDescription.StrokeDescription(p, 0, SWIPE_MS)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
    }

    /** Double tap in the middle of the screen (the "like" gesture in video apps). */
    fun doubleTap() {
        main.post {
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()
            fun tapPath() = Path().apply { moveTo(w / 2, h * 0.45f) }
            val first = GestureDescription.StrokeDescription(tapPath(), 0, 40)
            val second = GestureDescription.StrokeDescription(tapPath(), 120, 40)
            dispatchGesture(
                GestureDescription.Builder().addStroke(first).addStroke(second).build(),
                null, null
            )
        }
    }
}