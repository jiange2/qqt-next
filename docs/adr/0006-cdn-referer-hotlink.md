# CDN 媒体防刷采用 Referer 防盗链，App 以包名 URL 作为约定 Referer

防刷 CDN 流量（防脚本批量下载 mp3 消耗流量费）的需求下，决定**仅使用 Referer 防盗链**作为唯一读防护：否决 URL 签名鉴权（配置与运维麻烦，防盗链已够用）与带宽封顶（暂不做）。App 原生请求（ExoPlayer、Coil）默认不带 Referer，故客户端对全部媒体请求统一注入约定值 `http://com.qqt.music/`（包名 `com.qqt.music` 作 host）——不使用裸包名字符串，因为阿里云 CDN 把 Referer 当 URL 解析后提取域名做后缀匹配，裸字符串的解析行为未定义，存在 App 全体 403 的风险。防盗链配置在 CDN 层（`cdn.qqt.yunshangzhiai7.top`）并**分路径**：`uploads/`（音频，流量大头）禁止空 Referer + 白名单 `com.qqt.music`；`images/`、`images/thumbs/`、`lrc/` 允许空 Referer——管理面板经 `http://<IP>:8002` 访问，浏览器 Referer 为 IP:端口形态，进不了仅支持域名的白名单，且图片/歌词流量占比可忽略。OSS 桶（`qqt7`，公共读）保持不动，桶本身对匿名 GET 无任何认证，CDN 层规则是唯一读防护。

## Considered Options

- **URL 签名鉴权（CDN A/B/C 鉴权或 OSS 签名 URL）**：被拒——用户权衡后认为配置麻烦，Referer 防盗链足够。
- **带宽封顶/流量告警**：被拒——暂不做。
- **面板绑域名 + 全局禁止空 Referer**：被拒——多一步运维，图片流量不值得。
- **裸包名 `Referer: com.qqt.music`**：被拒——非合法 URL，CDN 域名提取行为未验证，风险不可接受。
- **包名 URL + 分路径规则（采纳）**：保住音频这一防刷核心，零新增运维，面板不受影响。

## Consequences

- Referer 值写死在客户端两处（ExoPlayer 数据源 + Coil 拦截器），**换值 = App 发版**；上线前必须真机验证 CDN 白名单匹配通过。
- 防护边界：防浏览器盗链与不带 Referer 的裸脚本直刷；**伪造 Referer 的脚本可绕过**（HTTP 明文下 Referer 本就是明文可见、可仿冒的头）。若将来刷流量损失扩大，升级路径是 CDN URL 鉴权。
- 绕过 CDN 直打 OSS 默认域名（`qqt7.oss-cn-shanghai.aliyuncs.com`）仍可匿名拉流——公共读桶不设防，接受此残留面。
- 被防盗链拦截的 403 响应仍产生少量 CDN 流量费（请求头 + 403 页面，可忽略）。
- 将来管理面板若绑定域名，可把 `images/`、`lrc/` 收紧为禁止空 Referer。
