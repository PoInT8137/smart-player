package app.smartplayer.desktop.data

import app.tvplayer.torrserver.TorrServerClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/** Папка данных: %APPDATA%\SmartPlayer. */
object AppDirs {
    val root: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "SmartPlayer").apply { mkdirs() }
}

data class HistoryEntry(
    val uri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Есть смысл продолжать: не самое начало и не самый конец. */
    val resumePositionMs: Long?
        get() = positionMs.takeIf { it > 30_000 && (durationMs <= 0 || it < durationMs - 60_000) }
}

/** История просмотра в JSON-файле. Те же правила, что в TV-версии. */
class PlaybackHistory(private val file: File = File(AppDirs.root, "history.json")) {

    fun get(uri: String): HistoryEntry? = load().firstOrNull { it.uri == uri }

    fun save(entry: HistoryEntry) = store((listOf(entry) + load().filterNot { it.uri == entry.uri }).take(100))

    fun remove(uri: String) = store(load().filterNot { it.uri == uri })

    fun continueWatching(): List<HistoryEntry> = load().filter { it.resumePositionMs != null }

    @Synchronized
    private fun load(): List<HistoryEntry> = runCatching {
        Json.parseToJsonElement(file.readText()).let { it as JsonArray }.map { el ->
            val o = el.jsonObject
            HistoryEntry(o.str("uri"), o.str("title"), o.long("position"), o.long("duration"), o.long("updated"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun store(list: List<HistoryEntry>) {
        val json = buildJsonArray {
            list.forEach {
                add(buildJsonObject {
                    put("uri", it.uri)
                    put("title", it.title)
                    put("position", it.positionMs)
                    put("duration", it.durationMs)
                    put("updated", it.updatedAt)
                })
            }
        }
        file.writeText(json.toString())
    }
}

/** Настройки: адрес TorrServer и к какому лаунчеру уже подключена Lampa. */
class Settings(private val file: File = File(AppDirs.root, "settings.json")) {

    var torrServerUrl: String
        get() = read().str("torrserver").ifBlank { TorrServerClient.DEFAULT_URL }
        set(value) = update("torrserver", value.trim().trimEnd('/'))

    /** Путь плеера, который уже прописан в Lampa — чтобы не переписывать её настройки при каждом запуске. */
    var lampaLinkedPath: String?
        get() = read().str("lampaLinked").ifBlank { null }
        set(value) = update("lampaLinked", value.orEmpty())

    private fun read(): JsonObject = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrDefault(JsonObject(emptyMap()))

    private fun update(key: String, value: String) = file.writeText(JsonObject(read() + (key to JsonPrimitive(value))).toString())
}

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
