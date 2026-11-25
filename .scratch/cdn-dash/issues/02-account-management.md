# 02: 账号管理与加密落盘

**What to build:** 使用者能在设置页新增账号（自起名称 + AccessKeyId + AccessKeySecret）、删除、启用/停用、拖拽排序；看板按手动顺序只列启用账号，顺序不随流量变化。密钥只存本机且加密：Keystore 中生成不可导出的 AES-GCM 密钥，密文 base64 进 DataStore，删除账号同时清除密文与快照，密钥不进日志/备份/导出。设置页同时落「刷新间隔」与「关于」两项（刷新间隔供票据 03 读取，关于展示版本与「凭据存在本机」说明）。AccountStore 是唯一持久化接缝：调用方只传明文、不见密文。

**Blocked by:** 01

**Status:** resolved

- [x] 新增账号后看板立即出现该账号卡片壳（名称正确），停用后从看板消失、启用后回来
- [x] 删除账号后其密文与快照不再存在；仅删除 UI 条目不算完成
- [x] 拖拽排序持久化，重启应用后顺序保持；流量大小不影响顺序
- [x] 同名/空名称/空 key 的输入按表单校验拒绝并给出提示
- [x] AccountStore 用内存实现替换后有 JVM 测试：保存后读回一致、删除后不存在、快照带抓取时间（本机不开编译，测试照写）
- [x] Keystore 真实加解密与「删除不留残留」列入人工验收清单
- [x] 设置页有刷新间隔选择项（默认 5 分钟）与关于页内容；生物识别开关留出条目位置（实现归 09）

## 实现备注

- `AccountStore` 形态：`accounts: Flow<List<Account>>`（含 id/名称/启用/排序）、`refreshIntervalMinutes: Flow<Int>`、`credentials(id)`、`addAccount(name, ak, sk)`、`deleteAccount(id)`、`setEnabled(id, enabled)`、`reorderAccounts(ids)`、`saveSnapshot(accountId, module, json, fetchedAtMillis)`、`snapshot(accountId, module)`、`clearSnapshots(accountId)`、`setRefreshIntervalMinutes`。
- 生产实现 `DataStoreAccountStore` + `KeystoreCredentialCipher`（`AndroidKeyStore`，固定别名 `cdn_dash_credentials_v1`，AES-256-GCM，密文/IV base64 存同一个 Preferences DataStore）；内存 fake `InMemoryAccountStore` 供 JVM 测试。
- 设置页在 `ui/settings/SettingsScreen.kt`，拖拽列表在 `ui/settings/AccountReorderList.kt`；看板卡片壳在 `ui/dashboard/DashboardScreen.kt`；快照模块枚举为今日累计 / 7 天走势 / 资源包 / 月度账单。
- 本机仅 JDK 8、无 Android SDK 与 gradle 命令，未编译、未跑测试。

## 人工验收（真机，未执行）

- [ ] 新增账号后杀进程重启：账号仍在、名称与启用状态一致，Keystore 密钥不可导出
- [ ] `logcat` 与崩溃日志中不出现 AccessKeyId/AccessKeySecret 明文、密文或 IV
- [ ] 删除账号后检查应用私有 DataStore：该账号的密文/IV 条目与四个模块快照键均已消失
- [ ] 拖拽排序后杀进程重启：顺序与拖拽结果一致；流量数字变化不影响顺序
- [ ] 停用后看板消失、重新启用后回到原顺序位置
