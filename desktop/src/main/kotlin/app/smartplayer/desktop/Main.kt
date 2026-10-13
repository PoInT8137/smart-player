package app.smartplayer.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.smartplayer.desktop.data.AppLog
import app.smartplayer.desktop.data.HistoryEntry
import app.smartplayer.desktop.data.PlaybackHistory
import app.smartplayer.desktop.player.MpcWebInterface
import app.smartplayer.desktop.player.MpvPlayer
import app.smartplayer.desktop.player.PlaybackProgress
import app.smartplayer.desktop.player.PlayItem
import app.smartplayer.desktop.ui.HomeScreen
import app.smartplayer.desktop.ui.SmartPlayerTheme
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.runBlocking
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Smart Player для Windows.
 *  - без аргументов — главное окно (история, TorrServer, открыть файл/ссылку);
 *  - `SmartPlayer.exe <ссылка или файл>` — сразу воспроизвести (для «Открыть с помощью» и внешних программ).
 */
fun main(args: Array<String>) {
    LaunchArgs.parse(args)?.let {
        playDirect(it)
        return
    }

    application {
        val state = rememberWindowState(width = 1320.dp, height = 820.dp, position = WindowPosition.PlatformDefault)
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Smart Player",
            icon = remember { appIcon() },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(960, 600)
                window.useDarkTitleBar()
            }
            val scope = rememberCoroutineScope()
            val model = remember { AppModel(scope) }
            LaunchedEffect(Unit) { model.refresh() }

            SmartPlayerTheme {
                HomeScreen(
                    model = model,
                    onOpenFile = { chooseVideoFile(window)?.let(model::playFile) },
                )
            }
        }
    }
}

/**
 * Воспроизведение без главного окна (запуск из Lampa или «Открыть с помощью»).
 * Прогресс сохраняется в общую историю; если Lampa ждёт веб-интерфейс MPC-HC — отвечаем ей.
 */
private fun playDirect(launch: LaunchArgs) = runBlocking {
    val history = PlaybackHistory()
    val file = File(launch.target)
    val item = if (file.isFile) PlayItem(file.absolutePath, file.nameWithoutExtension) else PlayItem(launch.target, launch.title)
    AppLog.write("Запуск: ${launch.target} старт=${launch.startPositionMs} порт=${launch.mpcWebPort} весь экран=${launch.fullscreen}")
    val web = launch.mpcWebPort?.let { port ->
        runCatching { MpcWebInterface(port) }
            .onSuccess { AppLog.write("Веб-интерфейс MPC-HC на порту $port") }
            .onFailure { AppLog.write("Не удалось открыть порт $port", it) }
            .getOrNull()
    }
    val save: (PlaybackProgress) -> Unit = { p ->
        history.save(HistoryEntry(p.item.url, p.item.title, p.positionMs, p.durationMs, System.currentTimeMillis()))
        web?.update(p)
    }
    try {
        val result = MpvPlayer.locate().play(
            items = listOf(item),
            startIndex = 0,
            // Позиция от Lampa важнее своей истории
            startPositionMs = launch.startPositionMs ?: history.get(item.url)?.resumePositionMs,
            fullscreen = launch.fullscreen,
            onProgress = save,
            onItemFinished = { history.remove(it.url) },
        )
        result?.let { if (it.completed) history.remove(it.item.url) else save(it) }
    } finally {
        web?.close()
    }
}

private fun appIcon(): BitmapPainter {
    val bytes = object {}.javaClass.getResourceAsStream("/icon.png")!!.use { it.readBytes() }
    return BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap())
}

private val VIDEO_EXTENSIONS = listOf("mkv", "mp4", "avi", "m4v", "mov", "ts", "m2ts", "webm", "wmv", "mpg", "mpeg", "flv", "vob", "m3u8")

/** Системный диалог Windows с фильтром видеофайлов. */
private fun chooseVideoFile(owner: java.awt.Window): File? {
    val dialog = FileDialog(owner as? Frame, "Открыть видео", FileDialog.LOAD).apply {
        file = VIDEO_EXTENSIONS.joinToString(";") { "*.$it" }
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int

    companion object {
        val INSTANCE: DwmApi? = runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
    }
}

/** Тёмный заголовок окна (Windows 10 20H1+/11) в цвет фона приложения. */
private fun java.awt.Window.useDarkTitleBar() {
    val api = DwmApi.INSTANCE ?: return
    runCatching {
        val hwnd = WinDef.HWND(Native.getComponentPointer(this) ?: Pointer.NULL)
        api.DwmSetWindowAttribute(hwnd, 20, IntByReference(1), 4) // DWMWA_USE_IMMERSIVE_DARK_MODE
        api.DwmSetWindowAttribute(hwnd, 35, IntByReference(0x000F0B0A), 4) // DWMWA_CAPTION_COLOR (BGR)
    }
}
