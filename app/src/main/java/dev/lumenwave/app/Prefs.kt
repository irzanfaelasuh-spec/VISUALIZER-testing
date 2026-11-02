package dev.lumenwave.app

import android.content.Context
import android.graphics.Color

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("lumenwave_settings", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()

    var visualizerEnabled: Boolean
        get() = sp.getBoolean("visualizer_enabled", true)
        set(v) = sp.edit().putBoolean("visualizer_enabled", v).apply()

    var colorMode: String
        get() = sp.getString("color_mode", "RAINBOW") ?: "RAINBOW"
        set(v) = sp.edit().putString("color_mode", v).apply()

    var barCount: Int
        get() = sp.getInt("bar_count", 36).coerceIn(12, 72)
        set(v) = sp.edit().putInt("bar_count", v.coerceIn(12, 72)).apply()

    var sensitivity: Float
        get() = sp.getFloat("sensitivity", 1.55f).coerceIn(0.5f, 3.5f)
        set(v) = sp.edit().putFloat("sensitivity", v.coerceIn(0.5f, 3.5f)).apply()

    var smoothing: Float
        get() = sp.getFloat("smoothing", 0.62f).coerceIn(0.1f, 0.95f)
        set(v) = sp.edit().putFloat("smoothing", v.coerceIn(0.1f, 0.95f)).apply()

    var barHeight: Int
        get() = sp.getInt("bar_height", 86).coerceIn(30, 100)
        set(v) = sp.edit().putInt("bar_height", v.coerceIn(30, 100)).apply()

    var barGap: Int
        get() = sp.getInt("bar_gap", 3).coerceIn(1, 9)
        set(v) = sp.edit().putInt("bar_gap", v.coerceIn(1, 9)).apply()

    var opacity: Int
        get() = sp.getInt("opacity", 86).coerceIn(35, 100)
        set(v) = sp.edit().putInt("opacity", v.coerceIn(35, 100)).apply()

    var glow: Boolean
        get() = sp.getBoolean("glow", true)
        set(v) = sp.edit().putBoolean("glow", v).apply()

    var peaks: Boolean
        get() = sp.getBoolean("peaks", true)
        set(v) = sp.edit().putBoolean("peaks", v).apply()

    var c1: Int
        get() = sp.getInt("color_1", Color.rgb(91, 226, 214))
        set(v) = sp.edit().putInt("color_1", v).apply()

    var c2: Int
        get() = sp.getInt("color_2", Color.rgb(120, 145, 255))
        set(v) = sp.edit().putInt("color_2", v).apply()

    var c3: Int
        get() = sp.getInt("color_3", Color.rgb(204, 125, 255))
        set(v) = sp.edit().putInt("color_3", v).apply()

    var windowWidthDp: Int
        get() = sp.getInt("window_width", 330).coerceIn(220, 620)
        set(v) = sp.edit().putInt("window_width", v.coerceIn(220, 620)).apply()

    var windowHeightDp: Int
        get() = sp.getInt("window_height", 174).coerceIn(110, 320)
        set(v) = sp.edit().putInt("window_height", v.coerceIn(110, 320)).apply()

    companion object {
        fun from(context: Context) = Prefs(context)
    }
}
