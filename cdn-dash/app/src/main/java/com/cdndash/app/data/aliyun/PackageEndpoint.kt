package com.cdndash.app.data.aliyun

import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.ResourcePackage
import java.time.Instant

/**
 * 资源包端点（票据 05 实现）。
 *
 * 计划参数：`DescribeCdnUserResourcePackage`，只接受 `CommodityCode=cdnflowbag`；
 * 分组、排序、「未生效」标注由纯函数完成（票据 05）。
 *
 * 本票据只留接缝：票据 05 只改这个文件。
 */
class PackageEndpoint(private val rpc: AliyunRpcClient) {

    suspend fun resourcePackages(
        account: AccountCredentials,
        now: Instant,
    ): ApiResult<List<ResourcePackage>> =
        TODO("票据 05：资源包余量（DescribeCdnUserResourcePackage，仅 cdnflowbag）")
}
