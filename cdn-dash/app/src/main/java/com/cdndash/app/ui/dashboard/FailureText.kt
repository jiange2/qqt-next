package com.cdndash.app.ui.dashboard

import com.cdndash.app.data.aliyun.ApiFailureKind

/**
 * 三类错误的卡片文案（User Story 34）：标题可区分，且各自给一句「该去做什么」。
 *
 * 不合并成「出错了」：使用者要能在卡片上直接判断是去控制台查 AK、补 RAM 权限，
 * 还是先看网络。花费页的无权限提示由票据 08 单独负责，不在这里。
 */
fun ApiFailureKind.label(): String = when (this) {
    ApiFailureKind.CREDENTIAL_INVALID -> "凭证失效"
    ApiFailureKind.PERMISSION_DENIED -> "权限不足"
    ApiFailureKind.NETWORK -> "网络错误"
}

/** 与 [label] 配套的一句可操作提示。 */
fun ApiFailureKind.actionHint(): String = when (this) {
    ApiFailureKind.CREDENTIAL_INVALID ->
        "去阿里云控制台检查该账号的 AccessKey 是否被删除、禁用或轮换过"

    ApiFailureKind.PERMISSION_DENIED ->
        "该 RAM 用户缺 CDN 读权限，去控制台补授权后下拉重试"

    ApiFailureKind.NETWORK ->
        "检查网络后下拉重试"
}
