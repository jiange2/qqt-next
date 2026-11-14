package com.qqt.music.data.local

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地音乐删除的唯一接缝（spec「分版本删除链路」，票据01）：输入本地歌曲（[LocalSong.contentUri]
 * 已是按卷 + 行 ID 构造好的 content URI），按系统版本路由三条链路，输出归一化删除结果
 * （[LocalDeleteResult]：成功 / 失败 / 用户取消）。UI 与 ViewModel 只调它，不直接碰 MediaStore 删除 API。
 *
 * 系统框经 UI 层注册的 `ActivityResultLauncher<IntentSenderRequest>` 拉起（沿用现有权限申请 /
 * Activity result 回调模式），本接缝负责版本路由与结果归一、不持有 Activity 或 launcher。
 * 两步系统框流程以「动作 → 回调」交接：[startDelete] 产出 UI 下一步该做的动作（[LocalDeleteAction]），
 * UI 执行后把原始结果喂回 [onSystemDialogResult] / [onWritePermissionResult]，直到拿到 Finished。
 *
 * 分版本链路（与 [MediaStoreLibrarySource] 同口径注入 sdkInt，纯路由见 [deleteRoute]）：
 * - Android 11+（API 30+）：`MediaStore.createDeleteRequest` 拉系统确认框，允许后系统执行删除（系统框即唯一确认）
 * - Android 10（API 29）：`resolver.delete` 捕获 `RecoverableSecurityException` 拉系统授权框，授权后重试删除
 * - Android 9 及以下（≤28）：23+ 运行时申请 `WRITE_EXTERNAL_STORAGE`（<23 安装即授予）后 `resolver.delete` 直接删
 */
interface LocalMusicDeleter {

    /**
     * 开始删除一首歌：按版本路由，返回 UI 下一步动作——拉系统框（[LocalDeleteAction.LaunchSystemDialog]）、
     * 申请写入权限（[LocalDeleteAction.RequestWritePermission]），或已直接得出结果（[LocalDeleteAction.Finished]）。
     * 文件已被外部删除 / URI 失效时返回失败结果而非崩溃。
     */
    suspend fun startDelete(song: LocalSong): LocalDeleteAction

    /**
     * 系统框（30+ 删除确认 / 29 授权）回调：[resultCode] 取 `ActivityResult.resultCode`。
     * 30+ 允许即已删；29 允许后重试删除，取消 / 失败归一。返回下一步动作（通常为 Finished）。
     */
    suspend fun onSystemDialogResult(song: LocalSong, resultCode: Int): LocalDeleteAction

    /**
     * 写入权限申请回调（仅 ≤28 链路）：授予则直接删，拒绝则失败（不删）。返回 Finished。
     */
    suspend fun onWritePermissionResult(song: LocalSong, granted: Boolean): LocalDeleteAction
}

/**
 * 归一化删除结果（镜像 [LocalLibraryResult] 风格）：成功 / 失败 / 用户取消三态。
 * 失败带可空原因——「已删 / URI 失效」（delete 返回 0 行）与「拒绝写入权限」无异常，原因为 null；
 * provider 抛错时带上异常供排查。取消 = 用户在系统框 / 权限框拒绝，行保持原样。
 */
sealed interface LocalDeleteResult {
    /** 文件已从设备存储真正删除（其他应用也不再可见） */
    data object Success : LocalDeleteResult

    /**
     * 删除失败：[cause] 为 provider 异常，或 null（已删 / URI 失效 / 拒绝写入权限）。
     * [fileUnavailable] 仅在「确认媒体库行已不在」时为 true（`resolver.delete` 返回非正行数——
     * 文件已被外部删除 / URI 失效）；其余一切失败（provider 异常、拒绝写入权限、非 OK/非 CANCELED
     * 的异常框结果码）都保持 false——它们不证明文件不可播。灰显是落盘且阻断播放的状态，
     * 必须要求正面证据才置位；真不可播的文件仍会被既有播放失败链路（unavailableLocalSongId）
     * 捕获灰显，并在下次刷新时自然消失。
     */
    data class Failure(
        val cause: Throwable? = null,
        val fileUnavailable: Boolean = false,
    ) : LocalDeleteResult

    /** 用户取消（系统框 / 权限框拒绝）：不删、行保持原样 */
    data object Cancelled : LocalDeleteResult
}

/**
 * UI 下一步动作（两步系统框流程的交接物）：接缝不持有 launcher，只产出动作，UI 执行后回调归一。
 */
sealed interface LocalDeleteAction {
    /** 拉起系统框：30+ 删除确认框，或 29 RecoverableSecurityException 授权框 */
    data class LaunchSystemDialog(val request: IntentSenderRequest) : LocalDeleteAction

    /** 运行时申请写入权限（仅 23–28）：[permission] 即 `WRITE_EXTERNAL_STORAGE`，UI 用它 launch 权限框 */
    data class RequestWritePermission(val permission: String) : LocalDeleteAction

    /** 终态：删除已得出归一化结果，UI 据此移除行 / Toast / 不动 */
    data class Finished(val result: LocalDeleteResult) : LocalDeleteAction
}

/** 删除链路按系统版本路由（spec「分版本删除链路」）：30+ 系统删除框 / 29 RSE 授权 / ≤28 写入权限直接删 */
enum class DeleteRoute { SystemRequest, RecoverableSecurity, DirectWrite }

/**
 * 版本路由（纯函数，JVM 可测）：Android 11+（API 30+）走 `createDeleteRequest` 系统框；
 * Android 10（API 29）走 `RecoverableSecurityException` 授权；Android 9 及以下（≤28）走 WRITE 直接删。
 * UI 也用它判断是否需要在删除前弹 App 确认框（仅 [DeleteRoute.DirectWrite]，spec「确认交互」）。
 */
fun deleteRoute(sdkInt: Int): DeleteRoute = when {
    sdkInt >= 30 -> DeleteRoute.SystemRequest
    sdkInt == 29 -> DeleteRoute.RecoverableSecurity
    else -> DeleteRoute.DirectWrite
}

/**
 * 是否需要运行时申请 `WRITE_EXTERNAL_STORAGE`（纯函数，JVM 可测）：仅 ≤28 链路且 API 23+。
 * 29 走 RSE 授权——scoped storage 下 WRITE 不授予删除能力，申请无意义；30+ 走系统框，不碰 WRITE。
 * <23 没有运行时权限框（清单声明即安装时授予），跳过申请直接删（与 [MediaStoreLibrarySource.hasAudioPermission]
 * 对 sdkInt < 23 返回 true 同口径）。
 */
internal fun writePermissionRequestRequired(sdkInt: Int): Boolean =
    deleteRoute(sdkInt) == DeleteRoute.DirectWrite && sdkInt >= 23

/** 系统框结果码分类（纯函数，JVM 可测）：供 30+ 归一与 29「授权后重试」判定共用 */
internal enum class SystemDialogOutcome { Approved, Cancelled, Failed }

/**
 * 系统框结果码 → 分类（纯函数，JVM 可测）。`Activity.RESULT_OK` / `RESULT_CANCELED` 是编译期常量
 * （kotlinc 内联，JVM 运行不需要 android.jar 方法桩，与 [mediaStoreProjection] 引用列常量同口径）。
 */
internal fun systemDialogOutcome(resultCode: Int): SystemDialogOutcome = when (resultCode) {
    Activity.RESULT_OK -> SystemDialogOutcome.Approved
    Activity.RESULT_CANCELED -> SystemDialogOutcome.Cancelled
    else -> SystemDialogOutcome.Failed
}

/**
 * 直接删除行数 → 归一结果（纯函数，JVM 可测）：`resolver.delete` 返回删除行数，>0 即成功；
 * 0（文件已被外部删除 / URI 失效）或负值 → 失败（不带异常），不崩溃。
 * 非正行数是唯一「确认媒体库行已不在」的失败证据 → [LocalDeleteResult.Failure.fileUnavailable]
 * 置位供 UI 灰显该行；其余失败构造一律保持默认 false（见 [LocalDeleteResult.Failure]）。
 */
internal fun normalizeDeleteRows(rowsDeleted: Int): LocalDeleteResult =
    if (rowsDeleted > 0) LocalDeleteResult.Success else LocalDeleteResult.Failure(fileUnavailable = true)

/**
 * 30+ 系统删除框结果码 → 归一结果（纯函数，JVM 可测）：允许即系统已删（成功），取消即用户取消，
 * 其它结果码 → 失败。29 授权框不走这里（允许后还要重试删除），由 impl 按 [systemDialogOutcome] 分支。
 */
internal fun normalizeSystemDeleteResult(resultCode: Int): LocalDeleteResult =
    when (systemDialogOutcome(resultCode)) {
        SystemDialogOutcome.Approved -> LocalDeleteResult.Success
        SystemDialogOutcome.Cancelled -> LocalDeleteResult.Cancelled
        SystemDialogOutcome.Failed -> LocalDeleteResult.Failure()
    }

/**
 * MediaStore 系统媒体库删除实现（spec，沿用 ADR 0022）：版本路由 + 结果归一为纯函数（JVM 可测），
 * 框架交互（`createDeleteRequest` / `RecoverableSecurityException` / `resolver.delete`）留真机手工验收。
 * 与 [MediaStoreLibrarySource] 同口径注入 sdkInt；不持有 Activity 或 launcher（系统框由 UI 拉起）。
 */
class MediaStoreDeleter(
    private val context: Context,
    /** 注入设备 API 级别：删除链路按它分支（测试可指定） */
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : LocalMusicDeleter {

    override suspend fun startDelete(song: LocalSong): LocalDeleteAction =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(song.contentUri)
            when (deleteRoute(sdkInt)) {
                DeleteRoute.SystemRequest -> startSystemDeleteRequest(uri)
                DeleteRoute.RecoverableSecurity -> startRecoverableDelete(uri)
                DeleteRoute.DirectWrite -> startLegacyDelete(uri)
            }
        }

    override suspend fun onSystemDialogResult(song: LocalSong, resultCode: Int): LocalDeleteAction =
        withContext(Dispatchers.IO) {
            when (deleteRoute(sdkInt)) {
                // 30+：系统框允许即已删，直接归一
                DeleteRoute.SystemRequest ->
                    LocalDeleteAction.Finished(normalizeSystemDeleteResult(resultCode))
                // 29：授权框允许后重试删除；取消 / 失败归一
                DeleteRoute.RecoverableSecurity -> when (systemDialogOutcome(resultCode)) {
                    SystemDialogOutcome.Approved ->
                        LocalDeleteAction.Finished(deleteDirectly(Uri.parse(song.contentUri)))
                    SystemDialogOutcome.Cancelled ->
                        LocalDeleteAction.Finished(LocalDeleteResult.Cancelled)
                    SystemDialogOutcome.Failed ->
                        LocalDeleteAction.Finished(LocalDeleteResult.Failure())
                }
                // ≤28 不经系统框；防御性兜底（正常流程不会走到）
                DeleteRoute.DirectWrite ->
                    LocalDeleteAction.Finished(LocalDeleteResult.Failure())
            }
        }

    override suspend fun onWritePermissionResult(song: LocalSong, granted: Boolean): LocalDeleteAction =
        withContext(Dispatchers.IO) {
            if (!granted) {
                // 拒绝写入权限 → 不删、失败（spec 用户故事 6 / 票据01）
                LocalDeleteAction.Finished(LocalDeleteResult.Failure())
            } else {
                LocalDeleteAction.Finished(deleteDirectly(Uri.parse(song.contentUri)))
            }
        }

    /** 30+：createDeleteRequest 拉系统确认框（系统框即唯一确认，允许后系统执行删除） */
    private fun startSystemDeleteRequest(uri: Uri): LocalDeleteAction = try {
        val pending = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
        LocalDeleteAction.LaunchSystemDialog(IntentSenderRequest.Builder(pending).build())
    } catch (e: Exception) {
        // URI 失效 / 已被外部删除：createDeleteRequest 抛错 → 失败结果，不崩溃
        Log.w(TAG, "创建系统删除请求失败", e)
        LocalDeleteAction.Finished(LocalDeleteResult.Failure(e))
    }

    /** 29：先直接删，捕获 RecoverableSecurityException 拉系统授权框（授权后由回调重试） */
    private fun startRecoverableDelete(uri: Uri): LocalDeleteAction = try {
        LocalDeleteAction.Finished(normalizeDeleteRows(deleteRows(uri)))
    } catch (e: RecoverableSecurityException) {
        // RSE 必须先于 Exception 捕获（它是 SecurityException 子类）：授权框 PendingIntent 在 userAction 里
        val pending = e.userAction.actionIntent
        LocalDeleteAction.LaunchSystemDialog(IntentSenderRequest.Builder(pending).build())
    } catch (e: Exception) {
        Log.w(TAG, "媒体库删除失败", e)
        LocalDeleteAction.Finished(LocalDeleteResult.Failure(e))
    }

    /** ≤28：23+ 且未授予写入权限时先申请，否则（含 <23 安装即授予）直接删 */
    private fun startLegacyDelete(uri: Uri): LocalDeleteAction =
        if (writePermissionRequestRequired(sdkInt) && !hasWritePermission()) {
            LocalDeleteAction.RequestWritePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            LocalDeleteAction.Finished(deleteDirectly(uri))
        }

    /** 直接删除并归一（≤28 与 29 授权后重试共用）：行数 >0 成功，0 / 异常 → 失败 */
    private fun deleteDirectly(uri: Uri): LocalDeleteResult = try {
        normalizeDeleteRows(deleteRows(uri))
    } catch (e: Exception) {
        Log.w(TAG, "媒体库删除失败", e)
        LocalDeleteResult.Failure(e)
    }

    /** 原始删除（不吞异常）：29 首删需让 RSE 冒泡到 [startRecoverableDelete] 拉授权框 */
    private fun deleteRows(uri: Uri): Int = context.contentResolver.delete(uri, null, null)

    private fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "MediaStoreDeleter"
    }
}