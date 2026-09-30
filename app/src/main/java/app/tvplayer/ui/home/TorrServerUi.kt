package app.tvplayer.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tvplayer.torrserver.TorrentFile
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.torrserver.formatSize
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.CardArt
import app.tvplayer.ui.TvButton
import app.tvplayer.ui.TvFocusable
import app.tvplayer.ui.safeRequestFocus
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay

/** Что известно о TorrServer на главном экране. */
sealed interface TorrServerState {
    data object Loading : TorrServerState
    data class Unavailable(val url: String) : TorrServerState
    data class Ready(val url: String, val torrents: List<TorrentInfo>) : TorrServerState
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

/** Постер из TorrServer (его туда кладёт Lampa); пока грузится или если его нет — градиентная обложка. */
@Composable
fun PosterImage(url: String?, seed: String, modifier: Modifier = Modifier, letterSize: Int = 90) {
    if (url == null) {
        CardArt(seed = seed, modifier = modifier, letterSize = letterSize.sp)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { CardArt(seed = seed, letterSize = letterSize.sp) },
        error = { CardArt(seed = seed, letterSize = letterSize.sp) },
    )
}

@Composable
fun TorrentCard(
    torrent: TorrentInfo,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    TvFocusable(
        onClick = onClick,
        onFocusChange = { if (it) onFocused() },
        modifier = modifier.size(width = 132.dp, height = 198.dp),
        focusedScale = 1.1f,
        focusBorder = true,
        background = AppColors.Surface,
        focusedBackground = AppColors.Surface,
        contentAlignment = Alignment.BottomStart,
    ) { _ ->
        PosterImage(torrent.poster, torrent.title, Modifier.fillMaxSize())
        // Название поверх постера — только если постера нет, иначе он сам всё говорит
        if (torrent.poster == null) {
            Text(
                torrent.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/**
 * Панель раздачи: запрашивает у TorrServer список файлов и показывает видео.
 * Для новой раздачи TorrServer сначала получает метаданные — ждём до ~30 с.
 * Если видеофайл один — сразу запускаем.
 */
@Composable
fun TorrentPanel(
    torrent: TorrentInfo,
    load: suspend (String) -> TorrentInfo,
    onPlay: (TorrentInfo, TorrentFile) -> Unit,
    onClose: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val details by produceState<Result<TorrentInfo>?>(initialValue = null, attempt) {
        value = null
        repeat(30) {
            val result = runCatching { load(torrent.hash) }
            if (result.isFailure || result.getOrNull()?.videoFiles?.isNotEmpty() == true) {
                value = result
                return@produceState
            }
            delay(1_000)
        }
        value = runCatching { load(torrent.hash) }
    }

    val loaded = details?.getOrNull()
    LaunchedEffect(loaded) {
        val videos = loaded?.videoFiles ?: return@LaunchedEffect
        if (videos.size == 1) {
            onPlay(loaded, videos.first())
            onClose()
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(560.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF101217), Color(0xFF101217))))
            .padding(horizontal = 36.dp, vertical = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PosterImage(
                torrent.poster,
                torrent.title,
                Modifier
                    .size(width = 84.dp, height = 126.dp)
                    .clip(RoundedCornerShape(10.dp)),
                letterSize = 60,
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text("TORRSERVER", color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    torrent.title,
                    color = AppColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(formatSize(torrent.size), color = AppColors.TextSecondary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))

        when {
            details == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Text("Получаем список файлов…", color = AppColors.TextSecondary, fontSize = 16.sp)
            }
            loaded == null -> ErrorBlock("Не удалось получить список файлов. Проверьте, что TorrServer запущен.") { attempt++ }
            loaded.videoFiles.isEmpty() -> ErrorBlock("В раздаче нет видеофайлов или у неё нет сидов.") { attempt++ }
            else -> FileList(loaded, onPlay = { onPlay(loaded, it) })
        }
    }
}

@Composable
private fun ErrorBlock(text: String, onRetry: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.safeRequestFocus() }
    Text(text, color = AppColors.TextSecondary, fontSize = 16.sp)
    Spacer(Modifier.height(18.dp))
    TvButton("Повторить", onClick = onRetry, modifier = Modifier.focusRequester(focus))
}

@Composable
private fun FileList(torrent: TorrentInfo, onPlay: (TorrentFile) -> Unit) {
    val videos = torrent.videoFiles
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.safeRequestFocus() }

    Text(
        "${videos.size} видео",
        color = AppColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        itemsIndexed(videos, key = { _, f -> f.id }) { i, file ->
            TvFocusable(
                onClick = { onPlay(file) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                focusedScale = 1.03f,
                background = Color.Transparent,
                contentAlignment = Alignment.CenterStart,
            ) { focused ->
                val primary = if (focused) AppColors.OnFocused else AppColors.TextPrimary
                val secondary = if (focused) AppColors.OnFocused.copy(alpha = 0.65f) else AppColors.TextSecondary
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        if (focused) {
                            Icon(Icons.Filled.PlayArrow, null, tint = primary, modifier = Modifier.size(22.dp))
                        } else {
                            Text("${i + 1}", color = secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.displayName, color = primary, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatSize(file.length), color = secondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
