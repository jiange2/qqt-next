package com.qqt.music.ui.screens.localmusic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.data.local.MetadataSuggestion
import com.qqt.music.data.local.filterSuggestions
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.PlaceholderBg

/** 一次最多渲染的候选行数：纯 Column 全量组合，大库靠过滤收窄，超出给提示行 */
private const val MAX_SUGGESTION_ROWS = 50

/**
 * 带编辑候选的输入框（spec「控件与交互」）：描边 / 配色与 [MetadataField] 同款，
 * 尾部箭头 + 字段下方内联候选列表。艺术家 / 专辑 / 专辑艺术家字段用本控件，
 * 标题 / 音轨号 / 年份仍用 [MetadataField]。
 *
 * 候选列表内联展开而非 Popup 下拉：ModalBottomSheet + 软键盘场景下 Popup 的定位与
 * IME 遮挡不可控，内联列表随面板滚动、始终在 imePadding 之上可见。
 *
 * 聚焦即展开**全量**（不按预填值过滤——单首面板初值是当前值，聚焦是为了换值）；
 * 打字即按输入过滤；失焦自动收起。焦点同一时刻只在一个字段上，故至多一个列表展开。
 * 自由输入保留：候选只是省打字，库里没有的新名字照样能填（spec Q3）。
 *
 * [onSuggestionClick] 默认只回填本框；专辑字段传联动 lambda 连带回填专辑艺术家
 * （spec「专辑候选联动」：归并键 = 专辑名 + 专辑艺术家，两框一起填才并进同一张）。
 */
@Composable
internal fun MetadataSuggestionField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<MetadataSuggestion>,
    modifier: Modifier = Modifier,
    onSuggestionClick: (MetadataSuggestion) -> Unit = { onValueChange(it.value) },
) {
    var expanded by remember { mutableStateOf(false) }
    // null = 不过滤（聚焦 / 点箭头展开全量）；非 null = 按用户输入过滤
    var filter by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val filtered = remember(suggestions, filter) { suggestions.filterSuggestions(filter.orEmpty()) }
    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "suggestionArrow")

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { text ->
                onValueChange(text)
                filter = text
                expanded = true
            },
            label = { Text(label, fontSize = 13.sp) },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (expanded) {
                            expanded = false
                        } else {
                            filter = null
                            expanded = true
                            focusRequester.requestFocus()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) "收起候选" else "展开候选",
                        tint = if (expanded) BrandOrange else InkSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(arrowRotation),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandOrange,
                focusedLabelColor = BrandOrange,
                cursorColor = BrandOrange,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    expanded = state.isFocused
                    if (state.isFocused) filter = null
                },
        )
        AnimatedVisibility(visible = expanded && filtered.isNotEmpty()) {
            Surface(
                color = PlaceholderBg.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                // 外层面板已 verticalScroll，这里限高内滚：候选多时不撑爆弹层
                Column(
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val shown = filtered.take(MAX_SUGGESTION_ROWS)
                    shown.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSuggestionClick(suggestion)
                                    expanded = false
                                    filter = null
                                    focusManager.clearFocus()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = suggestion.value,
                                fontSize = 13.sp,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!suggestion.supporting.isNullOrBlank()) {
                                Text(
                                    text = suggestion.supporting,
                                    fontSize = 11.sp,
                                    color = InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (filtered.size > shown.size) {
                        Text(
                            text = "还有 ${filtered.size - shown.size} 项，输入关键字过滤",
                            fontSize = 11.sp,
                            color = InkFaint,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}