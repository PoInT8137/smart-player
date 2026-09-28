package app.tvplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    /** Позиция, с которой имеет смысл продолжить, или null (почти начало / почти конец). */
    val resumePositionMs: Long?
        get() = positionMs.takeIf {
            it > MIN_RESUME_MS && (durationMs <= 0 || it < durationMs - END_MARGIN_MS)
        }

    companion object {
        private const val MIN_RESUME_MS = 30_000L
        private const val END_MARGIN_MS = 60_000L
    }
}

/** Позиции просмотра и список «Продолжить просмотр». Хранится в SharedPreferences как JSON. */
class PlaybackHistory(context: Context) {

    private val prefs = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)

    fun get(uri: String): HistoryEntry? = load().firstOrNull { it.uri == uri }

    fun save(entry: HistoryEntry) {
        val list = load().filterNot { it.uri == entry.uri }.toMutableList()
        list.add(0, entry)
        store(list.take(MAX_ENTRIES))
    }

    fun remove(uri: String) = store(load().filterNot { it.uri == uri })

    /** Недосмотренное — для главного экрана. */
    fun continueWatching(): List<HistoryEntry> = load().filter { it.resumePositionMs != null }

    private fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    uri = o.getString("uri"),
                    title = o.optString("title"),
                    positionMs = o.optLong("position"),
                    durationMs = o.optLong("duration"),
                    updatedAt = o.optLong("updated"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun store(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("title", it.title)
                    .put("position", it.positionMs)
                    .put("duration", it.durationMs)
                    .put("updated", it.updatedAt)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 100
    }
}
