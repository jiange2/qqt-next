# OSS 网格预览：正方形统一网格取代 CSS columns 瀑布流

OSS 管理页（Oss.vue）与 OssPicker 的图片预览从「CSS columns 真瀑布流、保留缩略图原始比例」改为**正方形统一网格**：`aspect-ratio: 1/1` + `object-fit: cover` 裁切，卡片等宽等高，列数随容器宽度自适应（`repeat(auto-fill, minmax(160px, 1fr))`）。原瀑布流方案（ADR 0004 期间引入，`columns: 5 170px`）在宽屏下因列数封顶导致单图被拉到 ~280px，且比例参差视觉不齐，用户反馈"图片太大、不整齐"。管理页同时新增「网格 / 表格」视图切换，偏好存 localStorage（`oss.viewMode`），默认网格；表格视图仅管理页提供（选择器职责是快速挑图，保持纯网格）。

## 决策要点

- **推翻"保原始比例"前提**：浏览/挑选场景下比例信息价值低，整齐性优先；点开原图（el-image preview / ZoomIn 角标）仍可看完整比例。
- **裁切纯前端**：thumbs 缩略图（300px 长边等比、`fit: inside`）保持不变，方形效果由 CSS 裁切实现，不动后端与存储；卡片目标尺寸 ~160px（thumbs 源图长边 300px 下的清晰度/流量平衡点）。
- **列数封顶取消**：`columns: 5 170px` 的 5 列上限是"图太大"的直接原因；改 auto-fill 后宽屏自动增加列数。
- **非图片文件对齐**：uploads/lrc 等文档图标占位同样正方形，保持整页网格整齐。
- **瀑布流→网格后 columns 机制废弃**：方形等高下 CSS columns 已无意义，改为 CSS grid；"列纵向填充顺序"这一既有取舍随之消失。
- **表格视图**：列=方形小缩略图(48px)/原名/key/大小/修改时间/操作，行为（分页、keyword 过滤、目录切换、删除确认、broken thumbs 回退）与网格完全一致，复用 `useOssList`，不新增接口。

## Considered Options

- 保留瀑布流、仅缩小图（去掉列数封顶）：解决了"太大"，但比例参差导致的"不整齐"仍在，被否。
- 重生成方形 thumbs（服务端 `resize(300,300,cover)`）：影响所有 thumbs 消费方、需回填存量文件，收益不成比例，被否。
- JS masonry 库：与既有决策一致被否（无依赖偏好），且方形网格下根本不需要。
- 仅管理页加表格切换、选择器不加：选择器场景视觉挑选效率优先，被采纳。

## Consequences

- OssGrid.vue 的布局实现从 CSS columns 变为 CSS grid；"瀑布流"一词在 admin-web 语境下不再使用，改称"网格视图"。
- 管理页工具栏多出视图切换控件，偏好为纯客户端 localStorage，不含账号级同步。
- 若未来要求恢复比例保真，仅改 CSS（aspect-ratio/object-fit/删 cover），thumbs 数据无迁移负担。
