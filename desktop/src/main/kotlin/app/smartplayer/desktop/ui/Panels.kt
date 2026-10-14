package app.smartplayer.desktop.ui

import app.smartplayer.desktop.LampaLink
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tvplayer.torrserver.TorrServerClient
import app.tvplayer.torrserver.TorrentFile
import app.tvplayer.torrserver.TorrentInfo
import app.tvplayer.torrserver.formatSize
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

/** Постер из TorrServer; пока грузится или если его нет — градиентная обложка. */
@Composable
fun PosterImage(url: String?, seed: String, modifier: Modifier = Modifier) {
    if (url == null) {
        CardArt(seed, modifier, letterSize = 80.sp)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { CardArt(seed, letterSize = 80.sp) },
        error = { CardArt(seed, letterSize = 80.sp) },
    )
}

/** Не даём клику «провалиться» сквозь панель на затемнение. */
private fun Modifier.swallowClicks() = clickable(interactionSource = MutableInteractionSource(), indication = null) {}

/**
 * Панель раздачи: список видео из TorrServer. Для новой раздачи TorrServer сначала
 * получает метаданные — ждём до ~30 с. Фильм из одного файла запускается сразу.
 */
@Composable
fun TorrentPanel(
    torrent: TorrentInfo,
    load: suspend (String) -> TorrentInfo,
    onPlay: (TorrentInfo, TorrentFile) -> Unit,
    onClose: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val details by produceState<Result<TorrentInfo>?>(null, torrent.hash, attempt) {
        value = null
        repeat(30) {
            val r = runCatching { load(torrent.hash) }
            if (r.isFailure || r.getOrNull()?.videoFiles?.isNotEmpty() == true) {
                value = r
                return@produceState
            }
            delay(1_000)
        }
        value = runCatching { load(torrent.hash) }
    }
    val loaded = details?.getOrNull()
    LaunchedEffect(loaded) {
        val videos = loaded?.videoFiles ?: return@LaunchedEffect
        if (videos.size == 1) onPlay(loaded, videos.first())
    }

    Column(
        Modifier.fillMaxHeight().width(560.dp).background(Color(0xFF101217)).swallowClicks().padding(32.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PosterImage(torrent.poster, torrent.title, Modifier.size(width = 90.dp, height = 135.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("TORRSERVER", color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text(torrent.title, color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Caption(formatSize(torrent.size))
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.height(22.dp))
        when {
            details == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Caption("Получаем список файлов…", size = 15.sp)
            }
            loaded == null || loaded.videoFiles.isEmpty() -> {
                Caption(
                    if (loaded == null) "Не удалось получить список файлов. Проверьте, что TorrServer запущен."
                    else "В раздаче нет видеофайлов или у неё нет сидов.",
                    size = 15.sp, lines = 3,
                )
                Spacer(Modifier.height(14.dp))
                PillButton("Повторить", { attempt++ })
            }
            else -> {
                val videos = loaded.videoFiles
                Text("${videos.size} ВИДЕО", color = AppColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(videos, key = { _, f -> f.id }) { i, file ->
                        Interactive(
                            onClick = { onPlay(loaded, file) },
                            modifier = Modifier.fillMaxWidth(),
                            activeScale = 1.02f,
                            background = Color.Transparent,
                            contentAlignment = Alignment.CenterStart,
                        ) { active ->
                            val primary = if (active) AppColors.OnFocused else AppColors.TextPrimary
                            val secondary = if (active) AppColors.OnFocused.copy(alpha = 0.65f) else AppColors.TextSecondary
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                                    if (active) Icon(Icons.Filled.PlayArrow, null, tint = primary, modifier = Modifier.size(20.dp))
                                    else Text("${i + 1}", color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(file.displayName, color = primary, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(formatSize(file.length), color = secondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Interactive(onClick = onClose, modifier = Modifier.size(34.dp), shape = CircleShape, background = AppColors.GlassLight) { active ->
        Icon(Icons.Filled.Close, "Закрыть", tint = if (active) AppColors.OnFocused else AppColors.TextPrimary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DialogCard(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.width(620.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF15171D)).swallowClicks().padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            CloseButton(onClose)
        }
        Spacer(Modifier.height(18.dp))
        content()
    }
}

@Composable
private fun field() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppColors.Accent,
    unfocusedBorderColor = AppColors.Stroke,
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    cursorColor = AppColors.Accent,
)

/** Вставить magnet / http-ссылку / путь к файлу. */
@Composable
fun OpenLinkDialog(
    onOpen: suspend (String) -> TorrentInfo?,
    onTorrentAdded: (TorrentInfo) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(clipboardText().takeIf { it.looksLikeLink() } ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (text.isBlank() || busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { onOpen(text) }
                .onSuccess { torrent -> if (torrent != null) onTorrentAdded(torrent) else onClose() }
                .onFailure { error = it.message ?: "Не удалось открыть ссылку" }
            busy = false
        }
    }

    DialogCard("Открыть ссылку", onClose) {
        Caption("Magnet-ссылка или хеш — добавится в TorrServer. Ссылка http(s) или путь к файлу — откроется сразу.", lines = 2)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("magnet:?xt=urn:btih:… или https://…", color = AppColors.TextMuted) },
            colors = field(),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { submit(); true } else false
            },
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF616E), fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (busy) "Открываем…" else "Открыть", ::submit, icon = Icons.Filled.PlayArrow, primary = true)
            PillButton("Вставить из буфера", { text = clipboardText() }, icon = Icons.Filled.ContentPaste)
        }
    }
}

/** Адрес TorrServer с проверкой подключения. */
@Composable
fun SettingsDialog(current: String, lampa: LampaLink, onConnectLampa: () -> Unit, onSave: (String) -> Unit, onClose: () -> Unit) {
    var url by remember { mutableStateOf(current) }
    var check by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DialogCard("Настройки", onClose) {
        Text("Адрес TorrServer", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Caption("На этом компьютере — http://127.0.0.1:8090. На приставке — её IP, например http://192.168.1.45:8090", lines = 2)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = url, onValueChange = { url = it; check = null }, singleLine = true, colors = field(), modifier = Modifier.fillMaxWidth())
        check?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = if (it.startsWith("✓")) Color(0xFFA5E075) else Color(0xFFFF616E), fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Сохранить", { onSave(url) }, primary = true)
            PillButton("Проверить", {
                check = "Проверяем…"
                scope.launch {
                    check = runCatching { TorrServerClient(url.trim()).echo() }
                        .fold({ "✓ Подключено: $it" }, { "Не отвечает: ${it.message ?: "нет связи"}" })
                }
            })
        }
        if (lampa != LampaLink.Unavailable) {
            Spacer(Modifier.height(26.dp))
            Text("Lampa для ПК", color = AppColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Caption(
                when (lampa) {
                    LampaLink.Linked -> "✓ Торренты из Lampa открываются в Smart Player"
                    LampaLink.Working -> "Настраиваем Lampa…"
                    is LampaLink.Failed -> "Не получилось: ${lampa.reason}"
                    else -> "Lampa открывает фильмы в другом плеере"
                },
                lines = 2,
            )
            Spacer(Modifier.height(10.dp))
            if (lampa != LampaLink.Working) {
                PillButton(if (lampa == LampaLink.Linked) "Подключить заново" else "Подключить к Lampa", onConnectLampa)
            }
        }
    }
}

private fun clipboardText(): String = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
}.getOrDefault("").trim()

private fun String.looksLikeLink() =
    startsWith("magnet:", true) || startsWith("http://") || startsWith("https://") || Regex("^[0-9a-fA-F]{40}$").matches(this)
