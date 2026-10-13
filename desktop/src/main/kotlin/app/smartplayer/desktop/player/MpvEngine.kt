package app.smartplayer.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.smartplayer.desktop.data.AppLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/** Дорожка из `track-list` mpv. */
data class MpvTrack(
    val id: Int,
    val type: String, // video / audio / sub
    val lang: String?,
    val title: String?,
    val codec: String?,
    val channels: Int?,
    val selected: Boolean,
    val forced: Boolean,
)

/**
 * Встроенный mpv: рисует видео в окно [wid], интерфейс — наш (Compose поверх).
 *
 * Две IPC-сессии на один named pipe: в первой только читаем события и изменения свойств,
 * во второй — запрос-ответ на команды (события в ней отключены). Так обходим ограничение
 * Windows: чтение и запись в один синхронный pipe из разных потоков блокируют друг друга.
 *
 * Все Compose-состояния меняются в потоке Swing (EDT).
 */
class MpvEngine(
    private val mpvExe: File,
    private val wid: Long,
    val items: List<PlayItem>,
    private val startIndex: Int,
    private val startPositionMs: Long?,
) {
    // --- Состояние для интерфейса ---
    var paused by mutableStateOf(false); private set
    private var waitingForCache by mutableStateOf(false)
    private var seeking by mutableStateOf(false)
    private var started by mutableStateOf(false)
    /** Ждём данные: старт, перемотка или кончился кэш (медленная раздача). */
    val buffering: Boolean get() = !started || waitingForCache || seeking
    var positionMs by mutableLongStateOf(0); private set
    var durationMs by mutableLongStateOf(0); private set
    var bufferedMs by mutableLongStateOf(0); private set
    var mediaTitle by mutableStateOf<String?>(null); private set
    var itemIndex by mutableIntStateOf(startIndex); private set
    var tracks by mutableStateOf<List<MpvTrack>>(emptyList()); private set
    var speed by mutableFloatStateOf(1f); private set
    var volume by mutableIntStateOf(100); private set
    var videoWidth by mutableIntStateOf(0); private set
    var videoHeight by mutableIntStateOf(0); private set
    var videoCodec by mutableStateOf<String?>(null); private set
    var hdr by mutableStateOf<String?>(null); private set
    var fps by mutableFloatStateOf(0f); private set
    var path by mutableStateOf<String?>(null); private set
    var eofReached by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    val itemTitles: List<String> = items.map { it.title }
    val itemCount: Int get() = items.size
    val hasNext: Boolean get() = itemIndex < items.size - 1
    val nextTitle: String? get() = itemTitles.getOrNull(itemIndex + 1)
    val currentItem: PlayItem get() = items[itemIndex.coerceIn(items.indices)]

    /** Вызывается из любого потока при переходе к следующему файлу плейлиста. */
    var onItemChanged: ((from: Int, to: Int) -> Unit)? = null

    private lateinit var process: Process
    private var events: RandomAccessFile? = null
    private var commands: RandomAccessFile? = null
    private val requestId = AtomicInteger()
    private val commandLock = Any()

    val isAlive: Boolean get() = ::process.isInitialized && process.isAlive

    fun start() {
        val pipe = "smartplayer-${ProcessHandle.current().pid()}-${System.nanoTime()}"
        val args = buildList {
            add(mpvExe.absolutePath)
            add("--wid=$wid")
            add("--input-ipc-server=\\\\.\\pipe\\$pipe")
            // Свой интерфейс: никаких OSC/uosc и клавиш mpv — всё управление из Compose
            add("--load-scripts=no")
            add("--osc=no")
            add("--osd-level=0")
            add("--input-default-bindings=no")
            add("--input-vo-keyboard=no")
            add("--input-cursor=no")
            add("--cursor-autohide=no")
            add("--keep-open=yes")
            add("--force-window=yes")
            add("--playlist-start=$startIndex")
            items.forEachIndexed { i, item ->
                add("--{")
                add("--force-media-title=${item.title.replace('"', '\'')}")
                if (i == startIndex && startPositionMs != null && startPositionMs > 0) add("--start=${startPositionMs / 1000.0}")
                add(item.url)
                add("--}")
            }
        }
        process = ProcessBuilder(args)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        Thread({ connectAndListen(pipe) }, "mpv-events").apply { isDaemon = true }.start()
    }

    private fun connectAndListen(pipe: String) {
        fun open(): RandomAccessFile? {
            repeat(150) {
                if (!process.isAlive) return null
                runCatching { return RandomAccessFile("\\\\.\\pipe\\$pipe", "rw") }
                Thread.sleep(100)
            }
            return null
        }
        val ev = open() ?: return onUi { error = "Не удалось запустить видеодвижок" }
        val cmd = open() ?: return onUi { error = "Не удалось подключиться к видеодвижку" }
        events = ev
        commands = cmd
        // Командная сессия: события не нужны, только ответы
        synchronized(commandLock) { write(cmd, buildJsonObject { put("command", buildJsonArray { add(JsonPrimitive("disable_event")); add(JsonPrimitive("all")) }) }) ; readLine(cmd) }

        OBSERVED.forEachIndexed { i, name ->
            write(ev, buildJsonObject {
                put("command", buildJsonArray { add(JsonPrimitive("observe_property")); add(JsonPrimitive(i + 1)); add(JsonPrimitive(name)) })
            })
        }
        while (true) {
            val line = runCatching { readLine(ev) }.getOrNull() ?: break
            val o = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            when ((o["event"] as? JsonPrimitive)?.contentOrNull) {
                "property-change" -> {
                    val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: continue
                    val data = o["data"] ?: JsonNull
                    onUi { apply(name, data) }
                }
            }
        }
    }

    private fun apply(name: String, data: JsonElement) {
        val p = data as? JsonPrimitive
        when (name) {
            "pause" -> paused = p?.booleanOrNull ?: false
            "paused-for-cache" -> waitingForCache = p?.booleanOrNull ?: false
            "time-pos" -> p?.doubleOrNull?.let {
                positionMs = (it * 1000).toLong()
                if (it > 0) started = true
            }
            "duration" -> durationMs = ((p?.doubleOrNull ?: 0.0) * 1000).toLong()
            "demuxer-cache-time" -> bufferedMs = ((p?.doubleOrNull ?: 0.0) * 1000).toLong()
            "media-title" -> mediaTitle = p?.contentOrNull
            "playlist-pos" -> p?.intOrNull?.takeIf { it >= 0 }?.let { pos ->
                if (pos != itemIndex) {
                    val from = itemIndex
                    itemIndex = pos
                    eofReached = false
                    started = false
                    onItemChanged?.invoke(from, pos)
                }
            }
            "track-list" -> tracks = (data as? JsonArray)?.mapNotNull { parseTrack(it) }.orEmpty()
            "speed" -> speed = (p?.doubleOrNull ?: 1.0).toFloat()
            "volume" -> volume = (p?.doubleOrNull ?: 100.0).toInt()
            "video-params" -> (data as? JsonObject).let { vp ->
                videoWidth = (vp?.get("w") as? JsonPrimitive)?.intOrNull ?: 0
                videoHeight = (vp?.get("h") as? JsonPrimitive)?.intOrNull ?: 0
                hdr = when ((vp?.get("gamma") as? JsonPrimitive)?.contentOrNull) {
                    "pq" -> "HDR10"
                    "hlg" -> "HLG"
                    else -> null
                }
            }
            "video-codec" -> videoCodec = p?.contentOrNull
            "container-fps" -> fps = (p?.doubleOrNull ?: 0.0).toFloat()
            "path" -> path = p?.contentOrNull
            "eof-reached" -> eofReached = p?.booleanOrNull ?: false
            "seeking" -> seeking = p?.booleanOrNull ?: false
        }
    }

    private fun parseTrack(el: JsonElement): MpvTrack? {
        val o = el as? JsonObject ?: return null
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        fun b(k: String) = (o[k] as? JsonPrimitive)?.booleanOrNull ?: false
        return MpvTrack(
            id = (o["id"] as? JsonPrimitive)?.intOrNull ?: return null,
            type = s("type") ?: return null,
            lang = s("lang"),
            title = s("title"),
            codec = s("codec"),
            channels = (o["demux-channel-count"] as? JsonPrimitive)?.intOrNull,
            selected = b("selected"),
            forced = b("forced"),
        )
    }

    // --- Команды ---

    fun command(vararg args: Any) {
        val cmd = commands ?: return
        val id = requestId.incrementAndGet()
        val json = buildJsonObject {
            put("command", buildJsonArray {
                args.forEach {
                    when (it) {
                        is Number -> add(JsonPrimitive(it))
                        is Boolean -> add(JsonPrimitive(it))
                        else -> add(JsonPrimitive(it.toString()))
                    }
                }
            })
            put("request_id", id)
        }
        // Запрос-ответ в отдельном потоке, чтобы не держать интерфейс
        Thread {
            synchronized(commandLock) {
                runCatching {
                    write(cmd, json)
                    while (true) {
                        val line = readLine(cmd) ?: break
                        val o = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                        if ((o["request_id"] as? JsonPrimitive)?.longOrNull == id.toLong()) break
                    }
                }.onFailure { AppLog.write("mpv: команда ${args.joinToString(" ")} не выполнена", it) }
            }
        }.apply { isDaemon = true }.start()
    }

    fun togglePause() = command("set", "pause", if (paused) "no" else "yes")
    fun pausePlayback(value: Boolean) = command("set", "pause", if (value) "yes" else "no")
    fun seekTo(ms: Long) = command("seek", ms / 1000.0, "absolute+exact")
    fun setAudio(id: Int) = command("set", "aid", id.toString())
    fun setSubtitle(id: Int?) = command("set", "sid", id?.toString() ?: "no")
    fun changeSpeed(value: Float) = command("set", "speed", value.toString())
    fun addVolume(delta: Int) = command("add", "volume", delta)
    fun playIndex(index: Int) = command("playlist-play-index", index)
    fun next() = command("playlist-next")

    /** Режим кадра: вписать / заполнить с обрезкой / растянуть. */
    fun setResizeMode(mode: ResizeMode) {
        when (mode) {
            ResizeMode.Fit -> { command("set", "keepaspect", "yes"); command("set", "panscan", "0") }
            ResizeMode.Zoom -> { command("set", "keepaspect", "yes"); command("set", "panscan", "1") }
            ResizeMode.Stretch -> { command("set", "panscan", "0"); command("set", "keepaspect", "no") }
        }
    }

    fun quit() {
        runCatching { commands?.let { write(it, buildJsonObject { put("command", buildJsonArray { add(JsonPrimitive("quit")) }) }) } }
        if (::process.isInitialized && !process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroy()
        runCatching { events?.close() }
        runCatching { commands?.close() }
    }

    private fun write(f: RandomAccessFile, json: JsonObject) = f.write((json.toString() + "\n").toByteArray(Charsets.UTF_8))

    private fun readLine(f: RandomAccessFile): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = f.read()
            if (b < 0) return if (bytes.size() > 0) bytes.toString(Charsets.UTF_8) else null
            if (b == '\n'.code) return bytes.toString(Charsets.UTF_8)
            bytes.write(b)
        }
    }

    private inline fun onUi(crossinline block: () -> Unit) = SwingUtilities.invokeLater { block() }

    companion object {
        private val OBSERVED = listOf(
            "pause", "paused-for-cache", "time-pos", "duration", "demuxer-cache-time", "media-title",
            "playlist-pos", "track-list", "speed", "volume", "video-params", "video-codec", "container-fps",
            "path", "eof-reached", "seeking",
        )
    }
}

enum class ResizeMode(val title: String) { Fit("Вписать"), Zoom("Заполнить (обрезать края)"), Stretch("Растянуть") }
