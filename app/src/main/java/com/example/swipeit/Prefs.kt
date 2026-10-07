package com.example.swipeit

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("gesture_prefs", Context.MODE_PRIVATE)

    var invertHorizontal: Boolean
        get() = sp.getBoolean("invertH", false)
        set(v) = sp.edit().putBoolean("invertH", v).apply()

    var invertVertical: Boolean
        get() = sp.getBoolean("invertV", false)
        set(v) = sp.edit().putBoolean("invertV", v).apply()

    var headEnabled: Boolean
        get() = sp.getBoolean("head", true)
        set(v) = sp.edit().putBoolean("head", v).apply()

    var eyeEnabled: Boolean
        get() = sp.getBoolean("eye", true)
        set(v) = sp.edit().putBoolean("eye", v).apply()

    var handEnabled: Boolean
        get() = sp.getBoolean("hand", true)
        set(v) = sp.edit().putBoolean("hand", v).apply()
}