package com.cdndash.app.ui.navigation

import android.net.Uri

// 资源包明细页的路由（票据 05）：账号 id 作路径参数，进入的是「某个账号」的包列表，
// 与看板卡片一一对应；返回交给顶栏返回键与系统返回键。
// 独立成文件是为了让票据 08（花费页）等并行改动只碰 AppNavigation，不碰这里的常量。

/** 路径参数名；导航图与取参处共用，避免字符串写两遍写错。 */
const val PACKAGE_DETAIL_ACCOUNT_ARG: String = "accountId"

/** 导航图里的路由模板，如 `packages/{accountId}`。 */
const val PACKAGE_DETAIL_ROUTE: String = "packages/{" + PACKAGE_DETAIL_ACCOUNT_ARG + "}"

/** 生成具体账号的明细页路由；账号 id 正常是 UUID，仍做一次编码以防未来换成别的 id。 */
fun packageDetailRoute(accountId: String): String = "packages/${Uri.encode(accountId)}"
