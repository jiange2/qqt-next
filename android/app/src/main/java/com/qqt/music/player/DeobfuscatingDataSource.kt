package com.qqt.music.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.qqt.music.AppConfig

/**
 * 媒体解混淆数据源（仓库级 ADR 0011）：OSS 上音频逐字节 +31 混淆存储，
 * 本数据源在网络读出路径上逐字节 -31 还原。
 *
 * - 变换与位置无关，ExoPlayer 的 Range/seek 与下载断点续传不受影响
 * - 仅对媒体 host（[AppConfig.MEDIA_HOST]）生效，其余地址原样透传
 * - URI 带 `#raw` 片段时旁路还原（错误重试自愈通道，见 PlayerViewModel）：fragment 不随请求
 *   发往服务器，但参与 CacheDataSource 缓存键，故 `#raw` 重试天然落在独立命名空间，不污染
 *   正常键的缓存条目
 * - 插在 CacheDataSource 的网络上游：被动缓存与「下载」落盘的均为还原后明文，
 *   存量明文缓存条目也仍然有效
 */
class DeobfuscatingDataSource(private val upstream: DataSource) : DataSource {

    private var deobfuscate = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        deobfuscate = dataSpec.uri.host == AppConfig.MEDIA_HOST &&
            dataSpec.uri.fragment != "raw"
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = upstream.read(buffer, offset, length)
        if (read > 0 && deobfuscate) {
            for (i in offset until offset + read) {
                buffer[i] =
                    ((buffer[i].toInt() + 256 - AppConfig.MEDIA_BYTE_SHIFT) and 0xff).toByte()
            }
        }
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        upstream.close()
    }
}

class DeobfuscatingDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = DeobfuscatingDataSource(upstream.createDataSource())
}
