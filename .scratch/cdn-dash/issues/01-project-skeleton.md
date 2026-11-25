# 01: 独立工程骨架与三页导航

**What to build:** 在 `cdn-dash/` 下立起一个与音乐客户端互不依赖的独立 Android 工程（Kotlin + Jetpack Compose Material 3 + Navigation Compose），应用可启动、可导航。启动进入看板页；没有任何账号时显示「去设置添加账号」引导空态（不是空白页）；花费页与设置页可达但内容为空壳。包名 `com.cdnndash.app`，`minSdk 26`、`targetSdk 35`，主题跟随系统深色模式。工程依赖一次配齐：Compose BOM、Navigation Compose、DataStore、OkHttp、kotlinx.serialization、JUnit4 + MockWebServer（test 源集），后续票据不再反复改构建脚本。Gradle wrapper 复用仓内 `android/` 工程的产物，不重新下载。

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `cdn-dash/` 是独立 Gradle 工程：`settings.gradle.kts` / 根构建脚本 / `app` 模块，不引用 `<root>/android` 或 `backend-next` 任何代码
- [ ] 依赖清单与 spec「工程与依赖」一致：Compose M3、Navigation Compose、kotlinx.serialization、OkHttp、DataStore；测试源集含 JUnit4 与 MockWebServer
- [ ] 应用启动直接进入看板页；底栏或等价入口可切到花费页、设置页，返回键行为正确
- [ ] 无账号时看板显示引导空态，文案指向设置页，点击可直接跳转
- [ ] 三页均为可识别的空壳，不出现占位崩溃；`Asia/Shanghai` 时区常量与字节/金额格式化工具已有落点（可为空实现）
- [ ] 包名、`minSdk`、`targetSdk`、版本号按 spec 默认值写入
- [ ] 本机无 Android SDK，未做编译验证；构建可在装有 SDK 的机器上直接 `./gradlew assembleDebug`
