package app.tvplayer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tvplayer.data.HistoryEntry
import app.tvplayer.data.PlaybackHistory
import app.tvplayer.player.PlayerActivity
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.TVPlayerTheme
import app.tvplayer.ui.TvButton
import app.tvplayer.ui.TvFocusable
import app.tvplayer.ui.safeRequestFocus

class MainActivity : ComponentActivity() {

    private lateinit var history: PlaybackHistory
    private var continueWatching by mutableStateOf<List<HistoryEntry>>(emptyList())

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        // Сохраняем доступ, чтобы потом можно было продолжить просмотр из истории
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        play(uri, null)
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { play(it, null) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = PlaybackHistory(this)
        setContent {
            TVPlayerTheme {
                HomeScreen(
                    continueWatching = continueWatching,
                    onOpenFile = ::pickFile,
                    onPlay = { play(Uri.parse(it.uri), it.title) },
                    onRemove = {
                        history.remove(it.uri)
                        refresh()
                        Toast.makeText(this, "Убрано из списка", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        continueWatching = history.continueWatching()
    }

    private fun pickFile() {
        try {
            openDocument.launch(arrayOf("video/*", "audio/*"))
        } catch (_: ActivityNotFoundException) {
            try {
                getContent.launch("video/*")
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    "На устройстве нет выбора файлов. Встроенный файловый браузер появится в следующих версиях.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun play(uri: Uri, title: String?) {
        val intent = Intent(this, PlayerActivity::class.java)
            .setData(uri)
            .putExtra("title", title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }
}

@Composable
private fun HomeScreen(
    continueWatching: List<HistoryEntry>,
    onOpenFile: () -> Unit,
    onPlay: (HistoryEntry) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.safeRequestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(horizontal = 56.dp, vertical = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(AppColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("TV Player", color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Торренты, фильмы и сериалы на большом экране", color = AppColors.TextSecondary, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(
                text = "Открыть файл",
                icon = Icons.Filled.FolderOpen,
                onClick = onOpenFile,
                modifier = Modifier.focusRequester(initialFocus),
            )
        }

        Spacer(Modifier.height(36.dp))

        if (continueWatching.isNotEmpty()) {
            Text("Продолжить просмотр", color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Долгое нажатие OK — убрать из списка", color = AppColors.TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
            ) {
                items(continueWatching, key = { it.uri }) { entry ->
                    HistoryCard(entry, onClick = { onPlay(entry) }, onLongClick = { onRemove(entry) })
                }
            }
        } else {
            LampaHint()
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    TvFocusable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.size(width = 300.dp, height = 150.dp),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        val primary = if (focused) AppColors.OnFocused else AppColors.TextPrimary
        val secondary = if (focused) AppColors.OnFocused.copy(alpha = 0.7f) else AppColors.TextSecondary
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(entry.title, color = primary, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Column {
                val leftMin = ((entry.durationMs - entry.positionMs) / 60_000).coerceAtLeast(1)
                Text(
                    text = if (entry.durationMs > 0) "Осталось $leftMin мин" else "Продолжить",
                    color = secondary,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AppColors.Accent,
                    trackColor = if (focused) AppColors.OnFocused.copy(alpha = 0.2f) else AppColors.SurfaceRaised,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun LampaHint() {
    Column(
        modifier = Modifier
            .width(720.dp)
            .background(AppColors.Surface, RoundedCornerShape(16.dp))
            .padding(28.dp),
    ) {
        Text("Как смотреть из Lampa", color = AppColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        listOf(
            "1. В настройках плеера Lampa выберите внешний плеер Android.",
            "2. Запустите фильм или торрент — в списке приложений выберите TV Player и отметьте «Всегда».",
            "3. Позиция просмотра вернётся в Lampa автоматически.",
        ).forEach {
            Text(it, color = AppColors.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(vertical = 3.dp))
        }
    }
}
