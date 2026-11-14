package com.qqt.music.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地音乐删除接缝（[LocalMusicDeleter]，spec「分版本删除链路」，票据01）的纯函数行为测试：
 * 版本路由、写入权限运行时申请门槛、删除结果归一。只测外部行为，不测内部实现（spec「Testing Decisions」）。
 *
 * 这些口径守住的是分版本链路的边界：30+ 走系统删除框、29 走 RecoverableSecurityException 授权、
 * ≤28 申请 WRITE_EXTERNAL_STORAGE 直接删；运行时权限框只在 23–28 出现（<23 安装即授予、29+ 不申请）。
 * 失败结果还带「文件是否确认已不在」的判定：只有 delete 返回非正行数才置位（灰显是持久化且阻断播放的
 * 状态，要正面证据），其余失败一律保持可播。
 * 系统框 / ContentResolver / MediaStore 的真实交互依赖框架，留真机手工验收（本仓不跑 Gradle）。
 * 断言用独立字面量（结果码 -1/0/1 即 RESULT_OK / RESULT_CANCELED / RESULT_FIRST_USER），不与实现同源。
 */
class LocalMusicDeleterTest {

    // ========== 版本路由 ==========

    @Test
    fun `Android 11 及以上走系统删除请求`() {
        assertEquals(DeleteRoute.SystemRequest, deleteRoute(30))
        assertEquals(DeleteRoute.SystemRequest, deleteRoute(31))
        assertEquals(DeleteRoute.SystemRequest, deleteRoute(35))
    }

    @Test
    fun `Android 10 走 RecoverableSecurityException 授权`() {
        assertEquals(DeleteRoute.RecoverableSecurity, deleteRoute(29))
    }

    @Test
    fun `Android 9 及以下走写入权限直接删`() {
        assertEquals(DeleteRoute.DirectWrite, deleteRoute(28))
        assertEquals(DeleteRoute.DirectWrite, deleteRoute(23))
        assertEquals(DeleteRoute.DirectWrite, deleteRoute(22))
        assertEquals(DeleteRoute.DirectWrite, deleteRoute(21))
    }

    // ========== 写入权限运行时申请门槛 ==========

    @Test
    fun `仅 23 到 28 需要运行时申请写入权限`() {
        assertTrue(writePermissionRequestRequired(23))
        assertTrue(writePermissionRequestRequired(28))
    }

    @Test
    fun `23 以下安装即授予不运行时申请`() {
        // 运行时权限框从 API 23 起才有；21–22 清单声明即授予，跳过申请直接删
        assertFalse(writePermissionRequestRequired(21))
        assertFalse(writePermissionRequestRequired(22))
    }

    @Test
    fun `29 及以上不申请写入权限`() {
        // 29 走 RSE 授权（scoped storage 下 WRITE 不授予删除能力），30+ 走系统框，均不碰 WRITE
        assertFalse(writePermissionRequestRequired(29))
        assertFalse(writePermissionRequestRequired(30))
        assertFalse(writePermissionRequestRequired(35))
    }

    // ========== 删除结果归一 ==========

    @Test
    fun `删除行数大于零归一为成功`() {
        assertEquals(LocalDeleteResult.Success, normalizeDeleteRows(1))
        assertEquals(LocalDeleteResult.Success, normalizeDeleteRows(5))
    }

    @Test
    fun `删除行数为零或负归一为失败并确认文件已不在`() {
        // 0 行 = 文件已被外部删除或 URI 失效：失败结果而非崩溃；
        // 这是唯一「确认媒体库行已不在」的失败证据 → fileUnavailable 置位，UI 据此灰显该行
        assertEquals(LocalDeleteResult.Failure(fileUnavailable = true), normalizeDeleteRows(0))
        assertEquals(LocalDeleteResult.Failure(fileUnavailable = true), normalizeDeleteRows(-1))
        assertNull((normalizeDeleteRows(0) as LocalDeleteResult.Failure).cause)
    }

    @Test
    fun `系统删除框允许归一为成功`() {
        // -1 = Activity.RESULT_OK：30+ 系统框允许即已删
        assertEquals(LocalDeleteResult.Success, normalizeSystemDeleteResult(-1))
    }

    @Test
    fun `系统删除框取消归一为取消`() {
        // 0 = Activity.RESULT_CANCELED
        assertEquals(LocalDeleteResult.Cancelled, normalizeSystemDeleteResult(0))
    }

    @Test
    fun `系统删除框其它结果码归一为失败`() {
        // 1 = Activity.RESULT_FIRST_USER（非 OK 非 CANCELED）
        val failure = normalizeSystemDeleteResult(1) as LocalDeleteResult.Failure
        assertEquals(LocalDeleteResult.Failure(), failure)
        assertNull(failure.cause)
        // 异常结果码不证明文件不可播：fileUnavailable 保持默认 false，行不灰显
        assertFalse(failure.fileUnavailable)
    }

    @Test
    fun `失败结果默认不判定文件不可用`() {
        // 灰显是持久化且阻断播放的状态，只有「确认媒体库行已不在」才置位；
        // 默认失败与 provider 异常都不证明文件不可播，必须保持可播
        assertFalse(LocalDeleteResult.Failure().fileUnavailable)
        assertFalse(LocalDeleteResult.Failure(IllegalStateException("provider 出错")).fileUnavailable)
    }

    @Test
    fun `系统框结果码分类供 29 授权后重试判定`() {
        assertEquals(SystemDialogOutcome.Approved, systemDialogOutcome(-1))
        assertEquals(SystemDialogOutcome.Cancelled, systemDialogOutcome(0))
        assertEquals(SystemDialogOutcome.Failed, systemDialogOutcome(1))
        assertEquals(SystemDialogOutcome.Failed, systemDialogOutcome(2))
    }
}