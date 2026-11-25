package com.cdndash.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.cdnDashDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "cdn_dash_accounts")

/** 生产环境的 AccountStore 组装点：DataStore 单例 + Android Keystore 加解密。 */
object AccountStoreProvider {

    fun create(context: Context): AccountStore = DataStoreAccountStore(
        dataStore = context.applicationContext.cdnDashDataStore,
        cipher = KeystoreCredentialCipher(),
    )
}
