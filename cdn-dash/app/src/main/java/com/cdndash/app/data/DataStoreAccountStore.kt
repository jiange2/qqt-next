package com.cdndash.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cdndash.app.domain.Account
import com.cdndash.app.domain.AccountCredentials
import com.cdndash.app.domain.AccountSnapshot
import com.cdndash.app.domain.SnapshotModule
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * AccountStore 的生产实现：账号元数据、凭据密文、快照与刷新间隔全部落在同一个
 * Preferences DataStore 中，密文由 [CredentialCipher] 产生。
 *
 * 明文凭据只在 [addAccount] 入口出现一次，随即加密；读取凭据时才解密。
 * 任何路径都不打印密钥、密文或 IV，也没有导出入口。
 */
class DataStoreAccountStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: CredentialCipher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AccountStore {

    private val readableData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val accounts: Flow<List<Account>> = readableData.map { preferences ->
        preferences.readAccounts()
            .sortedBy { it.sortOrder }
            .map { it.toAccount() }
    }

    override val refreshIntervalMinutes: Flow<Int> = readableData.map { preferences ->
        preferences[REFRESH_INTERVAL_MINUTES]
            ?: AccountStore.DEFAULT_REFRESH_INTERVAL_MINUTES
    }

    override val biometricEnabled: Flow<Boolean> = readableData.map { preferences ->
        preferences[BIOMETRIC_ENABLED] ?: AccountStore.DEFAULT_BIOMETRIC_ENABLED
    }

    override suspend fun credentials(accountId: String): AccountCredentials? {
        val preferences = dataStore.data.first()
        val ciphertext = preferences[credentialCiphertextKey(accountId)] ?: return null
        val iv = preferences[credentialIvKey(accountId)] ?: return null
        // 解密失败时不返回半份凭据，也不输出密文/IV/明文。
        val plaintext = cipher.open(ciphertext, iv) ?: return null
        val stored = try {
            json.decodeFromString(StoredCredential.serializer(), plaintext)
        } catch (error: SerializationException) {
            return null
        }
        return AccountCredentials(
            accessKeyId = stored.accessKeyId,
            accessKeySecret = stored.accessKeySecret,
        )
    }

    override suspend fun addAccount(
        name: String,
        accessKeyId: String,
        accessKeySecret: String,
    ): Account {
        val id = UUID.randomUUID().toString()
        val sealed = cipher.seal(
            json.encodeToString(
                StoredCredential.serializer(),
                StoredCredential(accessKeyId = accessKeyId, accessKeySecret = accessKeySecret),
            ),
        )
        var created: Account? = null
        dataStore.edit { preferences ->
            val existing = preferences.readAccounts()
            val account = Account(
                id = id,
                name = name,
                enabled = true,
                sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            )
            preferences[ACCOUNTS_JSON] = json.encodeToString(
                ACCOUNTS_SERIALIZER,
                existing + account.toStored(),
            )
            preferences[credentialCiphertextKey(id)] = sealed.ciphertextBase64
            preferences[credentialIvKey(id)] = sealed.ivBase64
            created = account
        }
        return checkNotNull(created)
    }

    override suspend fun deleteAccount(accountId: String) {
        dataStore.edit { preferences ->
            preferences[ACCOUNTS_JSON] = json.encodeToString(
                ACCOUNTS_SERIALIZER,
                preferences.readAccounts().filterNot { it.id == accountId },
            )
            preferences.remove(credentialCiphertextKey(accountId))
            preferences.remove(credentialIvKey(accountId))
            SnapshotModule.entries.forEach { module ->
                preferences.remove(snapshotPayloadKey(accountId, module))
                preferences.remove(snapshotFetchedAtKey(accountId, module))
            }
        }
    }

    override suspend fun setEnabled(accountId: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val updated = preferences.readAccounts().map { stored ->
                if (stored.id == accountId) stored.copy(enabled = enabled) else stored
            }
            preferences[ACCOUNTS_JSON] =
                json.encodeToString(ACCOUNTS_SERIALIZER, updated)
        }
    }

    override suspend fun reorderAccounts(orderedIds: List<String>) {
        dataStore.edit { preferences ->
            val current = preferences.readAccounts()
            val byId = current.associateBy { it.id }
            val requested = orderedIds.distinct().mapNotNull { byId[it] }
            val remaining = current.filterNot { it.id in orderedIds }
            val reordered = (requested + remaining).mapIndexed { index, stored ->
                stored.copy(sortOrder = index)
            }
            preferences[ACCOUNTS_JSON] =
                json.encodeToString(ACCOUNTS_SERIALIZER, reordered)
        }
    }

    override suspend fun saveSnapshot(
        accountId: String,
        module: SnapshotModule,
        payloadJson: String,
        fetchedAtMillis: Long,
    ) {
        dataStore.edit { preferences ->
            preferences[snapshotPayloadKey(accountId, module)] = payloadJson
            preferences[snapshotFetchedAtKey(accountId, module)] = fetchedAtMillis
        }
    }

    override suspend fun snapshot(
        accountId: String,
        module: SnapshotModule,
    ): AccountSnapshot? {
        val preferences = dataStore.data.first()
        val payloadJson = preferences[snapshotPayloadKey(accountId, module)] ?: return null
        val fetchedAtMillis =
            preferences[snapshotFetchedAtKey(accountId, module)] ?: return null
        return AccountSnapshot(
            accountId = accountId,
            module = module,
            payloadJson = payloadJson,
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    override suspend fun clearSnapshots(accountId: String) {
        dataStore.edit { preferences ->
            SnapshotModule.entries.forEach { module ->
                preferences.remove(snapshotPayloadKey(accountId, module))
                preferences.remove(snapshotFetchedAtKey(accountId, module))
            }
        }
    }

    override suspend fun setRefreshIntervalMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_MINUTES] = minutes
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }

    private fun Preferences.readAccounts(): List<StoredAccount> {
        val raw = this[ACCOUNTS_JSON] ?: return emptyList()
        return try {
            json.decodeFromString(ACCOUNTS_SERIALIZER, raw)
        } catch (error: SerializationException) {
            emptyList()
        } catch (error: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        val ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val ACCOUNTS_SERIALIZER = ListSerializer(StoredAccount.serializer())

        fun credentialCiphertextKey(accountId: String) =
            stringPreferencesKey("credential.$accountId.ciphertext")

        fun credentialIvKey(accountId: String) =
            stringPreferencesKey("credential.$accountId.iv")

        fun snapshotPayloadKey(accountId: String, module: SnapshotModule) =
            stringPreferencesKey("snapshot.$accountId.${module.name}.payload")

        fun snapshotFetchedAtKey(accountId: String, module: SnapshotModule) =
            longPreferencesKey("snapshot.$accountId.${module.name}.fetched_at")
    }
}

/** DataStore 内部的稳定存储格式，与领域模型解耦，便于后续迁移。 */
@Serializable
private data class StoredAccount(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    fun toAccount(): Account = Account(
        id = id,
        name = name,
        enabled = enabled,
        sortOrder = sortOrder,
    )
}

private fun Account.toStored(): StoredAccount = StoredAccount(
    id = id,
    name = name,
    enabled = enabled,
    sortOrder = sortOrder,
)

/** 加密前的凭据载荷；只在 Keystore 密文里存在。 */
@Serializable
private data class StoredCredential(
    val accessKeyId: String,
    val accessKeySecret: String,
)
