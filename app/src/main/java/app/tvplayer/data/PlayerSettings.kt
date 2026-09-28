package app.tvplayer.data

import android.content.Context

/** Настройки плеера, которые сохраняются между запусками. */
class PlayerSettings(context: Context) {

    private val prefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)

    /** Автоматически переключать частоту экрана под фильм. */
    var autoFrameRate: Boolean
        get() = prefs.getBoolean(KEY_AFR, true)
        set(value) = prefs.edit().putBoolean(KEY_AFR, value).apply()

    private companion object {
        const val KEY_AFR = "auto_frame_rate"
    }
}
