package com.qqt.music.data.local

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * 本地音乐导入的唯一写侧接缝（spec「唯一写侧接缝」，票据01）：接住来源 App（微信/QQ 等）经
 * 「用其他应用打开 / 分享」递出的音频 content URI，把字节复制进系统媒体库公共音乐目录 `Music/导入/`，
 * 使其成为一首普通本地歌曲（行入库由现有整库查询自然收录，ADR 0022）。UI 与 ViewModel 只调它，
 * 不直接碰 MediaStore 写入 API。入口在来源 App 一侧、qqt 内不设导入按钮（系统边界：够不到来源 App
 * 私有目录 `Android/data/`，spec「入口与接收」）。
 *
 * 一步流程（区别于删除的两步系统框动作机）：[importAudio] 单步 suspend 直接产出归一结果
 * [LocalImportResult]，无用户取消态、无权限申请交接——≤28 未授予写入权限时直接失败（见 impl）。
 * 只落库、不自动播放、不重建播放队列（编排与提示见票据02 的 ViewModel）。
 *
 * 分版本链路（与 [MediaStoreLibrarySource] / [MediaStoreDeleter] 同口径注入 sdkInt，纯路由见 [importRoute]）：
 * - Android 10+（API 29+）：`MediaStore.Audio.Media` 插入自有行（`RELATIVE_PATH = "Music/导入/"`、
 *   `IS_PENDING = 1` 期间写入字节、写完置 0），App 拥有自己创建的行，不需要新权限；
 *   任何失败清理自有 pending 行，不留半截（整库查询 `IS_PENDING = 0` 过滤也保证半截不可见）
 * - Android 9 及以下（≤28）：直写公共 `Music/导入/` 目录（临时文件 + rename）后
 *   `MediaScannerConnection.scanFile` 触发扫描；`WRITE_EXTERNAL_STORAGE` 清单已有（maxSdkVersion 29），
 *   23–28 未授予时直接失败、不弹申请框（单步接缝钉死，spec 不镜像删除的两步动作机）
 */
interface LocalMusicImporter {

    /**
     * 导入一个音频：[sourceUri] 是来源 App 递出的 content URI（读权限为来源 App 授予的临时 grant，
     * 复制在本协程内尽快完成，不落盘 URI、不延迟到后续会话）。按版本路由复制落库，目标目录重名时
     * 自动加后缀不覆盖（[resolveImportFileName]），每次导入各自成行（不去重不覆盖，ADR 0022）。
     * 任何失败归一为 [LocalImportResult.Failure] 而非崩溃，且不留半截文件 / pending 行。
     */
    suspend fun importAudio(sourceUri: Uri): LocalImportResult
}

/**
 * 归一化导入结果（镜像 [LocalDeleteResult] / [LocalLibraryResult] 风格）：成功 / 失败两态，
 * 无取消态（导入没有用户取消交互）。失败带可空原因——「插入被拒返回 null」「未授予写入权限」
 * 无异常，原因为 null；框架抛错（provider 异常、来源流不可读、写入/落位失败）时带上异常供排查。
 */
sealed interface LocalImportResult {
    /** 音频已复制进 `Music/导入/` 并落库（29+ 媒体库行已发布，≤28 已触发扫描），成为普通本地歌曲 */
    data object Success : LocalImportResult

    /**
     * 导入失败：[cause] 为框架异常，或 null（无异常的失败）。失败不留半截——
     * 29+ 清理自有 pending 行、≤28 删除临时文件（impl 保证），旧文件绝不被覆盖。
     */
    data class Failure(val cause: Throwable? = null) : LocalImportResult
}

/** 导入链路按系统版本路由（spec「分版本」）：29+ MediaStore 插入自有行 / ≤28 直写公共目录 + 扫描 */
enum class ImportRoute { MediaStoreInsert, DirectWrite }

/**
 * 版本路由（纯函数，JVM 可测，镜像 [deleteRoute]）：Android 10+（API 29+）走 `MediaStore.Audio.Media`
 * 插入（`IS_PENDING` 期间写入、写完置 0）；Android 9 及以下（≤28）直写公共 `Music/导入/` 目录后
 * `MediaScannerConnection.scanFile` 触发扫描。29 必须走 MediaStore——清单无 `requestLegacyExternalStorage`，
 * 29 上 File 直写公共目录被 scoped storage 挡（与 [deleteRoute] 的 29 分界一致）。
 */
fun importRoute(sdkInt: Int): ImportRoute =
    if (sdkInt >= 29) ImportRoute.MediaStoreInsert else ImportRoute.DirectWrite

/**
 * 空名 / 全非法字符兜底的默认展示名：带 `.mp3` 扩展名，保证 MediaStore（29+ 按扩展名推 MIME）
 * 与媒体扫描器（≤28）都把它识别为音频、`IS_MUSIC` 置位进库；实际容器格式由播放器按字节嗅探，
 * 扩展名只是入库提示（来源名整体不可用时才落到这里，无扩展名信息可保留）。
 */
internal const val DEFAULT_IMPORT_NAME = "导入音频.mp3"

/**
 * 目标目录重名解析（纯函数，JVM 可测）：无冲突原样返回；冲突加后缀 ` (n)`（n 从 2 递增）、
 * 保留扩展名，保证结果不在 [existingNames] 中（各自成行、不覆盖，ADR 0022）。
 * 空 / 全空白 [desired] 兜底 [DEFAULT_IMPORT_NAME] 后同样参与重名解析。
 * 扩展名按最后一个 `.` 切分（点号开头如 `.mp3` 无主名，整体视作无扩展名），后缀插在扩展名之前。
 * 这是重名的第一道防线：查询→插入之间无锁，极端并发（连续两次导入同一文件）由 MediaStore
 * 插入自身的同目录同名自动加后缀兜底（系统行为），不另加锁。
 */
internal fun resolveImportFileName(desired: String, existingNames: Collection<String>): String {
    val base = desired.trim().ifBlank { DEFAULT_IMPORT_NAME }
    if (base !in existingNames) return base
    val dot = base.lastIndexOf('.')
    val stem = if (dot > 0) base.substring(0, dot) else base
    val extension = if (dot > 0) base.substring(dot) else ""
    var suffix = 2
    while (true) {
        val candidate = "$stem ($suffix)$extension"
        if (candidate !in existingNames) return candidate
        suffix++
    }
}

/** 文件名非法字符：路径分隔符 + Windows 保留字符（跨平台 / MediaStore 安全）；控制字符另按码点剔除 */
private const val ILLEGAL_NAME_CHARS = "/\\:*?\"<>|"

/**
 * 展示名规范化（纯函数，JVM 可测）：来源 URI 的原始展示名（provider `DISPLAY_NAME`，取不到退回
 * URI 尾段）去掉路径分隔符与文件名非法字符（`/ \ : * ? " < > |` 及控制字符）、裁掉首尾空白；
 * 结果为空 / 全空白 / 纯点号（`.` `..` 等目录引用名）时兜底 [DEFAULT_IMPORT_NAME]。
 * 规范化后的名交 [resolveImportFileName] 做重名解析——规范化只管字符合法，重名只管不覆盖。
 */
internal fun normalizeImportDisplayName(raw: String): String {
    val cleaned = raw.filterNot { it in ILLEGAL_NAME_CHARS || it < ' ' }.trim()
    return if (cleaned.isBlank() || cleaned.all { it == '.' }) DEFAULT_IMPORT_NAME else cleaned
}

/**
 * 29+ MediaStore 插入目标目录（`RELATIVE_PATH` 形态，尾带 `/`——与 [folderPathOf] 的匹配口径一致，
 * 重名查询 selection 也用它精确匹配）。主外置卷 `external_primary`，不合并 SD 卡卷（写侧只关主卷）。
 */
internal const val IMPORT_RELATIVE_PATH = "Music/导入/"

/** ≤28 直写目标：公共 Music 目录（`Environment.DIRECTORY_MUSIC`）下的「导入」文件夹 */
internal const val IMPORT_FOLDER_NAME = "导入"

/**
 * ≤28 直写链路是否需要核对已授予 `WRITE_EXTERNAL_STORAGE`（纯函数，JVM 可测，镜像
 * [writePermissionRequestRequired]）：仅 [ImportRoute.DirectWrite] 且 API 23+。
 * 29+ 走 MediaStore 插入自有行、不碰 WRITE；<23 清单声明即安装时授予，无需核对。
 * 与删除不同：导入是单步 suspend 接缝，未授予时直接 [LocalImportResult.Failure]，不弹运行时申请框
 * （spec 钉死单步接缝，不镜像删除的两步动作机；≤28 的 READ/WRITE 同属 STORAGE 权限组，授过读即有写）。
 */
internal fun legacyImportRequiresWriteGrant(sdkInt: Int): Boolean =
    importRoute(sdkInt) == ImportRoute.DirectWrite && sdkInt >= 23

/**
 * MediaStore 系统媒体库导入实现（spec「唯一写侧接缝」，沿用 ADR 0022）：版本路由 / 重名解析 /
 * 展示名规范化 / 权限门槛为纯函数（JVM 可测，见 LocalImportRulesTest），框架交互（ContentResolver
 * 读写、MediaStore 插入、MediaScannerConnection 扫描）留真机手工验收。与 [MediaStoreLibrarySource] /
 * [MediaStoreDeleter] 同口径注入 sdkInt；IO 全在私有方法、[withContext]`(Dispatchers.IO)` 包裹，
 * 框架异常 `try/catch(Exception)` → `Log.w` + [LocalImportResult.Failure]，绝不崩溃、绝不暴露半截文件。
 */
class MediaStoreImporter(
    private val context: Context,
    /** 注入设备 API 级别：导入链路按它分支（测试可指定） */
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : LocalMusicImporter {

    override suspend fun importAudio(sourceUri: Uri): LocalImportResult =
        withContext(Dispatchers.IO) {
            when (importRoute(sdkInt)) {
                ImportRoute.MediaStoreInsert -> importViaMediaStore(sourceUri)
                ImportRoute.DirectWrite -> importViaLegacyFile(sourceUri)
            }
        }

    /**
     * 29+：查目标目录现有名 → 解析重名 → 插入自有 pending 行（`IS_PENDING = 1`）→ 复制来源字节 →
     * 置 `IS_PENDING = 0` 发布。任何失败清理自有 pending 行（App 拥有自己创建的行，删除不弹系统框），
     * 不留半截；整库查询 `IS_PENDING = 0` 过滤也保证清理前半截不可见。
     */
    private fun importViaMediaStore(sourceUri: Uri): LocalImportResult {
        var pendingUri: Uri? = null
        return try {
            val displayName = resolveImportFileName(
                normalizeImportDisplayName(rawDisplayName(sourceUri)),
                existingImportNames(),
            )
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.RELATIVE_PATH, IMPORT_RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri == null) {
                // provider 拒绝插入（无行可清）：无异常的失败
                Log.w(TAG, "MediaStore 插入返回 null，导入失败")
                return LocalImportResult.Failure()
            }
            pendingUri = uri
            resolver.openOutputStream(uri)?.use { output -> copySource(sourceUri, output) }
                ?: throw IOException("无法打开导入目标输出流：$uri")
            val published = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null,
            )
            if (published <= 0) throw IOException("导入行发布失败：IS_PENDING 置 0 影响 $published 行")
            LocalImportResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore 导入失败", e)
            pendingUri?.let { cleanupPendingRow(it) }
            LocalImportResult.Failure(e)
        }
    }

    /**
     * ≤28：核对写入权限（23–28 未授予直接失败、不弹申请框）→ 公共 `Music/导入/` 目录 → 解析重名 →
     * 写临时文件成功后 rename 到目标（失败删临时文件、目标已存在则不覆盖）→ scanFile 触发扫描。
     */
    private fun importViaLegacyFile(sourceUri: Uri): LocalImportResult {
        if (legacyImportRequiresWriteGrant(sdkInt) && !hasWritePermission()) {
            Log.w(TAG, "未授予 WRITE_EXTERNAL_STORAGE，直写导入失败")
            return LocalImportResult.Failure()
        }
        var tempFile: File? = null
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                IMPORT_FOLDER_NAME,
            )
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IOException("无法创建导入目录：$dir")
            }
            // 目录不存在时 list() 返回 null，按空集合处理
            val existing = dir.list()?.toSet() ?: emptySet()
            val displayName = resolveImportFileName(
                normalizeImportDisplayName(rawDisplayName(sourceUri)),
                existing,
            )
            val target = File(dir, displayName)
            val temp = File.createTempFile("import-", ".part", dir)
            tempFile = temp
            temp.outputStream().use { output -> copySource(sourceUri, output) }
            // 重名竞态兜底：目标已存在则不覆盖（纯函数是第一道防线，此处第二道；不加锁）
            if (target.exists()) throw IOException("目标文件已存在（重名竞态），不覆盖：$target")
            if (!temp.renameTo(target)) throw IOException("导入文件落位失败：$target")
            tempFile = null // 已 rename 到目标，无临时文件可清
            // 扫描触发是辅助步骤：文件已落位即导入成功，扫描失败只记日志（系统周期扫描会兜底收录）
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
            }.onFailure { Log.w(TAG, "触发媒体扫描失败", it) }
            LocalImportResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "直写导入失败", e)
            tempFile?.delete() // 清理半截临时文件，忽略删除结果
            LocalImportResult.Failure(e)
        }
    }

    /**
     * 打开来源输入流并复制到 [output]：来源 URI 读权限是来源 App 授予的临时 grant，复制在导入协程里
     * 尽快完成、不落盘 URI；来源不可读抛 [IOException] 由上层归一为失败。
     */
    private fun copySource(sourceUri: Uri, output: OutputStream) {
        context.contentResolver.openInputStream(sourceUri)?.use { input -> input.copyTo(output) }
            ?: throw IOException("来源音频不可读：$sourceUri")
    }

    /**
     * 29+ 目标目录现有文件名（重名检测）：只查主外置卷 `EXTERNAL_CONTENT_URI`（与插入目标同卷），
     * projection `DISPLAY_NAME`、selection `RELATIVE_PATH = ?` args [IMPORT_RELATIVE_PATH]（尾斜杠必须有）。
     * 不合并 SD 卡卷（写侧只关主卷）；不排除别家 App 在途 pending 行（更宽的重名网，避免与其将来可见的行撞名）。
     */
    private fun existingImportNames(): Set<String> {
        val names = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(IMPORT_RELATIVE_PATH),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0)) cursor.getString(0)?.let { names.add(it) }
            }
        }
        return names
    }

    /**
     * 来源展示名：先查 provider `OpenableColumns.DISPLAY_NAME`（微信/QQ 递出的 URI 尾段可能是编码路径
     * 而非文件名），取不到 / 查询抛错（grant 失效等）退回 `sourceUri.lastPathSegment`，再交纯函数规范化。
     */
    private fun rawDisplayName(sourceUri: Uri): String {
        val fromProvider = try {
            context.contentResolver.query(
                sourceUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "查询来源展示名失败，退回 URI 尾段", e)
            null
        }
        return fromProvider?.takeIf { it.isNotBlank() } ?: sourceUri.lastPathSegment.orEmpty()
    }

    /** 清理自有 pending 行（失败不留半截）：删除不弹系统框；清理失败只记日志、不掩盖原失败 */
    private fun cleanupPendingRow(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "清理导入 pending 行失败", e)
        }
    }

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "MediaStoreImporter"
    }
}