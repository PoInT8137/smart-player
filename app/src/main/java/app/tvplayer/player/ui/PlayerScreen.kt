package app.tvplayer.player.ui

import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.tvplayer.player.PlayerStateHolder
import app.tvplayer.player.afr.FrameRateMatcher
import app.tvplayer.torrserver.TorrServerClient
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.torrserver.formatSpeed
import app.tvplayer.torrserver.peersLabel
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.TvFocusable
import app.tvplayer.ui.safeRequestFocus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CONTROLS_TIMEOUT_MS = 3_500L
private const val SEEK_COMMIT_DELAY_MS = 700L
private const val HINT_DURATION_MS = 2_000L
private const val FLASH_DURATION_MS = 550L
private const val DOUBLE_BACK_WINDOW_MS = 2_000L
private const val NEXT_UP_MS = 20_000L

/** Шаг перемотки растёт, пока кнопка зажата: 10 с → 30 с → 60 с. */
private fun seekStepMs(repeatCount: Int): Long = when {
    repeatCount < 5 -> 10_000L
    repeatCount < 20 -> 30_000L
    else -> 60_000L
}

private val KeyEvent.repeatCount: Int get() = nativeKeyEvent.repeatCount

private val KeyEvent.isConfirm: Boolean
    get() = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Экран воспроизведения.
 *
 * Пульт без меню: OK — пауза/продолжить, ◀ ▶ — перемотка (зажать — быстрее),
 * ▲ ▼ — меню. В меню фокус сразу на шкале времени, ниже — кнопки с текущими
 * значениями (звук, субтитры, настройки, серии).
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: PlayerStateHolder,
    initialHint: String?,
    autoFrameRate: Boolean,
    onAutoFrameRateChange: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val player = state.player

    var controlsVisible by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf<SheetTab?>(null) }
    var episodesOpen by remember { mutableStateOf(false) }
    val overlayOpen = sheetTab != null || episodesOpen

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var interactionTick by remember { mutableIntStateOf(0) }

    var hint by remember { mutableStateOf(initialHint) }
    var hintTick by remember { mutableIntStateOf(0) }
    var lastBackAt by remember { mutableLongStateOf(0L) }

    var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }

    // Перемотка копится, пока жмут стрелки, и применяется одним seekTo после паузы
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var seekOriginMs by remember { mutableLongStateOf(0L) }
    var seekTick by remember { mutableIntStateOf(0) }

    var nextUpDismissedFor by remember { mutableIntStateOf(-1) }
    var timelineFocused by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }

    fun showHint(text: String) {
        hint = text
        hintTick++
    }

    fun flash(icon: ImageVector) {
        flashIcon = icon
        flashTick++
    }

    fun togglePlay() {
        player.playWhenReady = !player.playWhenReady
        flash(if (player.playWhenReady) Icons.Filled.PlayArrow else Icons.Filled.Pause)
    }

    fun seekBy(deltaMs: Long) {
        if (pendingSeekMs == null) seekOriginMs = player.currentPosition
        val base = pendingSeekMs ?: seekOriginMs
        val max = state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        pendingSeekMs = (base + deltaMs).coerceIn(0L, max)
        seekTick++
    }

    val remainingInItem = state.durationMs - state.positionMs
    val nextUpVisible = state.hasNext && state.durationMs > 60_000 &&
        remainingInItem in 1..NEXT_UP_MS && nextUpDismissedFor != state.itemIndex &&
        state.error == null && !overlayOpen && !controlsVisible

    // Статистика торрента, если поток идёт через TorrServer
    val torrentLink = remember(state.currentUri) {
        state.currentUri?.let { TorrServerClient.parseLink(it) }
    }
    val torrent by produceState<TorrentInfo?>(initialValue = null, torrentLink) {
        value = null
        val link = torrentLink ?: return@produceState
        val client = TorrServerClient(link.baseUrl)
        while (true) {
            runCatching { client.get(link.hash) }.onSuccess { value = it }
            delay(if (state.isBuffering) 1_000 else 3_000)
        }
    }

    // --- Эффекты ---

    LaunchedEffect(Unit) {
        while (true) {
            state.updateProgress()
            delay(250)
        }
    }

    LaunchedEffect(seekTick) {
        if (pendingSeekMs != null) {
            delay(SEEK_COMMIT_DELAY_MS)
            pendingSeekMs?.let { player.seekTo(it) }
            pendingSeekMs = null
        }
    }

    LaunchedEffect(controlsVisible, interactionTick, state.playWhenReady, overlayOpen) {
        if (controlsVisible && !overlayOpen && state.playWhenReady) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let { showHint(it.text) }
    }

    LaunchedEffect(hintTick) {
        if (hint != null) {
            delay(HINT_DURATION_MS)
            hint = null
        }
    }

    LaunchedEffect(flashTick) {
        if (flashIcon != null) {
            delay(FLASH_DURATION_MS)
            flashIcon = null
        }
    }

    LaunchedEffect(controlsVisible, overlayOpen, state.error) {
        when {
            state.error != null -> errorFocus.safeRequestFocus()
            overlayOpen -> Unit // панели сами ставят фокус на выбранный пункт
            controlsVisible -> timelineFocus.safeRequestFocus()
            else -> rootFocus.safeRequestFocus()
        }
    }

    // --- «Назад»: срабатывает последний включённый обработчик ---

    BackHandler(enabled = !controlsVisible && !overlayOpen) {
        when {
            pendingSeekMs != null -> pendingSeekMs = null
            nextUpVisible -> nextUpDismissedFor = state.itemIndex
            SystemClock.uptimeMillis() - lastBackAt < DOUBLE_BACK_WINDOW_MS -> onExit()
            else -> {
                lastBackAt = SystemClock.uptimeMillis()
                showHint("Нажмите «Назад» ещё раз, чтобы выйти")
            }
        }
    }
    BackHandler(enabled = controlsVisible && !overlayOpen) { controlsVisible = false }
    BackHandler(enabled = sheetTab != null) { sheetTab = null }
    BackHandler(enabled = episodesOpen) { episodesOpen = false }
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
                    Key.MediaPlay -> { player.play(); flash(Icons.Filled.PlayArrow); true }
                    Key.MediaPause -> { player.pause(); flash(Icons.Filled.Pause); controlsVisible = true; true }
                    Key.MediaFastForward -> { seekBy(30_000); true }
                    Key.MediaRewind -> { seekBy(-30_000); true }
                    Key.MediaNext -> { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); true }
                    Key.MediaPrevious -> { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); true }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                // Сюда доходят только необработанные события: управляем «голым» видео
                if (controlsVisible || overlayOpen || state.error != null) return@onKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.DirectionLeft -> { seekBy(-seekStepMs(event.repeatCount)); true }
                    event.key == Key.DirectionRight -> { seekBy(seekStepMs(event.repeatCount)); true }
                    event.isConfirm -> {
                        if (event.repeatCount == 0) {
                            if (nextUpVisible) {
                                player.seekToNextMediaItem()
                            } else {
                                togglePlay()
                                if (!player.playWhenReady) controlsVisible = true
                            }
                        }
                        true
                    }
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown || event.key == Key.Menu -> {
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
                    subtitleView?.applyCinemaStyle()
                    this.player = player
                }
            },
            update = {
                it.resizeMode = resizeMode
                // Когда открыто меню, поднимаем субтитры над ним, чтобы их не закрывали кнопки
                it.subtitleView?.setBottomPaddingFraction(
                    if (controlsVisible && !overlayOpen) SUBTITLE_BOTTOM_ABOVE_CONTROLS else SUBTITLE_BOTTOM_NORMAL
                )
            },
            onRelease = { it.player = null },
        )

        // Затемнение видео под панелями
        AnimatedVisibility(visible = overlayOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000)),
            )
        }

        AnimatedVisibility(
            visible = state.isBuffering && state.error == null && pendingSeekMs == null,
            enter = fadeIn(tween(400, delayMillis = 200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            BufferingIndicator(torrent)
        }

        CenterFlash(flashIcon, rememberLast(flashIcon))

        SeekBubble(
            deltaMs = pendingSeekMs?.takeIf { !controlsVisible }?.let { it - seekOriginMs },
        )

        // Мини-шкала при перемотке без меню
        AnimatedVisibility(
            visible = !controlsVisible && !overlayOpen && pendingSeekMs != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MiniSeekBar(state, rememberLast(pendingSeekMs) ?: state.positionMs)
        }

        // Значок паузы, если меню скрыли на паузе
        AnimatedVisibility(
            visible = !state.playWhenReady && !controlsVisible && !overlayOpen && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(40.dp),
        ) {
            Row(
                Modifier
                    .background(Color(0x99000000), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Пауза", color = Color.White, fontSize = 15.sp)
            }
        }

        AnimatedVisibility(
            visible = nextUpVisible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(48.dp),
        ) {
            NextUpCard(
                title = rememberLast(state.nextTitle) ?: "",
                secondsLeft = (remainingInItem / 1000).toInt().coerceAtLeast(0),
                progress = 1f - remainingInItem.toFloat() / NEXT_UP_MS,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !overlayOpen && state.error == null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000), Color(0xE6000000)))),
                )

                BottomControls(
                    state = state,
                    pendingSeekMs = pendingSeekMs,
                    timelineFocus = timelineFocus,
                    timelineFocused = timelineFocused,
                    onTimelineFocusChange = { timelineFocused = it },
                    onSeekBy = ::seekBy,
                    onTogglePlay = ::togglePlay,
                    onOpenSheet = { sheetTab = it },
                    onOpenEpisodes = { episodesOpen = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(spring(dampingRatio = 0.8f)) { it / 3 },
                            exit = slideOutVertically(tween(160)) { it / 3 },
                        ),
                )
            }
        }

        val lastSheetTab = rememberLast(sheetTab) ?: SheetTab.Audio
        AnimatedVisibility(
            visible = sheetTab != null,
            enter = slideInHorizontally(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SettingsSheet(
                initialTab = lastSheetTab,
                optionsFor = { tab ->
                    when (tab) {
                        SheetTab.Audio -> audioOptions(player, state.tracks)
                        SheetTab.Subtitles -> subtitleOptions(player, state.tracks)
                        SheetTab.Quality -> qualityOptions(player, state.tracks)
                        SheetTab.Picture -> resizeModes.mapIndexed { i, (mode, name) ->
                            PanelOption(
                                name,
                                selected = resizeMode == mode,
                                header = "Размер изображения".takeIf { i == 0 },
                                onSelect = { resizeMode = mode },
                            )
                        } + listOf(
                            PanelOption(
                                "Под частоту фильма",
                                subtitle = "Без подёргиваний: 24 кадра — 24 Гц, 25 — 50 Гц",
                                selected = autoFrameRate,
                                header = "Частота экрана",
                                onSelect = { onAutoFrameRateChange(true) },
                            ),
                            PanelOption(
                                "Как в системе",
                                subtitle = "Не переключать режим телевизора",
                                selected = !autoFrameRate,
                                onSelect = { onAutoFrameRateChange(false) },
                            ),
                        )
                        SheetTab.Speed -> speedValues.map { v ->
                            PanelOption(speedName(v), selected = state.speed == v, onSelect = { player.setPlaybackSpeed(v) })
                        }
                    }
                },
                onDismiss = { sheetTab = null },
                infoFor = { tab -> if (tab == SheetTab.Quality) streamInfo(state, torrent) else null },
            )
        }

        AnimatedVisibility(
            visible = episodesOpen,
            enter = slideInVertically(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            EpisodesPanel(
                titles = state.itemTitles,
                currentIndex = state.itemIndex,
                onSelect = { index ->
                    if (index != state.itemIndex) player.seekToDefaultPosition(index)
                    player.play()
                    episodesOpen = false
                    controlsVisible = false
                },
            )
        }

        val shownHint = rememberLast(hint)
        AnimatedVisibility(
            visible = hint != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
        ) {
            Text(
                text = shownHint ?: "",
                color = AppColors.TextPrimary,
                fontSize = 17.sp,
                modifier = Modifier
                    .background(Color(0xE6101217), RoundedCornerShape(50))
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn(tween(300)),
            exit = fadeOut(),
        ) {
            val error = rememberLast(state.error)
            if (error != null) {
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
}

/** Технические данные потока для вкладки «Качество»: «1080p · HEVC · HDR10 · 24 fps · AC3 5.1». */
private fun streamInfo(state: PlayerStateHolder, torrent: TorrentInfo?): String? {
    val media = mediaBadges(state.tracks).toMutableList()
    state.videoFps?.let { fps ->
        FrameRateMatcher.normalize(fps)?.let { media += "${FrameRateMatcher.format(it)} fps" }
    }
    val lines = listOfNotNull(
        media.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { "Сейчас: $it" },
        torrent?.let { "Торрент: ↓ ${formatSpeed(it.downloadSpeed)} · ${peersLabel(it.activePeers)}" },
    )
    return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** Время и момент окончания одной строкой: «21:05 · конец в 23:41». */
@Composable
private fun ClockLine(remainingMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val text = buildString {
        append(fmt.format(Date(now)))
        if (remainingMs > 0) append(" · конец в ").append(fmt.format(Date(now + remainingMs)))
    }
    Text(text, color = AppColors.TextSecondary, fontSize = 14.sp)
}

@Composable
private fun BottomControls(
    state: PlayerStateHolder,
    pendingSeekMs: Long?,
    timelineFocus: FocusRequester,
    timelineFocused: Boolean,
    onTimelineFocusChange: (Boolean) -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenSheet: (SheetTab) -> Unit,
    onOpenEpisodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownPosition = pendingSeekMs ?: state.positionMs
    val audioFocus = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.title ?: "",
                    color = AppColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state.itemCount > 1) {
                    Text(
                        "  ·  ${state.itemIndex + 1} из ${state.itemCount}",
                        color = AppColors.TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(24.dp))
            ClockLine(remainingMs = state.remainingMs)
        }

        // Время по бокам шкалы — одна строка вместо двух
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatTime(shownPosition),
                color = AppColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Timeline(
                positionMs = shownPosition,
                bufferedMs = state.bufferedMs,
                durationMs = state.durationMs,
                focusRequester = timelineFocus,
                downTarget = audioFocus,
                onFocusChange = onTimelineFocusChange,
                onSeek = { direction, repeat -> onSeekBy(direction * seekStepMs(repeat)) },
                onToggle = onTogglePlay,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            )
            Text(
                text = if (state.durationMs > 0) "−" + formatTime(state.durationMs - shownPosition) else "--:--",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayPauseButton(playing = state.playWhenReady, onClick = onTogglePlay)
            val hintsAlpha by animateFloatAsState(if (timelineFocused) 1f else 0f, label = "hints")
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
                    .alpha(hintsAlpha),
            ) {
                KeyHint("OK", if (state.playWhenReady) "пауза" else "смотреть")
                KeyHint("◀ ▶", "перемотка")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.itemCount > 1) {
                    ControlButton(
                        icon = Icons.Filled.VideoLibrary,
                        label = "Серии",
                        value = "${state.itemIndex + 1} из ${state.itemCount}",
                        onClick = onOpenEpisodes,
                    )
                }
                ControlButton(
                    icon = Icons.Filled.Audiotrack,
                    label = "Звук",
                    value = currentAudioSummary(state.tracks),
                    onClick = { onOpenSheet(SheetTab.Audio) },
                    modifier = Modifier.focusRequester(audioFocus),
                )
                ControlButton(
                    icon = Icons.Filled.Subtitles,
                    label = "Субтитры",
                    value = currentSubtitleSummary(state.tracks),
                    onClick = { onOpenSheet(SheetTab.Subtitles) },
                )
                ControlButton(
                    icon = Icons.Filled.HighQuality,
                    label = "Качество",
                    value = currentQualitySummary(state.player, state.tracks),
                    onClick = { onOpenSheet(SheetTab.Quality) },
                )
                ControlButton(
                    icon = Icons.Filled.Tune,
                    label = "Настройки",
                    value = if (state.speed != 1f) "Скорость ${speedName(state.speed)}" else "Картинка, скорость",
                    onClick = { onOpenSheet(SheetTab.Picture) },
                )
            }
        }
    }
}

/** Круглая кнопка-иконка; в фокусе раскрывается в «таблетку» с подписью и текущим значением. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        background = AppColors.GlassLight,
    ) { focused ->
        val color = if (focused) AppColors.OnFocused else AppColors.TextPrimary
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
            AnimatedVisibility(
                visible = focused,
                enter = expandHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkHorizontally(tween(140)) + fadeOut(tween(100)),
            ) {
                Column(Modifier.padding(start = 9.dp, end = 7.dp)) {
                    Text(
                        label,
                        color = color,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (value != null) {
                        Text(
                            value,
                            color = color.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    focusRequester: FocusRequester,
    downTarget: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onSeek: (direction: Int, repeatCount: Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChange(focused) }

    val trackHeight by animateDpAsState(if (focused) 7.dp else 4.dp, spring(dampingRatio = 0.6f), label = "trackH")
    val thumb by animateFloatAsState(if (focused) 1f else 0f, spring(dampingRatio = 0.5f), label = "thumb")
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackPx = with(LocalDensity.current) { trackHeight.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .height(46.dp)
            .focusRequester(focusRequester)
            // «Вниз» со шкалы всегда ведёт на «Звук»
            .focusProperties { down = downTarget }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when {
                    event.key == Key.DirectionLeft -> { onSeek(-1, event.repeatCount); true }
                    event.key == Key.DirectionRight -> { onSeek(1, event.repeatCount); true }
                    event.isConfirm -> { if (event.repeatCount == 0) onToggle(); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction),
    ) {
        // Подсказка времени над бегунком
        val tooltipWidth = 64.dp
        Text(
            text = formatTime(positionMs),
            color = AppColors.OnFocused,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (maxWidth * fraction - tooltipWidth / 2).coerceIn(0.dp, maxWidth - tooltipWidth))
                .width(tooltipWidth)
                .alpha(thumb.coerceIn(0f, 1f))
                .background(Color.White, RoundedCornerShape(6.dp))
                .padding(vertical = 2.dp),
        )
        ProgressTrack(
            fraction = fraction,
            bufferedFraction = buffered,
            heightPx = trackPx,
            thumbScale = thumb,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        focusedScale = 1.12f,
        background = AppColors.GlassLight,
    ) { focused ->
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Пауза" else "Смотреть",
            tint = if (focused) AppColors.OnFocused else AppColors.TextPrimary,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun MiniSeekBar(state: PlayerStateHolder, targetMs: Long) {
    val fraction = if (state.durationMs > 0) (targetMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (state.durationMs > 0) (state.bufferedMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val trackPx = with(LocalDensity.current) { 6.dp.toPx() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
            .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTime(targetMs), color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (state.durationMs > 0) {
                Text(formatTime(state.durationMs), color = AppColors.TextSecondary, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressTrack(
            fraction = fraction,
            bufferedFraction = buffered,
            heightPx = trackPx,
            thumbScale = 1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
        )
    }
}
