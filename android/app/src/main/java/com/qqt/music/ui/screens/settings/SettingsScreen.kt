package com.qqt.music.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqt.music.ui.theme.BrandOrange
import com.qqt.music.ui.theme.Hairline
import com.qqt.music.ui.theme.InkFaint
import com.qqt.music.ui.theme.InkPrimary
import com.qqt.music.ui.theme.InkSecondary
import com.qqt.music.ui.theme.WarmBackground

@Composable
fun SettingsScreen() {
    var darkTheme by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 偏好分组
        SettingsGroup {
            SettingsRow {
                Column(Modifier.weight(1f)) {
                    Text("主题", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = InkPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("跟随系统", fontSize = 12.sp, color = InkSecondary)
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = { darkTheme = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandOrange,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = InkFaint,
                    ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 关于分组
        SettingsGroup {
            SettingsArrowRow("评价APP") {}
            SettingsDivider()
            SettingsArrowRow("分享APP") {}
            SettingsDivider()
            SettingsArrowRow("隐私政策") {}
            SettingsDivider()
            SettingsArrowRow("关于我们") {}
        }
    }
}

/** 白色圆角卡片容器，一组设置项一个卡片 */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = Hairline, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun SettingsArrowRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, color = InkPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = InkFaint, modifier = Modifier.size(20.dp))
    }
}
