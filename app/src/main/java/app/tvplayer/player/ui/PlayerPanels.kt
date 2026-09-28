package app.tvplayer.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.CardArt
import app.tvplayer.ui.TvFocusable
import app.tvplayer.ui.safeRequestFocus

enum class SheetTab(val title: String) { Audio("Звук"), Subtitles("Субтитры"), Picture("Картинка"), Speed("Скорость") }

/**
 * Панель настроек справа. Вкладки переключаются простым наведением фокуса,
 * содержимое плавно «переезжает» в сторону выбранной вкладки.
 */
@Composable
fun SettingsSheet(
    initialTab: SheetTab,
    optionsFor: (SheetTab) -> List<PanelOption>,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }
    val tabFocus = remember { SheetTab.entries.associateWith { FocusRequester() } }
    var initialFocusDone by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(520.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0xD9101217), Color(0xF7101217))),
            )
            .padding(top = 44.dp, start = 32.dp, end = 32.dp),
    ) {
        Text("НАСТРОЙКИ", color = AppColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SheetTab.entries.forEach { t ->
                SheetTabItem(
                    title = t.title,
                    selected = t == tab,
                    modifier = Modifier.focusRequester(tabFocus.getValue(t)),
                    onFocused = { tab = t },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir / 3 } + fadeOut())
            },
            label = "sheetTab",
        ) { current ->
            OptionList(
                options = optionsFor(current),
                upTarget = tabFocus.getValue(current),
                requestFocus = !initialFocusDone && current == initialTab,
                onFocusRequested = { initialFocusDone = true },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun SheetTabItem(title: String, selected: Boolean, modifier: Modifier, onFocused: () -> Unit) {
    val underline by animateDpAsState(if (selected) 24.dp else 0.dp, label = "tabUnderline")
    TvFocusable(
        onClick = onFocused,
        onFocusChange = { if (it) onFocused() },
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.05f,
        background = Color.Transparent,
    ) { focused ->
        val color by animateColorAsState(
            when {
                focused -> AppColors.OnFocused
                selected -> AppColors.TextPrimary
                else -> AppColors.TextMuted
            },
            label = "tabColor",
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(underline)
                    .height(3.dp)
                    .background(if (focused) AppColors.OnFocused else AppColors.Accent, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun OptionList(
    options: List<PanelOption>,
    upTarget: FocusRequester,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val selectedFocus = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            selectedFocus.safeRequestFocus()
            onFocusRequested()
        }
    }

    if (options.isEmpty()) {
        Text("В этом файле нет вариантов", color = AppColors.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp, start = 4.dp, end = 4.dp),
    ) {
        itemsIndexed(options) { index, option ->
            option.header?.let {
                Text(
                    it.uppercase(),
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 18.dp, top = if (index == 0) 0.dp else 18.dp, bottom = 6.dp),
                )
            }
            TvFocusable(
                onClick = {
                    option.onSelect()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusProperties { up = upTarget } else Modifier)
                    .then(if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier),
                focusedScale = 1.03f,
                background = if (option.selected) AppColors.GlassLight else Color.Transparent,
                contentAlignment = Alignment.CenterStart,
            ) { focused ->
                val primary = if (focused) AppColors.OnFocused else AppColors.TextPrimary
                val secondary = if (focused) AppColors.OnFocused.copy(alpha = 0.65f) else AppColors.TextSecondary
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.title,
                            color = primary,
                            fontSize = 19.sp,
                            fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        option.subtitle?.let { Text(it, color = secondary, fontSize = 14.sp) }
                    }
                    if (option.selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Выбрано",
                            tint = if (focused) AppColors.OnFocused else AppColors.Accent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Лента серий снизу экрана. */
@Composable
fun EpisodesPanel(
    titles: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val currentFocus = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    LaunchedEffect(Unit) { currentFocus.safeRequestFocus() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000), Color(0xFA000000))))
            .padding(top = 80.dp, bottom = 40.dp),
    ) {
        Text(
            "Серии",
            color = AppColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 64.dp),
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(horizontal = 64.dp, vertical = 16.dp),
        ) {
            itemsIndexed(titles) { index, title ->
                val isCurrent = index == currentIndex
                TvFocusable(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .size(width = 300.dp, height = 170.dp)
                        .then(if (isCurrent) Modifier.focusRequester(currentFocus) else Modifier),
                    focusedScale = 1.1f,
                    focusBorder = true,
                    background = AppColors.Surface,
                    focusedBackground = AppColors.Surface,
                    contentAlignment = Alignment.BottomStart,
                ) { _ ->
                    CardArt(seed = title, letterSize = 110.sp) {
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                        )
                        if (isCurrent) {
                            Row(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp)
                                    .background(AppColors.Accent, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Сейчас", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
