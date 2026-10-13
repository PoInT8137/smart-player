package app.smartplayer.desktop.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.smartplayer.desktop.player.MpvEngine
import app.smartplayer.desktop.player.ResizeMode
import app.smartplayer.desktop.ui.AppColors
import app.smartplayer.desktop.ui.Clock
import app.smartplayer.desktop.ui.Interactive
import app.tvplayer.torrserver.TorrServerClient
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.torrserver.formatSpeed
import app.tvplayer.torrserver.peersLabel
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private const val CONTROLS_TIMEOUT_MS = 3_500L
private const val SEEK_COMMIT_DELAY_MS = 700L
private const val HINT_DURATION_MS = 2_000L
private const val FLASH_DURATION_MS = 550L
private const val DOUBLE_BACK_WINDOW_MS = 2_000L
private const val NEXT_UP_MS = 20_000L

/** Шаг перемотки растёт, пока клавиша зажата: 10 с → 30 с → 60 с. */
private fun seekStepMs(repeatCount: Int): Long = when {
    repeatCount < 5 -> 10_000L
    repeatCount < 20 -> 30_000L
    else -> 60_000L
}

private val KeyEvent.isConfirm: Boolean get() = key == Key.Enter || key == Key.NumPadEnter || key == Key.Spacebar

private val KeyEvent.isBack: Boolean get() = key == Key.Escape || key == Key.Backspace

/** Пустой курсор — прячем его вместе с меню. */
private val BlankCursor = PointerIcon(
    Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"),
)

/**
 * Экран воспроизведения — тот же интерфейс, что в Smart Player для Android TV.
 * Клавиатура как пульт: Пробел/Enter — пауза, ← → — перемотка (зажать — быстрее),
 * ↑ ↓ — меню, Esc — назад. Мышью: движение — меню, клик — пауза, двойной клик — полный экран,
 * колесо — громкость.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    engine: MpvEngine,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onDragWindow: (dxPx: Float, dyPx: Float) -> Unit,
    onExit: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var sheetTab by remember { mutableStateOf<SheetTab?>(null) }
    var episodesOpen by remember { mutableStateOf(false) }
    val overlayOpen = sheetTab != null || episodesOpen

    var resizeMode by remember { mutableStateOf(ResizeMode.Fit) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    var hintTick by remember { mutableIntStateOf(0) }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var seekOriginMs by remember { mutableLongStateOf(0L) }
    var seekTick by remember { mutableIntStateOf(0) }
    var heldKeyRepeats by remember { mutableIntStateOf(0) }
    var nextUpDismissedFor by remember { mutableIntStateOf(-1) }
    var timelineFocused by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val timelineFocus = remember { FocusRequester() }

    fun showHint(text: String) { hint = text; hintTick++ }
    fun flash(icon: ImageVector) { flashIcon = icon; flashTick++ }
    fun poke() { interactionTick++ }

    fun togglePlay() {
        val willPause = !engine.paused
        engine.togglePause()
        flash(if (willPause) Icons.Filled.Pause else Icons.Filled.PlayArrow)
        if (willPause) controlsVisible = true
    }

    fun seekBy(deltaMs: Long) {
        if (pendingSeekMs == null) seekOriginMs = engine.positionMs
        val base = pendingSeekMs ?: seekOriginMs
        val max = engine.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        pendingSeekMs = (base + deltaMs).coerceIn(0L, max)
        seekTick++
    }

    fun changeVolume(delta: Int) {
        engine.addVolume(delta)
        showHint("Громкость ${(engine.volume + delta).coerceIn(0, 130)}%")
    }

    // Статистика торрента, если поток идёт через TorrServer
    val torrentLink = remember(engine.itemIndex) { TorrServerClient.parseLink(engine.currentItem.url) }
    val torrent by produceState<TorrentInfo?>(initialValue = null, torrentLink) {
        value = null
        val link = torrentLink ?: return@produceState
        val client = TorrServerClient(link.baseUrl)
        while (true) {
            runCatching { client.get(link.hash) }.onSuccess { value = it }
            delay(if (engine.buffering) 1_000 else 3_000)
        }
    }

    val remainingInItem = engine.durationMs - engine.positionMs
    val nextUpVisible = engine.hasNext && engine.durationMs > 60_000 && remainingInItem in 1..NEXT_UP_MS &&
        nextUpDismissedFor != engine.itemIndex && engine.error == null && !overlayOpen && !controlsVisible

    LaunchedEffect(seekTick) {
        if (pendingSeekMs != null) {
            delay(SEEK_COMMIT_DELAY_MS)
            pendingSeekMs?.let { engine.seekTo(it) }
            pendingSeekMs = null
            heldKeyRepeats = 0
        }
    }
    LaunchedEffect(controlsVisible, interactionTick, engine.paused, overlayOpen) {
        if (controlsVisible && !overlayOpen && !engine.paused) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(hintTick) { if (hint != null) { delay(HINT_DURATION_MS); hint = null } }
    LaunchedEffect(flashTick) { if (flashIcon != null) { delay(FLASH_DURATION_MS); flashIcon = null } }
    LaunchedEffect(controlsVisible, overlayOpen) {
        when {
            overlayOpen -> Unit
            controlsVisible -> runCatching { timelineFocus.requestFocus() }
            else -> runCatching { rootFocus.requestFocus() }
        }
    }

    fun back() {
        when {
            sheetTab != null -> sheetTab = null
            episodesOpen -> episodesOpen = false
            controlsVisible -> controlsVisible = false
            pendingSeekMs != null -> pendingSeekMs = null
            nextUpVisible -> nextUpDismissedFor = engine.itemIndex
            fullscreen -> onToggleFullscreen()
            System.currentTimeMillis() - lastBackAt < DOUBLE_BACK_WINDOW_MS -> onExit()
            else -> {
                lastBackAt = System.currentTimeMillis()
                showHint("Нажмите Esc ещё раз, чтобы закрыть")
            }
        }
    }

    val cursorVisible = controlsVisible || overlayOpen || engine.error != null

    Box(
        Modifier.fillMaxSize()
            // Почти прозрачный фон: иначе Windows пропустит клики «сквозь» окно на видео
            .background(Color(0x01000000))
            .pointerHoverIcon(if (cursorVisible) PointerIcon.Default else BlankCursor)
            .onPointerEvent(PointerEventType.Move) {
                if (!controlsVisible && !overlayOpen) controlsVisible = true
                poke()
            }
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy != 0f) changeVolume(if (dy < 0) 5 else -5)
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (sheetTab == null && !episodesOpen) togglePlay() else { sheetTab = null; episodesOpen = false } },
                    onDoubleTap = { onToggleFullscreen() },
                )
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    if (event.type == KeyEventType.KeyUp && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight)) heldKeyRepeats = 0
                    return@onPreviewKeyEvent false
                }
                poke()
                when {
                    event.isBack -> { back(); true }
                    event.key == Key.F || event.key == Key.F11 -> { onToggleFullscreen(); true }
                    event.key == Key.MediaPlayPause -> { togglePlay(); true }
                    event.key == Key.N && engine.hasNext -> { engine.next(); true }
                    event.key == Key.A -> { sheetTab = SheetTab.Audio; true }
                    event.key == Key.S -> { sheetTab = SheetTab.Subtitles; true }
                    event.key == Key.M -> { engine.command("cycle", "mute"); showHint("Звук вкл/выкл"); true }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // В меню стрелки двигают фокус, как пульт (на ПК Compose сам этого не делает)
                if (controlsVisible || overlayOpen) {
                    val direction = when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        else -> null
                    }
                    return@onKeyEvent direction?.let { focusManager.moveFocus(it) } ?: false
                }
                // Сюда доходят только необработанные события: управляем «голым» видео
                if (engine.error != null) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { seekBy(-seekStepMs(heldKeyRepeats++)); true }
                    Key.DirectionRight -> { seekBy(seekStepMs(heldKeyRepeats++)); true }
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (nextUpVisible) engine.next() else togglePlay()
                        true
                    }
                    Key.DirectionUp -> { changeVolume(5); true }
                    Key.DirectionDown, Key.Menu -> { controlsVisible = true; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        AnimatedVisibility(visible = overlayOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }

        AnimatedVisibility(
            visible = engine.buffering && engine.error == null && pendingSeekMs == null,
            enter = fadeIn(tween(400, delayMillis = 200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) { BufferingIndicator(torrent) }

        CenterFlash(flashIcon, rememberLastValue(flashIcon))
        SeekBubble(deltaMs = pendingSeekMs?.takeIf { !controlsVisible }?.let { it - seekOriginMs })

        AnimatedVisibility(
            visible = !controlsVisible && !overlayOpen && pendingSeekMs != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { MiniSeekBar(engine, rememberLastValue(pendingSeekMs) ?: engine.positionMs) }

        AnimatedVisibility(
            visible = engine.paused && !controlsVisible && !overlayOpen && engine.error == null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(40.dp),
        ) {
            Row(Modifier.background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Пауза", color = Color.White, fontSize = 15.sp)
            }
        }

        AnimatedVisibility(
            visible = nextUpVisible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(48.dp),
        ) {
            NextUpCard(
                title = rememberLastValue(engine.nextTitle) ?: "",
                secondsLeft = (remainingInItem / 1000).toInt().coerceAtLeast(0),
                progress = 1f - remainingInItem.toFloat() / NEXT_UP_MS,
                onPlayNow = { engine.next() },
            )
        }

        AnimatedVisibility(visible = controlsVisible && !overlayOpen && engine.error == null, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(190.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))

                TopInfo(
                    engine = engine,
                    torrent = torrent,
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onClose = onExit,
                    onDrag = onDragWindow,
                    modifier = Modifier.align(Alignment.TopCenter).animateEnterExit(
                        enter = slideInVertically(spring(dampingRatio = 0.8f)) { -it / 2 },
                        exit = slideOutVertically(tween(160)) { -it / 2 },
                    ),
                )
                BottomControls(
                    engine = engine,
                    pendingSeekMs = pendingSeekMs,
                    timelineFocus = timelineFocus,
                    timelineFocused = timelineFocused,
                    onTimelineFocusChange = { timelineFocused = it },
                    onSeekBy = { seekBy(it) },
                    onSeekTo = { engine.seekTo(it) },
                    onTogglePlay = { togglePlay() },
                    onOpenSheet = { sheetTab = it },
                    onOpenEpisodes = { episodesOpen = true },
                    modifier = Modifier.align(Alignment.BottomCenter).animateEnterExit(
                        enter = slideInVertically(spring(dampingRatio = 0.8f)) { it / 3 },
                        exit = slideOutVertically(tween(160)) { it / 3 },
                    ),
                )
            }
        }

        val lastSheetTab = rememberLastValue(sheetTab) ?: SheetTab.Audio
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
                        SheetTab.Audio -> audioOptions(engine)
                        SheetTab.Subtitles -> subtitleOptions(engine)
                        SheetTab.Picture -> ResizeMode.entries.mapIndexed { i, mode ->
                            PanelOption(mode.title, selected = resizeMode == mode, header = "Размер изображения".takeIf { i == 0 }, onSelect = {
                                resizeMode = mode
                                engine.setResizeMode(mode)
                            })
                        }
                        SheetTab.Speed -> speedValues.map { v -> PanelOption(speedName(v), selected = engine.speed == v, onSelect = { engine.changeSpeed(v) }) }
                    }
                },
                onDismiss = { sheetTab = null },
            )
        }

        AnimatedVisibility(
            visible = episodesOpen,
            enter = slideInVertically(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            EpisodesPanel(engine.itemTitles, engine.itemIndex) { index ->
                if (index != engine.itemIndex) engine.playIndex(index)
                engine.pausePlayback(false)
                episodesOpen = false
                controlsVisible = false
            }
        }

        val shownHint = rememberLastValue(hint)
        AnimatedVisibility(
            visible = hint != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp),
        ) {
            Text(
                shownHint ?: "", color = AppColors.TextPrimary, fontSize = 17.sp,
                modifier = Modifier.background(Color(0xE6101217), RoundedCornerShape(50)).padding(horizontal = 26.dp, vertical = 12.dp),
            )
        }

        engine.error?.let { ErrorOverlay(it, onExit) }
    }
}

@Composable
private fun TopInfo(
    engine: MpvEngine,
    torrent: TorrentInfo?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            // В окне за верхнюю панель окно можно перетаскивать
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
            .padding(horizontal = 40.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            engine.mediaTitle ?: engine.currentItem.title, color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.width(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (engine.itemCount > 1) InfoBadge("${engine.itemIndex + 1} из ${engine.itemCount}", accent = true)
            mediaBadges(engine).forEach { InfoBadge(it) }
            torrent?.let { InfoBadge("↓ ${formatSpeed(it.downloadSpeed)} · ${peersLabel(it.activePeers)}") }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Clock()
            if (engine.durationMs > 0) {
                val remaining = ((engine.durationMs - engine.positionMs) / engine.speed).toLong()
                Text(
                    "Конец в " + java.text.SimpleDateFormat("HH:mm").format(java.util.Date(System.currentTimeMillis() + remaining)),
                    color = AppColors.TextSecondary, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        RoundIconButton(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Полный экран (F)", onToggleFullscreen)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Filled.Close, "Закрыть", onClose)
    }
}

@Composable
private fun BottomControls(
    engine: MpvEngine,
    pendingSeekMs: Long?,
    timelineFocus: FocusRequester,
    timelineFocused: Boolean,
    onTimelineFocusChange: (Boolean) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenSheet: (SheetTab) -> Unit,
    onOpenEpisodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownPosition = pendingSeekMs ?: engine.positionMs
    Column(modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTime(shownPosition), color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 3.dp))
            Timeline(
                positionMs = shownPosition,
                bufferedMs = engine.bufferedMs,
                durationMs = engine.durationMs,
                focusRequester = timelineFocus,
                onFocusChange = onTimelineFocusChange,
                onSeek = { dir, repeat -> onSeekBy(dir * seekStepMs(repeat)) },
                onSeekTo = onSeekTo,
                onToggle = onTogglePlay,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            Text(
                if (engine.durationMs > 0) "−" + formatTime(engine.durationMs - shownPosition) else "--:--",
                color = AppColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayPauseButton(playing = !engine.paused, onClick = onTogglePlay)
            val hintsAlpha by animateFloatAsState(if (timelineFocused) 1f else 0.6f, label = "hints")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f).padding(start = 16.dp).alpha(hintsAlpha)) {
                KeyHint("Пробел", if (engine.paused) "смотреть" else "пауза")
                KeyHint("◀ ▶", "перемотка")
                KeyHint("Колесо", "громкость")
            }
            val chip = Modifier.height(46.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (engine.itemCount > 1) ChipButton("Серии", Icons.Filled.VideoLibrary, "${engine.itemIndex + 1} из ${engine.itemCount}", onOpenEpisodes, chip)
                ChipButton("Звук", Icons.Filled.Audiotrack, currentAudioSummary(engine), { onOpenSheet(SheetTab.Audio) }, chip.widthIn(max = 220.dp))
                ChipButton("Субтитры", Icons.Filled.Subtitles, currentSubtitleSummary(engine), { onOpenSheet(SheetTab.Subtitles) }, chip.widthIn(max = 190.dp))
                ChipButton("Настройки", Icons.Filled.Tune, if (engine.speed != 1f) "Скорость ${speedName(engine.speed)}" else null, { onOpenSheet(SheetTab.Picture) }, chip)
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
    onFocusChange: (Boolean) -> Unit,
    onSeek: (direction: Int, repeatCount: Int) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    var repeats by remember { mutableIntStateOf(0) }
    LaunchedEffect(focused) { onFocusChange(focused) }

    val trackHeight by animateDpAsState(if (active) 7.dp else 4.dp, spring(dampingRatio = 0.6f), label = "trackH")
    val thumb by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.5f), label = "thumb")
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val trackPx = with(LocalDensity.current) { trackHeight.toPx() }

    BoxWithConstraints(
        modifier
            .height(46.dp)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyUp -> { repeats = 0; false }
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.DirectionLeft -> { onSeek(-1, repeats++); true }
                    event.key == Key.DirectionRight -> { onSeek(1, repeats++); true }
                    event.isConfirm -> { onToggle(); true }
                    else -> false
                }
            }
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            // Клик по шкале — переход к этому месту
            .pointerInput(durationMs) {
                detectTapGestures { offset -> if (durationMs > 0) onSeekTo((offset.x / size.width * durationMs).toLong()) }
            },
    ) {
        val tooltipWidth = 64.dp
        Text(
            formatTime(positionMs), color = AppColors.OnFocused, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopStart)
                .offset(x = (maxWidth * fraction - tooltipWidth / 2).coerceIn(0.dp, maxWidth - tooltipWidth))
                .width(tooltipWidth).alpha(thumb.coerceIn(0f, 1f))
                .background(Color.White, RoundedCornerShape(6.dp)).padding(vertical = 2.dp),
        )
        ProgressTrack(fraction, buffered, trackPx, thumb, Modifier.fillMaxWidth().height(22.dp).align(Alignment.BottomStart))
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    Interactive(onClick = onClick, modifier = Modifier.size(46.dp), shape = CircleShape, activeScale = 1.12f, background = AppColors.GlassLight) { active ->
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Пауза" else "Смотреть",
            tint = if (active) AppColors.OnFocused else AppColors.TextPrimary, modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Interactive(onClick = onClick, modifier = Modifier.size(38.dp), shape = CircleShape, activeScale = 1.1f, background = AppColors.GlassLight) { active ->
        Icon(icon, description, tint = if (active) AppColors.OnFocused else AppColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Кнопка с подписью и текущим значением: «Звук · Русский · AC3 · 5.1». */
@Composable
private fun ChipButton(text: String, icon: ImageVector, value: String?, onClick: () -> Unit, modifier: Modifier) {
    Interactive(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50), activeScale = 1.08f, background = AppColors.GlassLight) { active ->
        val color = if (active) AppColors.OnFocused else AppColors.TextPrimary
        Row(Modifier.padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text, color = color, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (value != null) Text(value, color = color.copy(alpha = 0.65f), fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MiniSeekBar(engine: MpvEngine, targetMs: Long) {
    val fraction = if (engine.durationMs > 0) (targetMs.toFloat() / engine.durationMs).coerceIn(0f, 1f) else 0f
    val buffered = if (engine.durationMs > 0) (engine.bufferedMs.toFloat() / engine.durationMs).coerceIn(0f, 1f) else 0f
    val trackPx = with(LocalDensity.current) { 6.dp.toPx() }
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatTime(targetMs), color = AppColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (engine.durationMs > 0) Text(formatTime(engine.durationMs), color = AppColors.TextSecondary, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        ProgressTrack(fraction, buffered, trackPx, 1f, Modifier.fillMaxWidth().height(24.dp))
    }
}
