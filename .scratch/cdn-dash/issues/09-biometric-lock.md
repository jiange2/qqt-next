# 09: 生物识别解锁开关

**What to build:** 设置页有生物识别开关，默认关闭；开启后进入应用前要求一次生物识别，失败可回退到设备凭据。设备不支持生物识别时开关隐藏并给出说明。该开关的任何失败都不得阻碍正常使用（例如传感器报错时放行或提示重试，而不是锁死）。密码学材料仍由 Keystore 默认保护解析，不做额外加密格式变更。

**Blocked by:** 02

**Status:** resolved

- [x] 默认关闭；开启后冷启动先验生物识别，通过才进入看板
- [x] 验证失败可回退到设备凭据（PIN/图案）
- [x] 设备不支持时开关不可见并说明原因
- [x] 生物识别异常不导致应用无法进入
- [x] 真机行为（是否隐藏、回退路径）列入人工验收清单

## 实现备注

- `AccountStore` 增加 `biometricEnabled: Flow<Boolean>` 与 `setBiometricEnabled`，常量 `DEFAULT_BIOMETRIC_ENABLED = false`；`DataStoreAccountStore` 落在同一个 Preferences DataStore（键 `biometric_enabled`），`InMemoryAccountStore` 用 `MutableStateFlow(false)`。凭据加密格式与 Keystore 未动。
- 门禁落点：`MainActivity` 改为 `FragmentActivity`（BiometricPrompt 的宿主要求），在 `CdnDashTheme` 内、`CdnDashApp(导航根)` 外层套 `ui/lock/AppLockGate.kt`。状态机在 `AppLockViewModel`：`Reading → Unlocked | Locked(attempt) | Retry(reason) | Bypassed(reason)`；状态放 ViewModel 是为了转屏不重复验、冷启动（新进程）必然重验。`Reading` 只渲染空白背景，避免开关关闭时闪一帧锁屏、开关开启时闪一帧看板。
- 验证方式：`ui/lock/BiometricUnlock.kt` 封装 `androidx.biometric.BiometricPrompt`；API 30+ 用 `setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)`，26–29 用 `setDeviceCredentialAllowed(true)`，两条路都允许回退到 PIN/图案/密码。
- 不锁死的取舍（`ui/lock/LockPolicy.kt` 一行注释 + 纯函数）：用户取消 → 保持门禁只给「重试」，不入内；`ERROR_LOCKOUT` / `ERROR_LOCKOUT_PERMANENT` / `ERROR_NO_DEVICE_CREDENTIAL` / 硬件缺失 / 传感器异常 / 超时 / 厂商错误 / 弹窗抛异常 → 「无法验证，临时放行」提示，点一下进入；开关开着但设备当前已验不了（指纹被删、硬件被停用）同样走该提示。
- 设置页：开关接真实状态，默认关；`BiometricManager.canAuthenticate(BIOMETRIC_WEAK)` 非 `SUCCESS` 时隐藏开关，并在原位置显示原因（无硬件 / 未录入 / 传感器停用 / 需安全更新 / 状态未知）。
- 依赖：`gradle/libs.versions.toml` 新增 `fragment = "1.8.5"` 与 `androidx-fragment`，`app/build.gradle.kts` 显式引入（biometric 的 fragment 传递依赖很旧，钉新版本以免与 activity-compose 1.9.3 / lifecycle 2.8.7 错配）。
- 未碰 `DashboardScreen` / `DashboardViewModel` / 花费页，给票据 03/08 留出接缝。
- 本机仅 JDK 8、无 Android SDK 与 gradle 命令：**未编译、未跑测试**。

## 人工验收（真机，未执行）

- [ ] 默认关闭：首装打开应用直接进看板，无任何验证弹窗；设置页开关为关
- [ ] 开启开关后杀进程重启：先弹系统身份验证，通过后才出现看板与底部导航
- [ ] 验证界面点「取消/返回」：停在「解锁未完成」页，只有「重试」，不进应用；点重试能再次弹窗
- [ ] 用指纹验证失败多次触发 `ERROR_LOCKOUT`：出现「无法验证，临时放行」，点「进入应用」可正常使用
- [ ] 无生物识别硬件的设备（或模拟器）：设置页看不到开关，并显示「本机没有指纹或面容识别硬件…」
- [ ] 有硬件未录入指纹：设置页看不到开关并提示先录入；录入后重进设置页开关出现
- [ ] 开启后到系统里删除全部指纹再冷启动：提示「系统里还没有录入指纹或面容…」的临时放行，不卡死
- [ ] 无锁屏密码的设备开启后验证失败：回退路径不存在时走临时放行而不是空转
- [ ] 验证通过后转屏：不再重复弹窗；退到后台再回前台：不重复弹窗（按票据语义只要求冷启动验一次）
