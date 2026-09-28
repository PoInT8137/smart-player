package app.tvplayer.player.afr

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.media3.common.Player

/**
 * Автоматическая частота экрана (AFR): переключает режим HDMI под частоту кадров фильма.
 *
 * Переключение режима — это пересинхронизация HDMI: телевизор гаснет на 1–3 с.
 * Поэтому на это время ставим паузу и продолжаем, когда экран сообщит о смене режима.
 * При выходе из плеера возвращаем режим, выбранный системой.
 */
class AutoFrameRate(
    private val activity: Activity,
    /** Сообщение пользователю: «Экран: 23.976 Гц». */
    private val onSwitched: (refreshRate: Float) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val displayManager = activity.getSystemService(DisplayManager::class.java)

    private var appliedModeId = 0
    private var lastFps: Float? = null
    private var pendingResume: Runnable? = null
    private var listener: DisplayManager.DisplayListener? = null

    private var detectedFps: Float? = null

    var enabled: Boolean = true
        private set

    /** Включить/выключить; при включении сразу применяем уже измеренную частоту. */
    fun setEnabled(value: Boolean, player: Player?) {
        enabled = value
        if (!value) {
            restore()
        } else if (player != null) {
            detectedFps?.let { onFrameRateDetected(it, player) }
        }
    }

    @Suppress("DEPRECATION")
    private val display: Display?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay

    /** Новый файл: частота могла поменяться. */
    fun onNewItem() {
        lastFps = null
        detectedFps = null
    }

    fun onFrameRateDetected(fps: Float, player: Player) {
        detectedFps = fps
        val rate = FrameRateMatcher.normalize(fps) ?: return
        if (!enabled || rate == lastFps) return
        lastFps = rate

        val display = display ?: return
        val current = display.mode.toInfo()
        val target = FrameRateMatcher.bestMode(rate, current, display.supportedModes.map { it.toInfo() })
        Log.d(TAG, "fps=$fps → $rate, current=${current.refreshRate}Hz, modes=${display.supportedModes.map { it.refreshRate }}, target=${target?.refreshRate}")
        if (target == null) return

        switchTo(target, player)
    }

    private fun switchTo(target: DisplayModeInfo, player: Player) {
        cancelPending()
        val resumeAfter = player.playWhenReady
        if (resumeAfter) player.pause()

        val resume = Runnable {
            cancelPending()
            // Пользователь мог сам нажать паузу/плей — тогда не вмешиваемся
            if (resumeAfter && !player.playWhenReady) player.play()
        }
        pendingResume = resume

        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(id: Int) {
                if (id == displayId && display?.mode?.modeId == target.id) {
                    // Даём телевизору «проснуться» после пересинхронизации HDMI
                    handler.removeCallbacks(resume)
                    handler.postDelayed(resume, SETTLE_DELAY_MS)
                }
            }
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
        }
        listener = l
        displayManager.registerDisplayListener(l, handler)
        handler.postDelayed(resume, SWITCH_TIMEOUT_MS)

        setPreferredMode(target.id)
        onSwitched(target.refreshRate)
    }

    /** Вернуть режим по умолчанию (0 — «на усмотрение системы»). */
    fun restore() {
        cancelPending()
        lastFps = null
        if (appliedModeId != 0) setPreferredMode(0)
    }

    private fun setPreferredMode(modeId: Int) {
        appliedModeId = modeId
        val window = activity.window
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
    }

    private fun cancelPending() {
        pendingResume?.let { handler.removeCallbacks(it) }
        pendingResume = null
        listener?.let { displayManager.unregisterDisplayListener(it) }
        listener = null
    }

    private fun Display.Mode.toInfo() = DisplayModeInfo(modeId, physicalWidth, physicalHeight, refreshRate)

    private companion object {
        const val TAG = "AutoFrameRate"
        const val SETTLE_DELAY_MS = 1_200L
        const val SWITCH_TIMEOUT_MS = 5_000L
    }
}
