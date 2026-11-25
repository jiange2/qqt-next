package com.cdndash.app

import android.app.Application
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.AccountStoreProvider
import com.cdndash.app.data.aliyun.AliyunApi
import com.cdndash.app.data.aliyun.AliyunApiProvider

/** 持有进程级的 AccountStore 与 AliyunApi，避免每次进入页面重新打开 DataStore/Keystore/连接池。 */
class CdnDashApplication : Application() {

    val accountStore: AccountStore by lazy { AccountStoreProvider.create(this) }

    val aliyunApi: AliyunApi by lazy { AliyunApiProvider.create() }
}
