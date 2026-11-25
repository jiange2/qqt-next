package com.cdndash.app

import android.app.Application
import com.cdndash.app.data.AccountStore
import com.cdndash.app.data.AccountStoreProvider

/** 持有进程级 AccountStore，避免每次进入页面重新打开 DataStore/Keystore。 */
class CdnDashApplication : Application() {

    val accountStore: AccountStore by lazy { AccountStoreProvider.create(this) }
}
