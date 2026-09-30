package app.tvplayer.data

import android.content.Context
import app.tvplayer.torrserver.TorrServerClient

/** Настройки плеера, которые сохраняются между запусками. */
class PlayerSettings(context: Context) {

    private val prefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)

    /** Автоматически переключать частоту экрана под фильм. */
    var autoFrameRate: Boolean
        get() = prefs.getBoolean(KEY_AFR, true)
        set(value) = prefs.edit().putBoolean(KEY_AFR, value).apply()

    /** Адрес TorrServer: по умолчанию на этой же приставке, иначе — последний увиденный в ссылках Lampa. */
    var torrServerUrl: String
        get() = prefs.getString(KEY_TORRSERVER, null) ?: TorrServerClient.DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_TORRSERVER, value).apply()

    private companion object {
        const val KEY_AFR = "auto_frame_rate"
        const val KEY_TORRSERVER = "torrserver_url"
    }
}
