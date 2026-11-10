package com.qqt.music.player

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener

/** 本地直读播放 URI：`file://`（已下载）与 `content://`（本地歌曲）都不进被动缓存、不经过网络栈 */
fun Uri.isLocalDirectReadUri(): Boolean = scheme == "file" || scheme == "content"

/** 本地歌曲的直读 URI：SAF 目录内的 `content://` 文档（与已下载的 `file://` 区分） */
fun Uri.isLocalSongUri(): Boolean = scheme == "content"

/**
 * 按 URI scheme 分流的播放数据源（ADR 0003）：
 * - `file://`（已下载歌曲离线播放）→ FileDataSource 直读本地文件，绕过缓存
 * - `content://`（本地音乐 SAF 目录内的文件）→ ContentDataSource 经持久化授权直读，绕过缓存
 * - 其余 http(s) → 被动缓存 CacheDataSource + 网络回源
 *
 * 本地两种 scheme 直读的字节不经缓存、不经过网络栈；打开失败就是失败，不回退到网络分支。
 */
class SchemeRoutingDataSource(
    private val cachedHttp: DataSource,
    private val localContent: DataSource,
) : DataSource {

    private val localFile = FileDataSource()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cachedHttp.addTransferListener(transferListener)
        localFile.addTransferListener(transferListener)
        localContent.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = when {
            dataSpec.uri.isLocalSongUri() -> localContent
            dataSpec.uri.scheme == "file" -> localFile
            else -> cachedHttp
        }
        active = delegate
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun close() {
        active?.close()
        active = null
    }
}

class SchemeRoutingDataSourceFactory(
    context: Context,
    private val cachedHttp: DataSource.Factory,
) : DataSource.Factory {

    /** ContentDataSource 持有 ContentResolver，用 Application 上下文避免长生命周期引用 */
    private val appContext = context.applicationContext

    // 每次装载都重新 createDataSource：ContentDataSource 关闭后不可复用，故随实例新建
    override fun createDataSource(): DataSource = SchemeRoutingDataSource(
        cachedHttp = cachedHttp.createDataSource(),
        localContent = ContentDataSource(appContext),
    )
}
