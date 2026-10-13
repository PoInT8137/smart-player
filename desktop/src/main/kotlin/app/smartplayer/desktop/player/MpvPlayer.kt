package app.smartplayer.desktop.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger

data class PlayItem(val url: String, val title: String)

/** Что было на экране, когда окно mpv закрыли: для истории просмотра. */
data class PlaybackProgress(
    val item: PlayItem,
    val positionMs: Long,
    val durationMs: Long,
    val paused: Boolean = false,
) {
    /** Досмотрено (последние 20 секунд — титры). */
    val completed: Boolean get() = durationMs > 0 && positionMs >= durationMs - 20_000
}

/**
 * Запускает mpv отдельным окном и следит за ним через JSON IPC (named pipe Windows).
 *
 * Читать и писать в синхронный pipe из разных потоков на Windows нельзя (чтение
 * блокирует запись), поэтому опрос строго последовательный: запрос → ответ.
 */
class MpvPlayer(private val mpvExe: File) {

    private val counter = AtomicInteger()

    val isAvailable: Boolean get() = mpvExe.isFile

    /**
     * Воспроизводит плейлист и возвращается, когда пользователь закрыл окно.
     * [onItemFinished] вызывается для серий, досмотренных до конца (их убираем из истории),
     * [onProgress] — периодически, чтобы прогресс сохранялся даже при аварийном закрытии.
     */
    suspend fun play(
        items: List<PlayItem>,
        startIndex: Int,
        startPositionMs: Long?,
        fullscreen: Boolean = false,
        onProgress: (PlaybackProgress) -> Unit,
        onItemFinished: (PlayItem) -> Unit,
    ): PlaybackProgress? = withContext(Dispatchers.IO) {
        val pipeName = "smartplayer-${ProcessHandle.current().pid()}-${counter.incrementAndGet()}"
        val args = buildList {
            add(mpvExe.absolutePath)
            add("--input-ipc-server=\\\\.\\pipe\\$pipeName")
            add("--playlist-start=$startIndex")
            if (fullscreen) add("--fullscreen")
            items.forEachIndexed { i, item ->
                // Опции «на файл»: своё название и, для первой серии, позиция старта
                add("--{")
                add("--force-media-title=${item.title.replace('"', '\'')}")
                if (i == startIndex && startPositionMs != null && startPositionMs > 0) {
                    add("--start=${startPositionMs / 1000.0}")
                }
                add(item.url)
                add("--}")
            }
        }
        val process = ProcessBuilder(args)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        var last: PlaybackProgress? = null
        var currentIndex = startIndex
        val pipe = connect(pipeName, process)
        try {
            var requestId = 0
            fun query(property: String): JsonPrimitive? {
                val id = ++requestId
                pipe?.write("""{"command":["get_property","$property"],"request_id":$id}""".toByteArray() + '\n'.code.toByte())
                // Пропускаем события mpv, пока не придёт ответ на наш запрос
                while (true) {
                    val line = pipe?.readUtf8Line() ?: return null
                    val o = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    if ((o["request_id"] as? JsonPrimitive)?.longOrNull == id.toLong()) {
                        return o["data"] as? JsonPrimitive
                    }
                }
            }

            while (isActive && process.isAlive && pipe != null) {
                val result = runCatching {
                    val pos = query("playlist-pos")?.longOrNull?.toInt() ?: currentIndex
                    val time = query("time-pos")?.doubleOrNull
                    val duration = query("duration")?.doubleOrNull
                    val paused = query("pause")?.booleanOrNull ?: false
                    Polled(pos, time, duration, paused)
                }.getOrNull() ?: break

                val (pos, time, duration, paused) = result
                if (pos != currentIndex && pos in items.indices) {
                    // Перешли к следующей серии — предыдущая досмотрена
                    if (pos > currentIndex) onItemFinished(items[currentIndex])
                    currentIndex = pos
                }
                if (time != null && currentIndex in items.indices) {
                    last = PlaybackProgress(items[currentIndex], (time * 1000).toLong(), ((duration ?: 0.0) * 1000).toLong(), paused)
                        .also(onProgress)
                }
                delay(1_000)
            }
        } finally {
            runCatching { pipe?.close() }
        }
        process.waitFor()
        last
    }

    private data class Polled(val index: Int, val time: Double?, val duration: Double?, val paused: Boolean)

    /** Ждём, пока mpv создаст pipe (обычно доли секунды). */
    private suspend fun connect(name: String, process: Process): RandomAccessFile? {
        repeat(100) {
            if (!process.isAlive) return null
            runCatching { return RandomAccessFile("\\\\.\\pipe\\$name", "rw") }
            delay(100)
        }
        return null
    }

    private fun RandomAccessFile.readUtf8Line(): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val b = read()
            if (b < 0) return if (bytes.size() > 0) bytes.toString(Charsets.UTF_8) else null
            if (b == '\n'.code) return bytes.toString(Charsets.UTF_8)
            bytes.write(b)
        }
    }

    companion object {
        /** mpv лежит рядом с приложением (ресурсы jpackage) или в папке разработки. */
        fun locate(): MpvPlayer {
            val candidates = listOfNotNull(
                System.getProperty("compose.application.resources.dir")?.let { File(it, "mpv/mpv.exe") },
                File("desktop/resources/windows/mpv/mpv.exe"),
                File("resources/windows/mpv/mpv.exe"),
            )
            return MpvPlayer(candidates.firstOrNull { it.isFile } ?: candidates.first())
        }
    }
}
