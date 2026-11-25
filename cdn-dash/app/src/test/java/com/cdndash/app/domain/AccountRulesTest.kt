package com.cdndash.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 账号规则纯函数测试（票据 02）：看板只列启用账号且严格按手动顺序，
 * 新增表单拒绝空值与重名。字面量断言，不依赖 Android 框架。
 */
class AccountRulesTest {

    @Test
    fun `看板只列启用账号并按手动顺序排列`() {
        val accounts = listOf(
            Account(id = "a", name = "A", enabled = true, sortOrder = 2),
            Account(id = "b", name = "B", enabled = false, sortOrder = 0),
            Account(id = "c", name = "C", enabled = true, sortOrder = 1),
        )

        assertEquals(
            listOf("c", "a"),
            visibleAccounts(accounts).map { it.id },
        )
    }

    @Test
    fun `全部停用时看板为空列表`() {
        val accounts = listOf(
            Account(id = "a", name = "A", enabled = false, sortOrder = 0),
            Account(id = "b", name = "B", enabled = false, sortOrder = 1),
        )

        assertEquals(emptyList<Account>(), visibleAccounts(accounts))
    }

    @Test
    fun `空名称空 AccessKeyId 空 Secret 分别被拒绝`() {
        assertEquals(
            AccountFormError.BlankName,
            validateNewAccount("   ", "AK", "SK", emptyList()),
        )
        assertEquals(
            AccountFormError.BlankAccessKeyId,
            validateNewAccount("主站生产", "  ", "SK", emptyList()),
        )
        assertEquals(
            AccountFormError.BlankAccessKeySecret,
            validateNewAccount("主站生产", "AK", " ", emptyList()),
        )
    }

    @Test
    fun `重名按去首尾空白后比较`() {
        assertEquals(
            AccountFormError.DuplicateName,
            validateNewAccount(" 主站生产 ", "AK", "SK", listOf("主站生产")),
        )
    }

    @Test
    fun `合法输入通过校验`() {
        assertNull(
            validateNewAccount(
                name = "海外测试",
                accessKeyId = "AK",
                accessKeySecret = "SK",
                existingNames = listOf("主站生产"),
            ),
        )
    }
}
