package app.tvplayer.ui.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tvplayer.data.HistoryEntry
import app.tvplayer.demo.DemoItem
import app.tvplayer.player.ui.formatTime
import app.tvplayer.torrserver.TorrentFile
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.torrserver.formatSize
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.CardArt
import app.tvplayer.ui.Clock
import app.tvplayer.ui.TvButton
import app.tvplayer.ui.TvFocusable
import app.tvplayer.ui.fadingTopEdge
import app.tvplayer.ui.paletteFor
import app.tvplayer.ui.safeRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Что показывает большой блок сверху — описание элемента в фокусе. */
private data class Hero(
    val tag: String,
    val title: String,
    val meta: String?,
    val description: String?,
    val seed: String?,
    val poster: String? = null,
)

private val WelcomeHero = Hero(
    tag = "ДОБРО ПОЖАЛОВАТЬ",
    title = "Что посмотрим?",
    meta = null,
    description = "Запускайте фильмы и торренты из Lampa — они откроются здесь.",
    seed = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    continueWatching: List<HistoryEntry>,
    demos: List<DemoItem>,
    resultMessage: String?,
    onDismissResult: () -> Unit,
    onPlayHistory: (HistoryEntry) -> Unit,
    onRemoveHistory: (HistoryEntry) -> Unit,
    onPlayDemo: (DemoItem) -> Unit,
    onOpenFile: () -> Unit,
    torrServer: TorrServerState,
    loadTorrent: suspend (String) -> TorrentInfo,
    onPlayTorrentFile: (TorrentInfo, TorrentFile) -> Unit,
    onRetryTorrServer: () -> Unit,
) {
    var hero by remember { mutableStateOf(WelcomeHero) }
    var guideOpen by remember { mutableStateOf(false) }
    var openTorrent by remember { mutableStateOf<TorrentInfo?>(null) }
    val shelfListState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        firstFocus.safeRequestFocus()
    }
    LaunchedEffect(resultMessage) {
        if (resultMessage != null) {
            delay(6_000)
            onDismissResult()
        }
    }
    BackHandler(enabled = guideOpen) { guideOpen = false }
    BackHandler(enabled = openTorrent != null) { openTorrent = null }

    // Вертикальную прокрутку полок ведём сами (полка с фокусом — к верху), встроенную отключаем,
    // иначе они спорят и список останавливается «между» полками. В рядах — обычное поведение.
    val rowBringIntoView = LocalBringIntoViewSpec.current
    CompositionLocalProvider(
        LocalShelfListState provides shelfListState,
        LocalRowBringIntoViewSpec provides rowBringIntoView,
        LocalBringIntoViewSpec provides NoBringIntoView,
    ) {
        Box(Modifier.fillMaxSize()) {
            AmbientBackground(hero.seed)

            Column(Modifier.fillMaxSize()) {
                TopBar()

                AnimatedContent(
                    targetState = hero,
                    transitionSpec = {
                        (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 10 }) togetherWith fadeOut(tween(200))
                    },
                    label = "hero",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(horizontal = 56.dp),
                ) { h -> HeroBlock(h) }

                LazyColumn(
                    state = shelfListState,
                    modifier = Modifier.fadingTopEdge(64.dp),
                    // Запас снизу, чтобы даже последняя полка могла подняться к верху списка
                    contentPadding = PaddingValues(bottom = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var shelfIndex = 0
                    var firstAssigned = false
                    fun takeFirst(): Boolean = if (!firstAssigned) {
                        firstAssigned = true
                        true
                    } else {
                        false
                    }

                    if (continueWatching.isNotEmpty()) {
                        val index = shelfIndex++
                        val withFirst = takeFirst()
                        item(key = "continue") {
                            Shelf("Продолжить просмотр", index) {
                                itemsIndexed(continueWatching, key = { _, e -> e.uri }) { i, entry ->
                                    HistoryCard(
                                        entry = entry,
                                        modifier = Modifier
                                            .animateItem()
                                            .then(if (withFirst && i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                            .firstInShelf(i),
                                        onFocused = { hero = entry.toHero() },
                                        onClick = { onPlayHistory(entry) },
                                        onLongClick = { onRemoveHistory(entry) },
                                    )
                                }
                            }
                        }
                    }

                    val torrents = (torrServer as? TorrServerState.Ready)?.torrents.orEmpty()
                    if (torrents.isNotEmpty()) {
                        val index = shelfIndex++
                        val withFirst = takeFirst()
                        item(key = "torrserver") {
                            Shelf("TorrServer", index) {
                                itemsIndexed(torrents, key = { _, t -> t.hash }) { i, torrent ->
                                    TorrentCard(
                                        torrent = torrent,
                                        modifier = (if (withFirst && i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                            .firstInShelf(i),
                                        onFocused = {
                                            hero = Hero(
                                                tag = "TORRSERVER",
                                                title = torrent.title,
                                                meta = "${formatSize(torrent.size)} · ${torrent.statusRu()}",
                                                description = "Нажмите OK, чтобы выбрать файл или серию.",
                                                seed = torrent.title,
                                                poster = torrent.poster,
                                            )
                                        },
                                        onClick = { openTorrent = torrent },
                                    )
                                }
                            }
                        }
                    }

                    if (demos.isNotEmpty()) {
                        val index = shelfIndex++
                        val withFirst = takeFirst()
                        item(key = "demo") {
                            Shelf("Демо: как из Lampa", index) {
                                itemsIndexed(demos, key = { _, d -> d.title }) { i, demo ->
                                    DemoCard(
                                        demo = demo,
                                        modifier = (if (withFirst && i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                            .firstInShelf(i),
                                        onFocused = {
                                            hero = Hero("ДЕМО", demo.title, demo.subtitle, demo.description, demo.title)
                                        },
                                        onClick = { onPlayDemo(demo) },
                                    )
                                }
                            }
                        }
                    }

                    val index = shelfIndex
                    val withFirst = takeFirst()
                    item(key = "actions") {
                        Shelf("Меню", index) {
                            item {
                                ActionTile(
                                    icon = Icons.Filled.FolderOpen,
                                    title = "Открыть файл",
                                    modifier = (if (withFirst) Modifier.focusRequester(firstFocus) else Modifier).firstInShelf(0),
                                    onFocused = {
                                        hero = Hero("ФАЙЛЫ", "Открыть файл", null, "Видео с флешки или из памяти приставки.", null)
                                    },
                                    onClick = onOpenFile,
                                )
                            }
                            if (torrServer is TorrServerState.Unavailable) {
                                item {
                                    ActionTile(
                                        icon = Icons.Filled.Refresh,
                                        title = "TorrServer",
                                        onFocused = {
                                            hero = Hero(
                                                "TORRSERVER",
                                                "TorrServer не найден",
                                                torrServer.url,
                                                "Запустите TorrServer на приставке и нажмите OK, чтобы проверить снова.",
                                                null,
                                            )
                                        },
                                        onClick = onRetryTorrServer,
                                    )
                                }
                            }
                            item {
                                ActionTile(
                                    icon = Icons.Filled.Link,
                                    title = "Подключить Lampa",
                                    onFocused = {
                                        hero = Hero(
                                            "LAMPA",
                                            "Подключить Lampa",
                                            null,
                                            "Три шага, чтобы фильмы и торренты из Lampa открывались в этом плеере.",
                                            null,
                                        )
                                    },
                                    onClick = { guideOpen = true },
                                )
                            }
                        }
                    }
                }
            }

            ResultBanner(resultMessage, Modifier.align(Alignment.BottomCenter))

            // Затемняем главный экран под боковыми панелями, чтобы текст не просвечивал
            AnimatedVisibility(visible = guideOpen || openTorrent != null, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xB3000000)),
                )
            }

            AnimatedVisibility(
                visible = guideOpen,
                enter = slideInHorizontally(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                LampaGuide(onClose = { guideOpen = false })
            }

            val shownTorrent = openTorrent ?: rememberLastTorrent(openTorrent)
            AnimatedVisibility(
                visible = openTorrent != null,
                enter = slideInHorizontally(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                shownTorrent?.let { torrent ->
                    TorrentPanel(
                        torrent = torrent,
                        load = loadTorrent,
                        onPlay = onPlayTorrentFile,
                        onClose = { openTorrent = null },
                    )
                }
            }
        }
    }
}

/** Список полок — чтобы полка с фокусом могла прокрутить себя к верху. */
private val LocalShelfListState = compositionLocalOf<LazyListState?> { null }

/** Первая карточка полки — сюда встаёт фокус при первом входе в ряд. */
private val LocalShelfFirst = compositionLocalOf<FocusRequester?> { null }

/** Обычное поведение «показать элемент с фокусом» — для горизонтальных рядов. */
private val LocalRowBringIntoViewSpec = compositionLocalOf<BringIntoViewSpec?> { null }

@OptIn(ExperimentalFoundationApi::class)
private object NoBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
private fun Modifier.firstInShelf(index: Int): Modifier {
    val first = LocalShelfFirst.current
    return if (index == 0 && first != null) focusRequester(first) else this
}

/** Последняя открытая раздача — чтобы панель красиво уехала, а не исчезла пустой. */
@Composable
private fun rememberLastTorrent(current: TorrentInfo?): TorrentInfo? {
    val holder = remember { arrayOfNulls<TorrentInfo>(1) }
    if (current != null) holder[0] = current
    return holder[0]
}

private fun HistoryEntry.toHero(): Hero {
    val uri = Uri.parse(uri)
    val source = when (uri.scheme) {
        "http", "https" -> if (uri.path.orEmpty().startsWith("/stream")) "TorrServer" else "Сеть · ${uri.host}"
        else -> "Файл на устройстве"
    }
    val left = durationMs - positionMs
    val meta = if (durationMs > 0) {
        "Остановились на ${formatTime(positionMs)} из ${formatTime(durationMs)} · осталось ${left / 60_000} мин"
    } else {
        "Остановились на ${formatTime(positionMs)}"
    }
    return Hero("ПРОДОЛЖИТЬ ПРОСМОТР", title, meta, source, title)
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(Brush.linearGradient(listOf(AppColors.Accent, AppColors.AccentSecondary)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("Smart Player", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Clock(big = false)
    }
}

@Composable
private fun HeroBlock(hero: Hero) {
    Row(Modifier.fillMaxSize()) {
        HeroText(hero, Modifier.weight(1f))
        hero.poster?.let { poster ->
            Spacer(Modifier.width(24.dp))
            PosterImage(
                poster,
                hero.title,
                Modifier
                    .padding(top = 16.dp)
                    .size(width = 110.dp, height = 165.dp)
                    .clip(RoundedCornerShape(12.dp)),
                letterSize = 70,
            )
        }
    }
}

@Composable
private fun HeroText(hero: Hero, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .padding(top = 28.dp),
    ) {
        Text(hero.tag, color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            hero.title,
            color = AppColors.TextPrimary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        hero.meta?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = AppColors.TextPrimary.copy(alpha = 0.85f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        hero.description?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = AppColors.TextSecondary,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
        }
    }
}

/** Живой фон: мягкие цветные пятна под цвет элемента в фокусе, медленно дрейфуют. */
@Composable
private fun AmbientBackground(seed: String?) {
    val (a, b) = seed?.let { paletteFor(it) } ?: (AppColors.Accent to AppColors.AccentSecondary)
    val colorA by animateColorAsState(a, tween(900), label = "ambientA")
    val colorB by animateColorAsState(b, tween(900), label = "ambientB")
    val drift by rememberInfiniteTransition(label = "ambient").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(AppColors.Background)
        val c1 = Offset(size.width * (0.72f + 0.12f * drift), size.height * (0.05f + 0.1f * drift))
        drawCircle(
            Brush.radialGradient(listOf(colorB.copy(alpha = 0.55f), Color.Transparent), center = c1, radius = size.width * 0.55f),
            radius = size.width * 0.55f,
            center = c1,
        )
        val c2 = Offset(size.width * (0.2f - 0.1f * drift), size.height * (0.3f + 0.2f * drift))
        drawCircle(
            Brush.radialGradient(listOf(colorA.copy(alpha = 0.4f), Color.Transparent), center = c2, radius = size.width * 0.45f),
            radius = size.width * 0.45f,
            center = c2,
        )
        drawRect(Brush.verticalGradient(0.3f to Color.Transparent, 1f to AppColors.Background.copy(alpha = 0.9f)))
    }
}

/** Полка с заголовком; появляется с небольшой задержкой по порядку — «каскадом». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Shelf(
    title: String,
    index: Int,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val listState = LocalShelfListState.current
    val scope = rememberCoroutineScope()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(500, delayMillis = 100 + index * 120)) +
            slideInVertically(tween(500, delayMillis = 100 + index * 120)) { it / 3 },
    ) {
        // Ряд с фокусом плавно поднимается к верху списка — предыдущие уходят за край целиком
        Column(
            Modifier.onFocusChanged {
                if (it.hasFocus && listState != null) scope.launch { listState.animateScrollToItem(index) }
            },
        ) {
            Text(
                title,
                color = AppColors.TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 56.dp, top = 8.dp),
            )
            val first = remember { FocusRequester() }
            CompositionLocalProvider(
                LocalShelfFirst provides first,
                LocalBringIntoViewSpec provides (LocalRowBringIntoViewSpec.current ?: LocalBringIntoViewSpec.current),
            ) {
                // Возвращаемся в ряд на ту же карточку; при первом входе — на первую
                LazyRow(
                    modifier = Modifier.focusRestorer(first),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocusChange = { if (it) onFocused() },
        modifier = modifier.size(width = 256.dp, height = 144.dp),
        focusedScale = 1.1f,
        focusBorder = true,
        background = AppColors.Surface,
        focusedBackground = AppColors.Surface,
        contentAlignment = Alignment.BottomStart,
    ) { _ ->
        CardArt(seed = entry.title, letterSize = 120.sp) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            ) {
                Text(entry.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x40FFFFFF), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(entry.progress.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(AppColors.Accent, AppColors.AccentSecondary)),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoCard(
    demo: DemoItem,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        onFocusChange = { if (it) onFocused() },
        modifier = modifier.size(width = 256.dp, height = 144.dp),
        focusedScale = 1.1f,
        focusBorder = true,
        background = AppColors.Surface,
        focusedBackground = AppColors.Surface,
        contentAlignment = Alignment.BottomStart,
    ) { _ ->
        CardArt(seed = demo.title, letterSize = 120.sp) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            ) {
                Text(demo.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(demo.subtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFocusable(
        onClick = onClick,
        onFocusChange = { if (it) onFocused() },
        modifier = modifier.size(width = 200.dp, height = 112.dp),
        focusedScale = 1.1f,
        background = AppColors.GlassLight,
        focusedBackground = Color.White,
    ) { focused ->
        val color = if (focused) AppColors.OnFocused else AppColors.TextPrimary
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Сообщение о том, что плеер вернул вызвавшему приложению (Lampa). */
@Composable
private fun ResultBanner(message: String?, modifier: Modifier) {
    val shown = remember { mutableStateOf<String?>(null) }
    if (message != null) shown.value = message
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Row(
            Modifier
                .background(Color(0xF2171920), RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = AppColors.Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(shown.value ?: "", color = AppColors.TextPrimary, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LampaGuide(onClose: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { closeFocus.safeRequestFocus() }
    Column(
        Modifier
            .fillMaxHeight()
            .width(520.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xE6101217), Color(0xFA101217))))
            .padding(horizontal = 40.dp, vertical = 48.dp),
    ) {
        Text("LAMPA", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Смотреть из Lampa", color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        listOf(
            "Откройте в Lampa настройки плеера и выберите внешний плеер Android.",
            "Запустите фильм или торрент. В списке приложений выберите Smart Player и нажмите «Всегда».",
            "Готово: позиция просмотра сама вернётся в Lampa, а серии будут переключаться прямо в плеере.",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(bottom = 22.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(AppColors.GlassLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Text(step, color = AppColors.TextSecondary, fontSize = 17.sp, lineHeight = 24.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        TvButton("Понятно", onClick = onClose, modifier = Modifier.focusRequester(closeFocus))
    }
}
