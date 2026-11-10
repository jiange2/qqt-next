package com.qqt.music.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * SAF 目录树扫描（ADR 0019）：自行递归遍历用户授权的目录，不查 MediaStore、不申请存储权限。
 *
 * - 音频格式白名单，跳过以 `.` 开头的隐藏目录/文件，目录级尊重 `.nomedia`，不限深度
 * - 身份为内容指纹（ADR 0021）：大小 + 首尾各 32KB 的 SHA-256，内容相同的两份文件只留先扫到的一条
 * - 标签串行读取（MediaMetadataRetriever 是主要成本），全程在 IO 线程，可被协程取消
 * - 返回按标题升序（中文排序）的结果；调用方拿到完整结果后再整体替换索引（原子提交）
 */
class LocalMusicScanner(private val context: Context) {

    /** 扫描进度：已处理文件数、已收录歌曲数、当前文件名（进度条只做无总量提示，不预估总时长） */
    data class Progress(val scannedFiles: Int, val foundSongs: Int, val currentName: String)

    /** 目录树里的一项：目录用于入队，文件用于读取指纹与标签 */
    private class DocumentEntry(
        val documentId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
    )

    /** 待遍历目录：documentId + 相对路径（根为 ""）+ 目录名（根用授权时的树名） */
    private class DirectoryNode(val documentId: String, val relativePath: String, val name: String)

    /** 标签与时长同一次 retriever 调用读出，避免为一个文件开两次解析器 */
    private class ExtractedTags(val tags: LocalSongTags, val durationMs: Long)

    /**
     * 扫描全部根目录。[onProgress] 在 IO 线程逐文件回调；协程取消即中止，异常向上抛，
     * 两种情况下都不返回半成品（原子提交由调用方保证）。
     */
    suspend fun scan(
        roots: List<LocalMusicRoot>,
        onProgress: (Progress) -> Unit = {},
    ): List<LocalSong> = withContext(Dispatchers.IO) {
        val byFingerprint = LinkedHashMap<String, LocalSong>()
        var scannedFiles = 0
        for (root in roots) {
            val queue = ArrayDeque<DirectoryNode>()
            queue.addLast(
                DirectoryNode(
                    documentId = DocumentsContract.getTreeDocumentId(root.treeUri),
                    relativePath = "",
                    name = root.name,
                )
            )
            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val directory = queue.removeFirst()
                val entries = queryChildren(root.treeUri, directory.documentId)
                // 目录级 .nomedia：整棵子树都不是媒体目录，连遍历都省掉
                if (entries.any { it.name.equals(NOMEDIA, ignoreCase = true) }) continue
                for (entry in entries) {
                    // 隐藏目录与隐藏文件一律不收录（.nomedia、.thumbnails 等）
                    if (entry.name.startsWith(".")) continue
                    if (entry.isDirectory) {
                        queue.addLast(
                            DirectoryNode(
                                documentId = entry.documentId,
                                relativePath = directory.relativePath + entry.name + "/",
                                name = entry.name,
                            )
                        )
                        continue
                    }
                    if (!isAudioFile(entry.name)) continue
                    currentCoroutineContext().ensureActive()
                    scannedFiles++
                    onProgress(Progress(scannedFiles, byFingerprint.size, entry.name))
                    val song = readSong(root, directory, entry) ?: continue
                    // 内容完全相同的副本只留先扫到的一条（ADR 0021）
                    if (song.fingerprint !in byFingerprint) byFingerprint[song.fingerprint] = song
                }
            }
        }
        val collator = Collator.getInstance(Locale.CHINA)
        val byTitle = Comparator<LocalSong> { left, right ->
            collator.compare(left.displayTitle(), right.displayTitle())
        }
        byFingerprint.values.sortedWith(byTitle)
    }

    /** 该文件对 [treeUri] 的 document URI：播放队列与封面读取都用它，不另存路径假 URI */
    private fun readSong(root: LocalMusicRoot, directory: DirectoryNode, entry: DocumentEntry): LocalSong? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(root.treeUri, entry.documentId)
        val fingerprint = contentFingerprint(documentUri, entry.size) ?: return null
        val extracted = readTags(documentUri)
        return LocalSong(
            fingerprint = fingerprint,
            treeUri = root.treeUri.toString(),
            documentUri = documentUri.toString(),
            relativePath = directory.relativePath + entry.name,
            displayName = entry.name,
            folderName = directory.name,
            size = entry.size,
            lastModified = entry.lastModified,
            durationMs = extracted.durationMs,
            tags = extracted.tags,
        )
    }

    /** 目录树的一层子项；查询失败按空目录处理（单点失败不中断整次扫描） */
    private fun queryChildren(treeUri: Uri, parentDocumentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val rows = ArrayList<DocumentEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    rows.add(
                        DocumentEntry(
                            documentId = cursor.getString(0),
                            name = name,
                            isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            size = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                            lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                        )
                    )
                }
            }
        }.onFailure { e -> Log.w(TAG, "目录读取失败：$parentDocumentId", e) }
        return rows
    }

    private fun isAudioFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS

    /**
     * 内容指纹（ADR 0021）：文件大小 + 首 32KB 与尾 32KB 的 SHA-256（不足 64KB 取全文）。
     * 只读首尾是为了让首扫成本与文件大小无关；读不到（流不可寻址、文件被删）即放弃该文件。
     */
    private fun contentFingerprint(uri: Uri, size: Long): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(size).array())
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                if (size <= 0L) {
                    // 个别 provider 不给大小：退化为全文哈希，仍不会把两份不同文件当成同一首
                    digestChunk(stream, Long.MAX_VALUE, digest)
                } else {
                    digestChunk(stream, minOf(CHUNK_BYTES, size), digest)
                    if (size > CHUNK_BYTES * 2) {
                        stream.channel.position(size - CHUNK_BYTES)
                        digestChunk(stream, CHUNK_BYTES, digest)
                    }
                }
            }
        }
        digest.digest().toHex()
    }.getOrElse { e ->
        Log.w(TAG, "指纹计算失败，跳过 $uri", e)
        null
    }

    private fun digestChunk(stream: FileInputStream, length: Long, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER_BYTES)
        var remaining = length
        while (remaining > 0) {
            val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            digest.update(buffer, 0, read)
            remaining -= read
        }
    }

    /** 标签读取失败（损坏文件、provider 不返回元数据）时退回空标签，标题由文件名兜底 */
    private fun readTags(uri: Uri): ExtractedTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            ExtractedTags(
                tags = LocalSongTags(
                    title = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                    artist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                    album = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                    albumArtist = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty(),
                    // 音轨号常见写法是 "3/12"，只取前面的序号
                    trackNo = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                        ?.substringBefore('/')?.toIntOrNull(),
                    // 年份可能是 "2019" 或完整的发行日期，只取前四位数字
                    year = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?.take(4)?.toIntOrNull(),
                ),
                durationMs = retriever.metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            Log.w(TAG, "标签读取失败：$uri", e)
            ExtractedTags(LocalSongTags(), 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun MediaMetadataRetriever.metadata(key: Int): String? =
        extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }

    private fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        for (i in indices) {
            val value = this[i].toInt() and 0xFF
            hex[i * 2] = HEX_DIGITS[value ushr 4]
            hex[i * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return String(hex)
    }

    private companion object {
        const val TAG = "LocalMusicScanner"
        const val NOMEDIA = ".nomedia"

        /** 音频格式白名单（spec）；其余文件即使出现在目录里也不收录 */
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

        /** 指纹取头尾各 32KB */
        const val CHUNK_BYTES = 32L * 1024
        const val BUFFER_BYTES = 8 * 1024

        val HEX_DIGITS = "0123456789abcdef".toCharArray()

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
