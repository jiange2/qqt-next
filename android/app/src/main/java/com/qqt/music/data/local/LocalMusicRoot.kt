package com.qqt.music.data.local

import android.net.Uri

/** 已授权的音乐文件夹：SAF 树 URI + 展示用目录名（ADR 0019 以用户选中的树为扫描范围） */
data class LocalMusicRoot(val treeUri: Uri, val name: String)
