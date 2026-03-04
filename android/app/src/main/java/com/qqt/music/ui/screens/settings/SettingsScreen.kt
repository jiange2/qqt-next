package com.qqt.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.qqt.music.ui.theme.OrangePrimary

@Composable
fun SettingsScreen() {
    var darkTheme by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
    ) {
        // Theme toggle
        SettingsRow {
            Column(Modifier.weight(1f)) {
                Text("主题", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("跟随系统", fontSize = 13.sp, color = Color.Gray)
            }
            Switch(
                checked = darkTheme,
                onCheckedChange = { darkTheme = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = OrangePrimary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.LightGray,
                ),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE), modifier = Modifier.padding(start = 16.dp))

        SettingsArrowRow("评价APP") {}
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE), modifier = Modifier.padding(start = 16.dp))

        SettingsArrowRow("分享APP") {}
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE), modifier = Modifier.padding(start = 16.dp))

        SettingsArrowRow("隐私政策") {}
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE), modifier = Modifier.padding(start = 16.dp))

        SettingsArrowRow("关于我们") {}
    }
}

@Composable
private fun SettingsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SettingsArrowRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
    }
}
