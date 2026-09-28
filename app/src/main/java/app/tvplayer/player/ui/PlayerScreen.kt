package app.tvplayer.player.ui

import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.tvplayer.player.PlayerStateHolder
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.TvButton
import app.tvplayer.ui.TvFocusable
import app.tvplayer.ui.safeRequestFocus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CONTROLS_TIMEOUT_MS = 5_000L
private const val SEEK_COMMIT_DELAY_MS = 700L
private const val HINT_DURATION_MS = 2_500L
private const val DOUBLE_BACK_WINDOW_MS = 2_000L

private enum class Panel { Audio, Subtitles, Speed, Aspect }

/** Шаг перемотки растёт, пока кнопка зажата: 10 с → 30 с → 60 с. */
private fun seekStepMs(repeatCount: Int): Long = when {
    repeatCount < 5 -> 10_000L
    repeatCount < 20 -> 30_000L
    else -> 60_000L
}

private val KeyEvent.repeatCount: Int get() = nativeKeyEvent.repeatCount

private val KeyEvent.isConfirm: Boolean
    get() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: PlayerStateHolder,
    initialHint: String?,
    onExit: () -> Unit,
) {
    val player = state.player

    var controlsVisible by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var interactionTick by remember { mutableIntStateOf(0) }

    var hint by remember { mutableStateOf(initialHint) }
    var hintTick by remember { mutableIntStateOf(0) }
    var lastBackAt by remember { mutableLongStateOf(0L) }

    // Перемотка копится, пока жмут стрелки, и применяется одним seekTo после паузы
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var seekTick by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }

    fun showHint(text: String) {
        hint = text
        hintTick++
    }

    fun seekBy(deltaMs: Long) {
        val base = pendingSeekMs ?: player.currentPosition
        val max = state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        pendingSeekMs = (base + deltaMs).coerceIn(0L, max)
        seekTick++
    }

    fun togglePlay() {
        player.playWhenReady = !player.playWhenReady
    }

    LaunchedEffect(Unit) {
        while (true) {
            state.updateProgress()
            delay(500)
        }
    }

    LaunchedEffect(seekTick) {
        if (pendingSeekMs != null) {
            delay(SEEK_COMMIT_DELAY_MS)
            pendingSeekMs?.let { player.seekTo(it) }
            pendingSeekMs = null
        }
    }

    LaunchedEffect(controlsVisible, interactionTick, state.playWhenReady, panel) {
        if (controlsVisible && panel == null && state.playWhenReady) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(hintTick) {
        if (hint != null) {
            delay(HINT_DURATION_MS)
            hint = null
        }
    }

    LaunchedEffect(controlsVisible, panel, state.error) {
        when {
            state.error != null -> errorFocus.safeRequestFocus()
            panel != null -> Unit // панель сама ставит фокус на выбранный пункт
            controlsVisible -> playFocus.safeRequestFocus()
            else -> rootFocus.safeRequestFocus()
        }
    }

    // Порядок важен: срабатывает последний включённый обработчик
    BackHandler(enabled = panel == null && !controlsVisible) {
        if (pendingSeekMs != null) {
            pendingSeekMs = null
            return@BackHandler
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < DOUBLE_BACK_WINDOW_MS) {
            onExit()
        } else {
            lastBackAt = now
            showHint("Нажмите «Назад» ещё раз, чтобы выйти")
        }
    }
    BackHandler(enabled = panel == null && controlsVisible) { controlsVisible = false }
    BackHandler(enabled = panel != null) { panel = null }
    BackHandler(enabled = state.error != null) { onExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interactionTick++
                // Медиа-кнопки пульта работают всегда
                when (event.key) {
                    Key.MediaPlayPause -> {
                        togglePlay()
                        if (!player.playWhenReady) controlsVisible = true
                        true
                    }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); controlsVisible = true; true }
                    Key.MediaFastForward -> { seekBy(30_000); true }
                    Key.MediaRewind -> { seekBy(-30_000); true }
                    Key.MediaNext -> { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); true }
                    Key.MediaPrevious -> { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); true }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                // Сюда доходят только необработанные события; управляем «голым» видео
                if (controlsVisible || panel != null || state.error != null) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.DirectionLeft -> { seekBy(-seekStepMs(event.repeatCount)); true }
                    event.key == Key.DirectionRight -> { seekBy(seekStepMs(event.repeatCount)); true }
                    event.isConfirm || event.key == Key.DirectionUp || event.key == Key.DirectionDown || event.key == Key.Menu -> {
                        controlsVisible = true
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    // Фокусом управляет Compose, PlayerView не должен его перехватывать
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.setApplyEmbeddedStyles(true)
                    this.player = player
                }
            },
            update = { it.resizeMode = resizeMode },
            onRelease = { it.player = null },
        )

        if (state.isBuffering && state.error == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(64.dp),
                color = AppColors.Accent,
                strokeWidth = 5.dp,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ControlsOverlay(
                state = state,
                pendingSeekMs = pendingSeekMs,
                playFocus = playFocus,
                onSeekBy = ::seekBy,
                onTogglePlay = ::togglePlay,
                onOpenPanel = { panel = it },
            )
        }

        // Мини-полоска прогресса при перемотке без оверлея
        AnimatedVisibility(
            visible = !controlsVisible && pendingSeekMs != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            SeekPreviewBar(state, pendingSeekMs ?: state.positionMs)
        }

        hint?.let { text ->
            Text(
                text = text,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(50))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = panel != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            when (panel) {
                Panel.Audio -> SidePanel("Звуковая дорожка", audioOptions(player, state.tracks)) { panel = null }
                Panel.Subtitles -> SidePanel("Субтитры", subtitleOptions(player, state.tracks)) { panel = null }
                Panel.Speed -> SidePanel(
                    "Скорость",
                    speedValues.map { v ->
                        PanelOption(speedName(v), selected = state.speed == v, onSelect = { player.setPlaybackSpeed(v) })
                    },
                ) { panel = null }
                Panel.Aspect -> SidePanel(
                    "Размер изображения",
                    resizeModes.map { (mode, name) ->
                        PanelOption(name, selected = resizeMode == mode, onSelect = { resizeMode = mode })
                    },
                ) { panel = null }
                null -> Unit
            }
        }

        state.error?.let { error ->
            ErrorOverlay(
                message = describeError(error),
                details = error.errorCodeName + (error.cause?.message?.let { " · $it" } ?: ""),
                focusRequester = errorFocus,
                onRetry = {
                    state.clearError()
                    player.prepare()
                    player.play()
                },
                onExit = onExit,
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    state: PlayerStateHolder,
    pendingSeekMs: Long?,
    playFocus: FocusRequester,
    onSeekBy: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenPanel: (Panel) -> Unit,
) {
    val player = state.player
    val shownPosition = pendingSeekMs ?: state.positionMs

    Box(Modifier.fillMaxSize()) {
        // Затемнение сверху и снизу, чтобы текст читался на любом кадре
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))),
        )

        // Верх: название и часы
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title ?: "",
                    color = AppColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.itemCount > 1) {
                    Text(
                        text = "${state.itemIndex + 1} из ${state.itemCount}",
                        color = AppColors.TextSecondary,
                        fontSize = 16.sp,
                    )
                }
            }
            Clock(state)
        }

        // Низ: прогресс и кнопки
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(shownPosition), color = AppColors.TextPrimary, fontSize = 16.sp)
                SeekBar(
                    positionMs = shownPosition,
                    bufferedMs = state.bufferedMs,
                    durationMs = state.durationMs,
                    onSeek = { direction, repeat -> onSeekBy(direction * seekStepMs(repeat)) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
                Text(
                    text = if (state.durationMs > 0) formatTime(state.durationMs) else "--:--",
                    color = AppColors.TextSecondary,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.itemCount > 1) {
                    RoundControl(Icons.Filled.SkipPrevious, "Предыдущий", enabled = state.hasPrevious) {
                        player.seekToPreviousMediaItem()
                    }
                    Spacer(Modifier.width(12.dp))
                }
                RoundControl(Icons.Filled.Replay10, "Назад 10 секунд") { onSeekBy(-10_000) }
                Spacer(Modifier.width(12.dp))
                RoundControl(
                    icon = if (state.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (state.playWhenReady) "Пауза" else "Играть",
                    large = true,
                    modifier = Modifier.focusRequester(playFocus),
                    onClick = onTogglePlay,
                )
                Spacer(Modifier.width(12.dp))
                RoundControl(Icons.Filled.Forward10, "Вперёд 10 секунд") { onSeekBy(10_000) }
                if (state.itemCount > 1) {
                    Spacer(Modifier.width(12.dp))
                    RoundControl(Icons.Filled.SkipNext, "Следующий", enabled = state.hasNext) {
                        player.seekToNextMediaItem()
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Звук", icon = Icons.Filled.Audiotrack, onClick = { onOpenPanel(Panel.Audio) })
                    TvButton("Субтитры", icon = Icons.Filled.Subtitles, onClick = { onOpenPanel(Panel.Subtitles) })
                    TvButton("Кадр", icon = Icons.Filled.AspectRatio, onClick = { onOpenPanel(Panel.Aspect) })
                    TvButton(speedName(state.speed), icon = Icons.Filled.Speed, onClick = { onOpenPanel(Panel.Speed) })
                }
            }
        }
    }
}

/** Часы и время окончания фильма с учётом скорости. */
@Composable
private fun Clock(state: PlayerStateHolder) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(horizontalAlignment = Alignment.End) {
        Text(fmt.format(Date(now)), color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Light)
        if (state.durationMs > 0) {
            val remaining = ((state.durationMs - state.positionMs) / state.speed).toLong()
            Text(
                text = "Закончится в " + fmt.format(Date(now + remaining)),
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onSeek: (direction: Int, repeatCount: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val barHeight by animateDpAsState(if (focused) 8.dp else 4.dp, label = "seekBarHeight")

    Box(
        modifier = modifier
            .height(28.dp)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeek(-1, event.repeatCount); true }
                    Key.DirectionRight -> { onSeek(1, event.repeatCount); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        ProgressLine(positionMs, bufferedMs, durationMs, barHeight, showThumb = focused)
    }
}

@Composable
private fun ProgressLine(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    height: androidx.compose.ui.unit.Dp,
    showThumb: Boolean,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
    ) {
        val h = height.toPx()
        val top = (size.height - h) / 2
        val radius = CornerRadius(h / 2, h / 2)
        fun fraction(ms: Long) = if (durationMs > 0) (ms.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

        drawRoundRect(Color(0x40FFFFFF), Offset(0f, top), Size(size.width, h), radius)
        drawRoundRect(Color(0x66FFFFFF), Offset(0f, top), Size(size.width * fraction(bufferedMs), h), radius)
        val played = size.width * fraction(positionMs)
        drawRoundRect(AppColors.Accent, Offset(0f, top), Size(played, h), radius)
        if (showThumb) {
            drawCircle(Color.White, radius = h * 1.2f, center = Offset(played, size.height / 2))
        }
    }
}

@Composable
private fun SeekPreviewBar(state: PlayerStateHolder, targetMs: Long) {
    val delta = targetMs - state.positionMs
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTime(targetMs), color = AppColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(12.dp))
            Text(
                text = (if (delta >= 0) "+" else "−") + formatTime(kotlin.math.abs(delta)),
                color = AppColors.Accent,
                fontSize = 18.sp,
            )
            Spacer(Modifier.weight(1f))
            if (state.durationMs > 0) {
                Text(formatTime(state.durationMs), color = AppColors.TextSecondary, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressLine(targetMs, state.bufferedMs, state.durationMs, 6.dp, showThumb = true)
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val size = if (large) 68.dp else 54.dp
    TvFocusable(
        onClick = { if (enabled) onClick() },
        modifier = modifier.size(size),
        shape = CircleShape,
        focusedScale = 1.12f,
        background = if (large) Color(0x33FFFFFF) else Color.Transparent,
    ) { focused ->
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> AppColors.TextMuted
                focused -> AppColors.OnFocused
                else -> AppColors.TextPrimary
            },
            modifier = Modifier.size(if (large) 38.dp else 30.dp),
        )
    }
}

@Composable
private fun SidePanel(title: String, options: List<PanelOption>, onDismiss: () -> Unit) {
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { firstFocus.safeRequestFocus() }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(440.dp)
            .background(Color(0xF21A1C22))
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(title, color = AppColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        if (options.isEmpty()) {
            Text("Нет доступных вариантов", color = AppColors.TextSecondary, fontSize = 16.sp)
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(options) { index, option ->
                TvFocusable(
                    onClick = {
                        option.onSelect()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == selectedIndex) Modifier.focusRequester(firstFocus) else Modifier),
                    focusedScale = 1.02f,
                    background = Color.Transparent,
                    contentAlignment = Alignment.CenterStart,
                ) { focused ->
                    val primary = if (focused) AppColors.OnFocused else AppColors.TextPrimary
                    val secondary = if (focused) AppColors.OnFocused.copy(alpha = 0.7f) else AppColors.TextSecondary
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(24.dp)) {
                            if (option.selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = if (focused) primary else AppColors.Accent)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(option.title, color = primary, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            option.subtitle?.let { Text(it, color = secondary, fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    details: String,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(640.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Не удалось воспроизвести", color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(message, color = AppColors.TextSecondary, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(details, color = AppColors.TextMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Повторить", onClick = onRetry, modifier = Modifier.focusRequester(focusRequester))
                TvButton("Выйти", onClick = onExit)
            }
        }
    }
}
