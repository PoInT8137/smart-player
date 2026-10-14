package app.smartplayer.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.smartplayer.desktop.AppModel
import app.smartplayer.desktop.LampaLink
import app.smartplayer.desktop.TorrServerState
import app.smartplayer.desktop.data.HistoryEntry
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.torrserver.formatSize
import kotlinx.coroutines.delay

/** Что показывает большой блок сверху — описание того, что под курсором. */
data class Hero(
    val tag: String,
    val title: String,
    val meta: String? = null,
    val description: String? = null,
    val seed: String? = null,
    val poster: String? = null,
)

private val WelcomeHero = Hero(
    tag = "ДОБРО ПОЖАЛОВАТЬ",
    title = "Что посмотрим?",
    description = "Откройте файл, вставьте magnet-ссылку или выберите раздачу из TorrServer.",
)

private enum class Dialog { Link, Settings }

@Composable
fun HomeScreen(model: AppModel, onOpenFile: () -> Unit) {
    var hero by remember { mutableStateOf(WelcomeHero) }
    var openTorrent by remember { mutableStateOf<TorrentInfo?>(null) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    LaunchedEffect(model.message) {
        if (model.message != null) {
            delay(5_000)
            model.message = null
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        AmbientBackground(hero.seed)

        Column(Modifier.fillMaxSize()) {
            TopBar(
                onOpenFile = onOpenFile,
                onOpenLink = { dialog = Dialog.Link },
                onSettings = { dialog = Dialog.Settings },
            )

            AnimatedContent(
                targetState = hero,
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 12 }) togetherWith fadeOut(tween(180)) },
                label = "hero",
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 48.dp),
            ) { HeroBlock(it) }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LampaBanner(model.lampa, onConnect = model::connectLampa)

                if (model.continueWatching.isNotEmpty()) {
                    Shelf("Продолжить просмотр") {
                        items(model.continueWatching, key = { it.uri }) { entry ->
                            HistoryCard(
                                entry,
                                onActive = { hero = entry.toHero() },
                                onClick = { model.playHistory(entry) },
                                onRemove = { model.removeFromHistory(entry) },
                            )
                        }
                    }
                }

                when (val ts = model.torrServer) {
                    is TorrServerState.Ready -> if (ts.torrents.isNotEmpty()) {
                        Shelf("TorrServer") {
                            items(ts.torrents, key = { it.hash }) { torrent ->
                                TorrentCard(
                                    torrent,
                                    onActive = {
                                        hero = Hero(
                                            tag = "TORRSERVER",
                                            title = torrent.title,
                                            meta = "${formatSize(torrent.size)} · ${torrent.statusRu()}",
                                            description = "Нажмите, чтобы выбрать файл или серию.",
                                            seed = torrent.title,
                                            poster = torrent.poster,
                                        )
                                    },
                                    onClick = { openTorrent = torrent },
                                )
                            }
                        }
                    } else {
                        EmptyHint("В TorrServer пока нет раздач. Вставьте magnet-ссылку — кнопка «Открыть ссылку» вверху.")
                    }
                    is TorrServerState.Unavailable -> TorrServerMissing(ts.url, onRetry = model::refreshTorrServer, onSettings = { dialog = Dialog.Settings })
                    TorrServerState.Loading -> Unit
                }
            }
        }

        // Затемнение под панелями и окнами; клик по нему закрывает
        AnimatedVisibility(openTorrent != null || dialog != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    openTorrent = null
                    dialog = null
                },
            )
        }

        val lastTorrent = rememberLast(openTorrent)
        AnimatedVisibility(
            visible = openTorrent != null,
            enter = slideInHorizontally(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            lastTorrent?.let { torrent ->
                TorrentPanel(
                    torrent = torrent,
                    load = { model.client.get(it) },
                    onPlay = { t, f ->
                        model.playTorrentFile(t, f)
                        openTorrent = null
                    },
                    onClose = { openTorrent = null },
                )
            }
        }

        val lastDialog = rememberLast(dialog)
        AnimatedVisibility(
            visible = dialog != null,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            when (lastDialog) {
                Dialog.Link -> OpenLinkDialog(
                    onOpen = model::openLink,
                    onTorrentAdded = {
                        dialog = null
                        openTorrent = it
                    },
                    onClose = { dialog = null },
                )
                Dialog.Settings -> SettingsDialog(
                    current = model.settings.torrServerUrl,
                    lampa = model.lampa,
                    onConnectLampa = model::connectLampa,
                    onSave = {
                        model.settings.torrServerUrl = it
                        model.refreshTorrServer()
                        dialog = null
                    },
                    onClose = { dialog = null },
                )
                null -> Unit
            }
        }

        StatusBar(
            nowPlaying = model.nowPlaying,
            message = model.message,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun HistoryEntry.toHero(): Hero {
    val source = when {
        uri.contains("/stream/") && uri.contains("link=") -> "TorrServer"
        uri.startsWith("http") -> "Сеть"
        else -> "Файл на компьютере"
    }
    val left = (durationMs - positionMs) / 60_000
    val meta = if (durationMs > 0) "Остановились на ${formatTime(positionMs)} из ${formatTime(durationMs)} · осталось $left мин"
    else "Остановились на ${formatTime(positionMs)}"
    return Hero("ПРОДОЛЖИТЬ ПРОСМОТР", title, meta, source, title)
}

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun TorrentInfo.statusRu(): String = when (stat) {
    0 -> "Добавлен"
    1 -> "Получение данных"
    2 -> "Предзагрузка"
    3 -> "Загружается"
    4 -> "Остановлен"
    5 -> "В списке"
    else -> statString
}

@Composable
private fun TopBar(onOpenFile: () -> Unit, onOpenLink: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(Brush.linearGradient(listOf(AppColors.Accent, AppColors.AccentSecondary)), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text("Smart Player", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Открыть файл", onOpenFile, icon = Icons.Filled.FolderOpen)
            PillButton("Открыть ссылку", onOpenLink, icon = Icons.Filled.Link)
            PillButton("Настройки", onSettings, icon = Icons.Filled.Settings)
            Spacer(Modifier.width(10.dp))
            Clock()
        }
    }
}

@Composable
private fun HeroBlock(hero: Hero) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 30.dp)) {
            Text(hero.tag, color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text(hero.title, color = AppColors.TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            hero.meta?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = AppColors.TextPrimary.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            hero.description?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = AppColors.TextSecondary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.75f))
            }
        }
        hero.poster?.let {
            PosterImage(it, hero.title, Modifier.padding(top = 18.dp).size(width = 128.dp, height = 192.dp).clip(RoundedCornerShape(12.dp)))
        }
    }
}

/** Живой фон: мягкие цветные пятна под цвет элемента под курсором, медленно дрейфуют. */
@Composable
private fun AmbientBackground(seed: String?) {
    val (a, b) = seed?.let { paletteFor(it) } ?: (AppColors.Accent to AppColors.AccentSecondary)
    val colorA by animateColorAsState(a, tween(900), label = "a")
    val colorB by animateColorAsState(b, tween(900), label = "b")
    val drift by rememberInfiniteTransition(label = "ambient").animateFloat(
        0f, 1f, infiniteRepeatable(tween(14_000), RepeatMode.Reverse), label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val c1 = Offset(size.width * (0.75f + 0.1f * drift), size.height * (0.05f + 0.1f * drift))
        drawCircle(Brush.radialGradient(listOf(colorB.copy(alpha = 0.5f), Color.Transparent), c1, size.width * 0.5f), size.width * 0.5f, c1)
        val c2 = Offset(size.width * (0.15f - 0.08f * drift), size.height * (0.3f + 0.2f * drift))
        drawCircle(Brush.radialGradient(listOf(colorA.copy(alpha = 0.35f), Color.Transparent), c2, size.width * 0.4f), size.width * 0.4f, c2)
        drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to AppColors.Background.copy(alpha = 0.92f)))
    }
}

@Composable
private fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(start = 48.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onActive: () -> Unit, onClick: () -> Unit, onRemove: () -> Unit) {
    Interactive(
        onClick = onClick,
        onActiveChange = { if (it) onActive() },
        modifier = Modifier.size(width = 256.dp, height = 144.dp),
        activeScale = 1.07f,
        activeBorder = true,
        activeBackground = AppColors.Surface,
        contentAlignment = Alignment.BottomStart,
    ) { active ->
        CardArt(entry.title) {
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(entry.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x40FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(
                        Modifier.fillMaxWidth(entry.progress.coerceIn(0.02f, 1f)).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(AppColors.Accent, AppColors.AccentSecondary)), RoundedCornerShape(2.dp)),
                    )
                }
            }
            // Крестик «убрать из списка» появляется при наведении
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).alpha(if (active) 1f else 0f).size(28.dp)
                    .background(Color(0x99000000), CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Убрать из списка", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun TorrentCard(torrent: TorrentInfo, onActive: () -> Unit, onClick: () -> Unit) {
    Interactive(
        onClick = onClick,
        onActiveChange = { if (it) onActive() },
        modifier = Modifier.size(width = 140.dp, height = 210.dp),
        activeScale = 1.08f,
        activeBorder = true,
        activeBackground = AppColors.Surface,
        contentAlignment = Alignment.BottomStart,
    ) {
        PosterImage(torrent.poster, torrent.title, Modifier.fillMaxSize())
        if (torrent.poster == null) {
            Text(torrent.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        }
    }
}

/** Предложение подключить Lampa, если она открыта и настроить её молча нельзя. */
@Composable
private fun LampaBanner(link: LampaLink, onConnect: () -> Unit) {
    val visible = link is LampaLink.NeedsClose || link is LampaLink.Working || link is LampaLink.Failed
    val shown = rememberLast(link.takeIf { visible })
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut()) {
        Row(
            Modifier.padding(horizontal = 48.dp).fillMaxWidth()
                .background(AppColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(16.dp)).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Открывать фильмы из Lampa в Smart Player", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Caption(
                    when (shown) {
                        is LampaLink.Failed -> "Не получилось: ${shown.reason}"
                        LampaLink.Working -> "Закрываем Lampa, меняем настройку и открываем снова…"
                        else -> "Lampa сейчас открыта. Нажмите кнопку — она закроется на пару секунд, запомнит Smart Player как плеер для торрентов и откроется снова."
                    },
                    lines = 2,
                )
            }
            Spacer(Modifier.width(20.dp))
            if (shown != LampaLink.Working) PillButton("Подключить к Lampa", onConnect, primary = true, icon = Icons.Filled.Link)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = AppColors.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 48.dp))
}

@Composable
private fun TorrServerMissing(url: String, onRetry: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 48.dp).background(AppColors.Surface.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).padding(24.dp),
    ) {
        Text("TorrServer не найден", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Caption("Адрес: $url. Запустите TorrServer или укажите адрес приставки в настройках.", lines = 2)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Проверить снова", onRetry, icon = Icons.Filled.Refresh)
            PillButton("Указать адрес", onSettings, icon = Icons.Filled.Settings)
        }
    }
}

/** Строка состояния: «сейчас играет» и короткие сообщения. */
@Composable
private fun StatusBar(nowPlaying: String?, message: String?, modifier: Modifier) {
    val text = message ?: nowPlaying?.let { "Сейчас играет: $it" }
    val shown = rememberLast(text)
    AnimatedVisibility(text != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = modifier.padding(bottom = 24.dp)) {
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
        Row(
            Modifier.background(Color(0xF2171920), RoundedCornerShape(50)).padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (message == null) {
                Box(Modifier.size(10.dp).alpha(pulse).background(AppColors.Accent, CircleShape))
                Spacer(Modifier.width(12.dp))
            }
            Text(shown ?: "", color = AppColors.TextPrimary, fontSize = 15.sp)
        }
    }
}

/** Последнее ненулевое значение — чтобы анимация исчезновения показывала старый контент. */
@Composable
fun <T> rememberLast(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}
