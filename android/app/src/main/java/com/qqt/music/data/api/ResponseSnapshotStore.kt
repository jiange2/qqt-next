package com.qqt.music.data.api

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * 响应快照存储（ADR 0012）：接口成功响应的历史留档，断网或请求失败时由
 * [ResponseSnapshotInterceptor] 回放，维持浏览、搜索与阅读可用。
 *
 * - 存储于 cacheDir 专用目录，随卸载与系统回收清理，用户不可感知也不可管理
 * - 独立小预算 + LRU：按文件 lastModified 计龄（读时触碰），超预算驱逐最旧条目，
 *   不并入音频缓存预算（ADR 0004 的总预留只服务音频）
 * - key 为业务语义串（method_name + 业务参数 / 歌词 URL / 歌曲实体键），
 *   落盘前做 SHA-1 摘要作文件名，规避非法字符与长度限制
 */
object ResponseSnapshotStore {

    private const val TAG = "ResponseSnapshot"

    /** 快照总预算（字节）：留档仅是小体量 JSON/文本响应，5MB 足以覆盖浏览/搜索/阅读的高频页 */
    private const val MAX_BYTES = 5L * 1024 * 1024

    private var dir: File? = null

    /** 须在 Application.onCreate 调用；未初始化时读写均为空操作（拦截器自动退化为直连） */
    fun init(context: Context) {
        dir = File(context.cacheDir, "response_snapshot").apply { mkdirs() }
    }

    /** 读快照并触碰其最近使用时间（LRU 按访问计龄）；无快照返回 null */
    fun read(key: String): String? {
        val file = dir?.resolve(fileName(key)) ?: return null
        if (!file.isFile) return null
        return try {
            file.setLastModified(System.currentTimeMillis())
            file.readText()
        } catch (e: Exception) {
            null
        }
    }

    /** 写快照并按预算驱逐最旧条目；仅「非空有效响应」才应写入（口径见拦截器） */
    fun write(key: String, text: String) {
        val target = dir ?: return
        try {
            target.resolve(fileName(key)).writeText(text)
            evictIfNeeded(target)
        } catch (e: Exception) {
            Log.w(TAG, "snapshot write failed", e)
        }
    }

    /** 预算驱逐：超出预算时按 lastModified 升序删除最旧条目（刚写入的条目自然最晚被驱逐） */
    private fun evictIfNeeded(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_BYTES) return
            val len = file.length()
            if (file.delete()) total -= len
        }
    }

    private fun fileName(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
