package app.smartplayer.desktop.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.awt.ComposeDialog
import app.smartplayer.desktop.data.AppLog
import app.smartplayer.desktop.ui.SmartPlayerTheme
import app.smartplayer.desktop.ui.player.PlayerScreen
import com.sun.jna.Native
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Canvas
import java.awt.Color
import java.awt.Dialog
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame

data class PlayItem(val url: String, val title: String)

/** Что было на экране, когда плеер закрыли: для истории просмотра. */
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
 * Окно воспроизведения: внизу — окно, в которое рисует mpv (`--wid`), поверх — прозрачный
 * слой Compose с интерфейсом Smart Player. Слой следует за окном видео при перемещении.
 */
class PlayerWindow(private val mpvExe: File = locateMpv()) {

    val isAvailable: Boolean get() = mpvExe.isFile

    suspend fun play(
        items: List<PlayItem>,
        startIndex: Int,
        startPositionMs: Long?,
        fullscreen: Boolean = false,
        onProgress: (PlaybackProgress) -> Unit,
        onItemFinished: (PlayItem) -> Unit,
    ): PlaybackProgress? {
        val done = CompletableDeferred<PlaybackProgress?>()
        val scope = CoroutineScope(Dispatchers.Swing)

        withContext(Dispatchers.Swing) {
            val first = items[startIndex]
            val frame = JFrame("${first.title} — Smart Player").apply {
                isUndecorated = true
                background = Color.BLACK
                contentPane.background = Color.BLACK
                defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
                // Клавиатура — у слоя с интерфейсом; окно видео фокус не забирает
                focusableWindowState = false
                runCatching { iconImage = ImageIO.read(PlayerWindow::class.java.getResource("/icon.png")) }
            }
            val canvas = Canvas().apply {
                background = Color.BLACK
                isFocusable = false
            }
            frame.contentPane.add(canvas)

            val screen = currentScreenBounds()
            var windowed = Rectangle(
                screen.x + screen.width * 15 / 100, screen.y + screen.height * 12 / 100,
                screen.width * 70 / 100, screen.height * 70 / 100,
            )
            val isFullscreen = mutableStateOf(fullscreen)
            frame.bounds = if (fullscreen) screen else windowed
            frame.isVisible = true

            val engine = MpvEngine(mpvExe, Native.getComponentID(canvas), items, startIndex, startPositionMs)
            engine.onItemChanged = { from, to -> if (to > from) onItemFinished(items[from]) }
            engine.start()

            val overlay = ComposeDialog(frame, Dialog.ModalityType.MODELESS).apply {
                isUndecorated = true
                isTransparent = true
            }

            fun syncOverlay() {
                if (canvas.isShowing) overlay.bounds = Rectangle(canvas.locationOnScreen, canvas.size)
            }

            fun progress() = PlaybackProgress(engine.currentItem, engine.positionMs, engine.durationMs, engine.paused)

            fun finish() {
                if (done.isCompleted) return
                val result = progress().takeIf { engine.positionMs > 0 }
                scope.cancel()
                Thread { engine.quit() }.start()
                overlay.dispose()
                frame.dispose()
                done.complete(result)
            }

            fun toggleFullscreen() {
                val goFull = !isFullscreen.value
                if (!goFull) frame.bounds = windowed else {
                    windowed = frame.bounds
                    frame.bounds = currentScreenBounds(frame.bounds)
                }
                isFullscreen.value = goFull
            }

            overlay.setContent {
                SmartPlayerTheme {
                  // Интерфейс рассчитан на «телевизорные» ~1280dp по ширине: в полном экране
                  // он такой же крупный, как в Smart Player для TV, в окне — не меньше обычного
                  BoxWithConstraints(Modifier.fillMaxSize()) {
                    val base = LocalDensity.current
                    val scaled = Density(maxOf(base.density, constraints.maxWidth / 1280f), base.fontScale)
                    CompositionLocalProvider(LocalDensity provides scaled) {
                    PlayerScreen(
                        engine = engine,
                        fullscreen = isFullscreen.value,
                        onToggleFullscreen = ::toggleFullscreen,
                        onDragWindow = { dx, dy ->
                            if (!isFullscreen.value) {
                                // Compose даёт физические пиксели, AWT — логические (масштаб Windows)
                                val scale = frame.graphicsConfiguration.defaultTransform.scaleX
                                frame.setLocation(frame.x + (dx / scale).toInt(), frame.y + (dy / scale).toInt())
                            }
                        },
                        onExit = ::finish,
                    )
                    }
                  }
                }
            }

            frame.addComponentListener(object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = syncOverlay()
                override fun componentResized(e: ComponentEvent) = syncOverlay()
            })
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) = finish()
                override fun windowActivated(e: WindowEvent) {
                    // Клик по панели задач активирует окно видео — фокус отдаём слою с интерфейсом
                    if (overlay.isVisible) overlay.toFront()
                }
            })

            syncOverlay()
            overlay.isVisible = true
            overlay.toFront()
            overlay.requestFocus()
            // Windows не всегда отдаёт фокус новому окну сразу — повторяем, когда слой отрисуется
            scope.launch {
                delay(400)
                overlay.toFront()
                overlay.requestFocus()
            }

            scope.launch {
                var seenAlive = false
                while (!done.isCompleted) {
                    delay(1_000)
                    if (engine.positionMs > 0) onProgress(progress())
                    // Конец последнего файла плейлиста — закрываемся сами
                    if (engine.eofReached && !engine.hasNext) finish()
                    if (engine.isAlive) seenAlive = true else if (seenAlive) {
                        AppLog.write("mpv завершился сам")
                        finish()
                    }
                }
            }
        }
        return done.await()
    }

    companion object {
        /** mpv лежит рядом с приложением (ресурсы jpackage) или в папке разработки. */
        fun locateMpv(): File {
            val candidates = listOfNotNull(
                System.getProperty("compose.application.resources.dir")?.let { File(it, "mpv/mpv.exe") },
                File("desktop/resources/windows/mpv/mpv.exe"),
                File("resources/windows/mpv/mpv.exe"),
            )
            return candidates.firstOrNull { it.isFile } ?: candidates.first()
        }

        /** Экран, на котором сейчас окно (или курсор) — там и открываем плеер. */
        private fun currentScreenBounds(near: Rectangle? = null): Rectangle {
            val point = near?.let { java.awt.Point(it.centerX.toInt(), it.centerY.toInt()) } ?: MouseInfo.getPointerInfo()?.location
            val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            val device = devices.firstOrNull { point != null && it.defaultConfiguration.bounds.contains(point) }
                ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            return device.defaultConfiguration.bounds
        }
    }
}
