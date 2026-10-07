package com.example.swipeit

import kotlin.math.abs

/** Detects a hand wave from palm-center positions (normalized 0..1). */
class HandSwipeDetector(private val cfg: Config = Config()) {

    data class Config(
        val windowMs: Long = 500L,     // wave must complete within this time
        val minTravel: Float = 0.15f,  // fraction of the frame the palm must travel
        val dominance: Float = 1.4f,   // main axis must beat the other axis by this factor
        val cooldownMs: Long = 800L,   // also stops the return motion from firing
        val minPoints: Int = 2,
        val flipX: Boolean = true      // set false if left/right is reversed
    )

    private class P(val t: Long, val x: Float, val y: Float)

    private val pts = ArrayDeque<P>()
    private var lastFire = 0L

    @Volatile var lastDx = 0f
    @Volatile var lastDy = 0f

    fun update(x: Float, y: Float, now: Long): SwipeDir? {
        pts.addLast(P(now, x, y))
        while (pts.isNotEmpty() && now - pts.first().t > cfg.windowMs) pts.removeFirst()
        if (pts.size < cfg.minPoints) return null

        val first = pts.first()
        val last = pts.last()
        val dx = (last.x - first.x).let { if (cfg.flipX) -it else it }
        val dy = last.y - first.y   // image y grows downward
        lastDx = dx
        lastDy = dy

        if (now - lastFire < cfg.cooldownMs) return null

        val dir = when {
            abs(dx) >= cfg.minTravel && abs(dx) > cfg.dominance * abs(dy) ->
                if (dx > 0) SwipeDir.RIGHT else SwipeDir.LEFT
            abs(dy) >= cfg.minTravel && abs(dy) > cfg.dominance * abs(dx) ->
                if (dy < 0) SwipeDir.UP else SwipeDir.DOWN
            else -> null
        }
        if (dir != null) {
            lastFire = now
            pts.clear()
        }
        return dir
    }
}