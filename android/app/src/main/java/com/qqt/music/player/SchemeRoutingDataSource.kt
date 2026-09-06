package com.qqt.music.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener

/**
 * 按 URI scheme 分流的播放数据源（ADR 0003）：
 * - `file://`（已下载歌曲离线播放）→ FileDataSource 直读本地文件，绕过缓存
 * - 其余 http(s) → 被动缓存 CacheDataSource + 网络回源
 */
class SchemeRoutingDataSource(private val cachedHttp: DataSource) : DataSource {

    private val localFile = FileDataSource()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cachedHttp.addTransferListener(transferListener)
        localFile.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = if (dataSpec.uri.scheme == "file") localFile else cachedHttp
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

class SchemeRoutingDataSourceFactory(private val cachedHttp: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = SchemeRoutingDataSource(cachedHttp.createDataSource())
}
