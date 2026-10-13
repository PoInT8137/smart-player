package app.smartplayer.desktop

import java.net.URLDecoder

/**
 * Аргументы запуска, как их передают внешние программы (в первую очередь Lampa для ПК):
 *  - обычный запуск: `SmartPlayer.exe <ссылка или файл>`;
 *  - стиль MPC-HC: `<ссылка> /webport 3999 /start <мс> /play /close /fullscreen`;
 *  - стиль VLC: `--start-time=<с> --fullscreen ... <ссылка>`.
 */
data class LaunchArgs(
    val target: String,
    val startPositionMs: Long? = null,
    val fullscreen: Boolean = false,
    /** Порт веб-интерфейса MPC-HC, который опрашивает Lampa. */
    val mpcWebPort: Int? = null,
) {
    /** Заголовок окна по ссылке: последний сегмент пути без кодирования. */
    val title: String
        get() {
            val raw = target.substringBefore('?').trimEnd('/').substringAfterLast('/').substringAfterLast('\\')
            val decoded = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(raw)
            return decoded.substringBeforeLast('.').ifBlank { target }
        }

    companion object {
        fun parse(args: Array<String>): LaunchArgs? {
            var target: String? = null
            var start: Long? = null
            var fullscreen = false
            var port: Int? = null
            var i = 0
            while (i < args.size) {
                val a = args[i]
                val next = args.getOrNull(i + 1)
                when {
                    a.equals("/start", true) -> { start = next?.toLongOrNull(); i++ }
                    a.equals("/webport", true) -> { port = next?.toIntOrNull(); i++ }
                    a.equals("/fullscreen", true) || a == "--fullscreen" -> fullscreen = true
                    a.startsWith("--start-time=") -> start = a.substringAfter('=').toDoubleOrNull()?.let { (it * 1000).toLong() }
                    a.startsWith("/") && !a.startsWith("//") && a.length < 16 && !a.contains('.') -> Unit // /play, /close и т.п.
                    a.startsWith("--") -> Unit // прочие ключи VLC
                    a.isNotBlank() && target == null -> target = a
                }
                i++
            }
            return target?.let { LaunchArgs(it, start?.takeIf { s -> s > 0 }, fullscreen, port) }
        }
    }
}
