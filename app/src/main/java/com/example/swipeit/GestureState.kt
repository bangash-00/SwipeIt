package com.example.swipeit

import kotlinx.coroutines.flow.MutableStateFlow

object GestureState {
    data class Snapshot(
        val running: Boolean = false,
        val enabled: Boolean = true,
        val yaw: Float = 0f,
        val pitch: Float = 0f,
        val blink: Boolean = false,
        val lastAction: String = "-",
        val handSeen: Boolean = false,
        val handMove: String = "-",
        val error: String = ""
    )

    val flow = MutableStateFlow(Snapshot())
}