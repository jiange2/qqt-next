package com.qqt.music.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 本地专辑封面（spec「专辑与封面」）：文件夹封面文件 → 音频内嵌图 → 都没有（返回 null，页面显示占位图）。
 *
 * - 只在被展示时解析（懒加载），结果按专辑键写进 App 私有缓存目录，不放进音频缓存（那是下载流量的预算）
 * - 缓存有容量上限，超出按最近使用淘汰：本进程访问过的键记在 [recent] 里，没访问过的按文件修改时间，
 *   不会随专辑数量无限增长
 * - 重扫完成后由调用方 [clear] 清空：文件夹换了封面文件，要等重扫才被感知（spec）
 * - 全部读盘与解析在 IO 线程，SAF 查询逐目录串行，不在主线程开解析器
 */
class LocalCoverCache(private val context: Context) {

    private val cacheDir = File(context.cacheDir, CACHE_DIR)

    /** 封面解析与淘汰都改缓存目录，同一把锁串行化 */
    private val lock = Mutex()

    /** 最近使用的缓存条目（插入序 = 访问序，命中时先删后插挪到队尾） */
    private val recent = LinkedHashMap<String, Long>()

    /**
     * 本进程内确认过「没有任何封面」的专辑键：避免同一张无封面专辑每次滚回屏幕都重开一次解析器
     * （开一次 MediaMetadataRetriever 不便宜）。重扫清空缓存时一并作废，新增了封面文件仍能生效。
     */
    private val missing = LinkedHashSet<String>()

    /**
     * 取专辑封面：命中缓存直接返回，否则按优先级解析并写入缓存。
     * 返回 null 表示这张专辑没有任何封面可用（文件夹里没有封面文件、音频也没有内嵌图）。
     */
    suspend fun cover(album: LocalAlbum): File? = withContext(Dispatchers.IO) {
        lock.withLock { resolve(album) }
    }

    /** 清空缓存：重扫提交后用，好让「文件夹封面变化」在重扫后生效 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            recent.clear()
            missing.clear()
            cacheDir.listFiles()?.forEach { file -> runCatching { file.delete() } }
        }
    }

    private fun resolve(album: LocalAlbum): File? {
        val cached = File(cacheDir, entryName(album.key))
        if (cached.isFile && cached.length() > 0) {
            markUsed(cached.name)
            return cached
        }
        if (album.key in missing) return null
        // 优先级：文件夹封面文件 → 音频内嵌图 → 占位（null）
        val bytes = readFolderCover(album) ?: readEmbeddedCover(album.coverSong)
        if (bytes == null) {
            rememberMissing(album.key)
            return null
        }
        val written = writeEntry(cached, bytes) ?: return null
        markUsed(written.name)
        trim()
        return written
    }

    private fun rememberMissing(key: String) {
        missing.remove(key)
        missing.add(key)
        while (missing.size > MAX_TRACKED) {
            val oldest = missing.firstOrNull() ?: break
            missing.remove(oldest)
        }
    }

    /** 缓存文件名：专辑键的 SHA-256，键里带路径与分隔符也不用担心文件名非法 */
    private fun entryName(key: String): String = sha256Hex(key) + ENTRY_SUFFIX

    private fun markUsed(name: String) {
        recent.remove(name)
        recent[name] = System.currentTimeMillis()
        // 记录表本身也要有界：超出上限时丢掉最久未用的那条（文件按需淘汰，不影响正确性）
        while (recent.size > MAX_TRACKED) {
            val oldest = recent.keys.firstOrNull() ?: break
            recent.remove(oldest)
        }
    }

    /**
     * 超额淘汰：总量超过 [MAX_BYTES] 时按最近使用从旧到新删。
     * 本进程访问过的条目按 [recent] 的顺序，其余按文件修改时间（上次进程最后一次写入 / 使用的时刻）。
     */
    private fun trim() {
        val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        val candidates = files.sortedBy { recent[it.name] ?: it.lastModified() }
        for (file in candidates) {
            if (total <= MAX_BYTES) break
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                total -= length
                recent.remove(file.name)
            }
        }
    }

    /** 临时文件 + rename：并发读到半个封面的情况不会落在正式文件名上 */
    private fun writeEntry(target: File, bytes: ByteArray): File? = runCatching {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val temp = File(cacheDir, target.name + TEMP_SUFFIX)
        temp.writeBytes(bytes)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) error("封面缓存 rename 失败：${target.name}")
        target
    }.getOrElse { e ->
        Log.w(TAG, "封面缓存写入失败", e)
        null
    }

    /**
     * 文件夹封面文件：歌曲所在目录里的 `cover / folder / front / album` + `jpg / jpeg / png / webp`。
     * 同名不同扩展名时按 [COVER_BASE_NAMES] 的顺序取第一个能读出来的。
     */
    private fun readFolderCover(album: LocalAlbum): ByteArray? {
        val song = album.coverSong
        val treeUri = Uri.parse(song.treeUri)
        val folderId = folderDocumentId(treeUri, song.relativePath.substringBeforeLast('/', "")) ?: return null
        val children = queryChildren(treeUri, folderId)
        for (baseName in COVER_BASE_NAMES) {
            val entry = children.firstOrNull { it.isCoverNamed(baseName) } ?: continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.documentId)
            readBytes(uri)?.let { return it }
        }
        return null
    }

    /** 音频内嵌图：封面的第二来源 */
    private fun readEmbeddedCover(song: LocalSong): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(song.documentUri))
            retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "内嵌封面读取失败：${song.documentUri}", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * 歌曲所在文件夹的 documentId：从树根按相对路径逐级查子项。
     * 索引里只存 relativePath（spec 的索引字段清单固定），所以只能这样回查；
     * 逐级 query 与扫描同源，第三方 provider 的不透明 documentId 也走得通。
     */
    private fun folderDocumentId(treeUri: Uri, folderPath: String): String? {
        var documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        if (folderPath.isEmpty()) return documentId
        for (segment in folderPath.split('/')) {
            if (segment.isEmpty()) continue
            val children = queryChildren(treeUri, documentId)
            val child = children.firstOrNull { it.isDirectory && it.name == segment }
                ?: children.firstOrNull { it.isDirectory && it.name.equals(segment, ignoreCase = true) }
                ?: return null
            documentId = child.documentId
        }
        return documentId
    }

    private fun queryChildren(treeUri: Uri, parentDocumentId: String): List<FolderEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val entries = ArrayList<FolderEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    entries.add(
                        FolderEntry(
                            documentId = cursor.getString(0),
                            name = name,
                            isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        )
                    )
                }
            }
        }.onFailure { e -> Log.w(TAG, "目录读取失败，按空目录处理：$parentDocumentId", e) }
        return entries
    }

    /** 单张封面最多读 8MB：超大的图当封面没有意义（也会撑爆缓存），读满上限就换下一种来源 */
    private fun readBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (out.size() < MAX_COVER_BYTES) {
                val count = stream.read(buffer)
                if (count <= 0) break
                out.write(buffer, 0, count)
            }
            val bytes = out.toByteArray()
            // 读到上限要么刚好这么大、要么被截断：截断的图解码不出来，两种情况都当没有封面
            if (bytes.isEmpty() || bytes.size >= MAX_COVER_BYTES) null else bytes
        }
    }.getOrElse { e ->
        Log.w(TAG, "封面文件读取失败：$uri", e)
        null
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val byte = digest[i].toInt() and 0xFF
            hex[i * 2] = HEX_DIGITS[byte ushr 4]
            hex[i * 2 + 1] = HEX_DIGITS[byte and 0x0F]
        }
        return String(hex)
    }

    private class FolderEntry(val documentId: String, val name: String, val isDirectory: Boolean) {
        /** `cover.jpg` 命中 `cover` 这一档；比较大小写不敏感（`Cover.JPG` 也算封面） */
        fun isCoverNamed(baseName: String): Boolean {
            if (isDirectory) return false
            if (name.substringBeforeLast('.', "").lowercase(Locale.ROOT) != baseName) return false
            return name.substringAfterLast('.', "").lowercase(Locale.ROOT) in COVER_EXTENSIONS
        }
    }

    private companion object {
        const val TAG = "LocalCoverCache"
        const val CACHE_DIR = "local_covers"
        const val ENTRY_SUFFIX = ".img"
        const val TEMP_SUFFIX = ".tmp"

        /** 缓存容量上限：专辑封面几百 KB 量级，16MB 够几百张，超出按最近使用淘汰 */
        const val MAX_BYTES = 16L * 1024 * 1024

        /** 单张封面读取上限 */
        const val MAX_COVER_BYTES = 8 * 1024 * 1024

        const val BUFFER_BYTES = 8 * 1024

        /** 访问记录表的条目上限（只为淘汰排序用，不影响缓存文件本身） */
        const val MAX_TRACKED = 256

        /** 文件夹封面文件名（不含扩展名），按此顺序取第一个命中的 */
        val COVER_BASE_NAMES = listOf("cover", "folder", "front", "album")
        val COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

        val HEX_DIGITS = "0123456789abcdef".toCharArray()

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}