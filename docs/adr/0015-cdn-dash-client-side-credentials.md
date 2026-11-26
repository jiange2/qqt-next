# CDN 看板走端上直连 OpenAPI：每账号一条只读 AK，存 Android Keystore，不建后端

运营要在一个 Android 应用里看多个阿里云账号的 CDN 流量、资源包余量和账号花费。使用者的明确要求是「不要后端，直接在安上填」，因此凭据必然落在手机上——这与阿里云官方「不要把 AK 内置客户端」的建议正面冲突，必须显式记录，否则半年后会有人把它当成疏忽去「修」。

## 决策

1. **无自建后端**：App 直接对 `https://cdn.aliyuncs.com/` 与 `business.aliyuncs.com` 签名调用，RPC v1（HMAC-SHA1）在端上计算，沿用 `backend-next/server/src/services/cdn-traffic.ts` 已验证的签名实现。
2. **凭据粒度**：每个账号一条 RAM 用户的 AccessKey，权限只到 `cdn:DescribeDomainTrafficData`、`cdn:DescribeCdnUserResourcePackage`、`bss:QueryAccountBill`（`AliyunBSSReadOnlyAccess` 亦可）。不做跨账号 AssumeRole——链式角色仍要求主凭据在端上，风险只增不减。
3. **落盘**：AK/SK 用 Android Keystore 中不可导出的 AES-GCM 密钥加密，密文（base64）与其他账号元数据一并存 DataStore；不写日志、不进备份、不导出。删除账号即删密文。不用 `androidx.security:security-crypto`——该库已进入维护状态，自己用 `javax.crypto` 加一层反而是长期更稳的写法。
4. **不做 STS**：签发 STS 需要一方持有主凭据并受信，本方案没有这一方；引入它就等于把被否掉的后端换个名字加回来。
5. **失败即局部降级**：鉴权失败只把该账号标为「凭证失效」，不 fail-fast 整个应用（对比 ADR 0014 对服务端配置采用的「缺配置即禁用」）。

## Considered Options

- **自建后端中转 / 复用 backend-next**：被否——使用者明确要去掉额外进程；且会引入部署、鉴权、域名与费用的维护面。
- **App 内填 AK 但提交给后端保存**：被否——同上，仍是后端。
- **每账号 AssumeRole 换临时 STS**：被否——主凭据仍在端上，且多一层角色配置，收益为零。
- **使用阿里云官方 SDK**：被否——只为四个只读接口引入 openapi-client 依赖链，包体与升级成本高于收益。

## Consequences

- **泄露面 = 手机**：设备丢失或 root 后可能被提取密钥，唯一止损手段是去控制台轮换 AK；App 内不提供「远程吊销」。
- **官方不建议**：这是使用者的知情选择，风险随使用范围扩大而扩大；本应用不应被分发给不受信的人。
- **无服务端缓存与限流保护**：多账号 × 多接口的调用全部由客户端承担，必须自带本地缓存与按需加载（见花费页按需拉取），否则冷启动会连发数十个请求。
- **若将来加后端**：本 ADR 被取代，凭据模型与 API 层随之重写。
