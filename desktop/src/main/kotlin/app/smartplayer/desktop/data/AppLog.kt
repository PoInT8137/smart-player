package app.smartplayer.desktop.data

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Простой журнал в %APPDATA%\SmartPlayer\log.txt — для диагностики запуска из Lampa. */
object AppLog {
    private val file = File(AppDirs.root, "log.txt")
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Synchronized
    fun write(message: String, error: Throwable? = null) {
        runCatching {
            // Не даём журналу расти бесконечно
            if (file.length() > 512 * 1024) file.writeText("")
            file.appendText("${LocalDateTime.now().format(time)}  $message${error?.let { " — $it" } ?: ""}\n")
        }
    }
}
