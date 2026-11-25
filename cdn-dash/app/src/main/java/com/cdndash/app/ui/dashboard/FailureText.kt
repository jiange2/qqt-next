package com.cdndash.app.ui.dashboard

import com.cdndash.app.data.aliyun.ApiFailureKind

/**
 * 全应用唯一的错误文案表（User Story 34）：标题可区分，且每类给一句「该去做什么」。
 *
 * 卡片、资源包摘要/明细页、花费页都从这里取文案，不再各自维护 when 表。
 * 不合并成「出错了」：使用者要能直接判断是去控制台查 AK、补 RAM 权限，还是先看网络。
 */

/** 三类错误的统一标签（GLOSSARY「凭证失效」）。 */
fun ApiFailureKind.label(): String = when (this) {
    ApiFailureKind.CREDENTIAL_INVALID -> "凭证失效"
    ApiFailureKind.PERMISSION_DENIED -> "权限不足"
    ApiFailureKind.NETWORK -> "网络错误"
}

/** 与 [label] 配套的一句可操作提示（看板卡片与资源包摘要/明细页共用）。 */
fun ApiFailureKind.actionHint(): String = when (this) {
    ApiFailureKind.CREDENTIAL_INVALID ->
        "去阿里云控制台检查该账号的 AccessKey 是否被删除、禁用或轮换过"

    ApiFailureKind.PERMISSION_DENIED ->
        "该 RAM 用户缺 CDN 读权限，去控制台补授权后下拉重试"

    ApiFailureKind.NETWORK ->
        "检查网络后下拉重试"
}

/**
 * 花费页标题：权限类要说清是「费用权限」而不是 CDN 权限（User Story 28），
 * 其余沿用 [label]。
 */
fun ApiFailureKind.spendLabel(): String = when (this) {
    ApiFailureKind.PERMISSION_DENIED -> "费用权限未开启"
    else -> label()
}

/**
 * 花费页的可操作提示。权限/鉴权类说明去哪里改；网络类在有上次成功账单时只说明
 * 「下方是上次数据」，不催用户刷新。
 *
 * @param cachedAtHourMinute 已有上次成功账单时其抓取时间的北京时间 `HH:mm`；没有旧账单时 null。
 */
fun ApiFailureKind.spendActionHint(cachedAtHourMinute: String?): String = when (this) {
    ApiFailureKind.PERMISSION_DENIED ->
        "该账号的只读用户缺少 bss:QueryAccountBill 权限。在 RAM 控制台补充后点「刷新」；" +
            "该账号的流量与资源包视图不受影响，其他账号也照常展示。"

    ApiFailureKind.CREDENTIAL_INVALID ->
        "该账号的 AccessKey 可能已失效或签名不匹配，去设置更新密钥后重试；其他账号不受影响。"

    ApiFailureKind.NETWORK -> if (cachedAtHourMinute != null) {
        "刷新失败，下面显示上次成功获取的账单（数据时间 $cachedAtHourMinute）。"
    } else {
        "请检查网络后点「刷新」重试。"
    }
}
