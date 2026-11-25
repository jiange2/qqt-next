package com.cdndash.app.domain

/**
 * 看板只列启用账号，并严格按手动顺序展示（User Story 7：顺序不因流量大小变化）。
 * 纯函数，供 DashboardViewModel 使用，也可独立测试。
 */
fun visibleAccounts(accounts: List<Account>): List<Account> =
    accounts
        .asSequence()
        .filter { it.enabled }
        .sortedBy { it.sortOrder }
        .toList()

/** 新增账号表单的校验失败原因；消息直接展示给使用者。 */
enum class AccountFormError(val message: String) {
    BlankName("请填写账号名称"),
    BlankAccessKeyId("请填写 AccessKeyId"),
    BlankAccessKeySecret("请填写 AccessKeySecret"),
    DuplicateName("账号名称已存在"),
}

/**
 * 新增账号的输入校验：名称/密钥不得为空，名称不得与现有账号重名（去首尾空白后比较）。
 * 返回 null 表示可以保存。
 */
fun validateNewAccount(
    name: String,
    accessKeyId: String,
    accessKeySecret: String,
    existingNames: Collection<String>,
): AccountFormError? {
    val trimmedName = name.trim()
    return when {
        trimmedName.isEmpty() -> AccountFormError.BlankName
        accessKeyId.isBlank() -> AccountFormError.BlankAccessKeyId
        accessKeySecret.isBlank() -> AccountFormError.BlankAccessKeySecret
        existingNames.any { it.trim() == trimmedName } -> AccountFormError.DuplicateName
        else -> null
    }
}
