package com.example.swipeit

import kotlin.math.abs

class GestureEngine(private val cfg: Config = Config()) {

    data class Config(
        val yawThreshold: Float = 20f,
        val pitchThreshold: Float = 8f,
        val yawNeutral: Float = 8f,
        val pitchNeutral: Float = 4f,
        val cooldownMs: Long = 500L,
        val settleMs: Long = 350L,          // head must rest in neutral this long to re-arm
        val blinkThreshold: Float = 0.55f,
        val minBlinkMs: Long = 150L,
        val maxBlinkMs: Long = 700L,
        val doubleBlinkWindowMs: Long = 380L,
        val baselineAlpha: Float = 0.01f
    )

    enum class Action { SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN, DOUBLE_TAP }

    private var armed = true
    private var lastFire = 0L
    private var neutralSince = 0L
    private var baseReady = false
    private var baseYaw = 0f
    private var basePitch = 0f

    private var eyesClosed = false
    private var closedSince = 0L
    private var pendingBlinkAt = 0L

    fun update(
        yaw: Float,
        pitch: Float,
        blinkLeft: Float,
        blinkRight: Float,
        now: Long,
        invertH: Boolean,
        invertV: Boolean,
        headEnabled: Boolean,
        eyeEnabled: Boolean
    ): Action? {

        // ---------- blinks ----------
        if (!eyeEnabled) {
            eyesClosed = false
            pendingBlinkAt = 0L
        } else {
            val closed = blinkLeft > cfg.blinkThreshold && blinkRight > cfg.blinkThreshold
            if (closed && !eyesClosed) {
                eyesClosed = true
                closedSince = now
            } else if (!closed && eyesClosed) {
                eyesClosed = false
                val duration = now - closedSince
                if (duration in cfg.minBlinkMs..cfg.maxBlinkMs) {
                    if (pendingBlinkAt != 0L && closedSince - pendingBlinkAt <= cfg.doubleBlinkWindowMs) {
                        pendingBlinkAt = 0L
                        return Action.DOUBLE_TAP
                    }
                    pendingBlinkAt = now
                }
            }
            if (pendingBlinkAt != 0L && !eyesClosed && now - pendingBlinkAt > cfg.doubleBlinkWindowMs) {
                pendingBlinkAt = 0L
                return Action.SWIPE_UP
            }
        }

        // ---------- head ----------
        if (!headEnabled) return null

        if (!baseReady) {
            baseYaw = yaw; basePitch = pitch; baseReady = true
        }
        val ry = yaw - baseYaw
        val rp = pitch - basePitch

        val inNeutral = abs(ry) < cfg.yawNeutral && abs(rp) < cfg.pitchNeutral
        if (inNeutral) {
            if (neutralSince == 0L) neutralSince = now
            // re-arm only after the head has RESTED in neutral (ignores overshoot on the way back)
            if (!armed && now - neutralSince >= cfg.settleMs) armed = true
            if (armed) {
                baseYaw += cfg.baselineAlpha * ry
                basePitch += cfg.baselineAlpha * rp
            }
            return null
        }
        neutralSince = 0L   // left the neutral zone, restart the rest timer

        if (!armed || now - lastFire < cfg.cooldownMs) return null

        val hy = if (invertH) -ry else ry
        val vp = if (invertV) -rp else rp
        val yawRatio = abs(hy) / cfg.yawThreshold
        val pitchRatio = abs(vp) / cfg.pitchThreshold

        val action = when {
            yawRatio >= 1f && yawRatio >= pitchRatio ->
                if (hy > 0) Action.SWIPE_LEFT else Action.SWIPE_RIGHT
            pitchRatio >= 1f ->
                if (vp > 0) Action.SWIPE_UP else Action.SWIPE_DOWN
            else -> null
        }
        if (action != null) {
            armed = false
            lastFire = now
        }
        return action
    }
}