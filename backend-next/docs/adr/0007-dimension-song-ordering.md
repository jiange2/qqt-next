---
status: partially superseded by ADR 0009（维度顺序范式仍有效，并扩展至专辑在分类内排序；「未分类歌曲」约束「歌须先有分类才能进专辑」已被 0009 废弃；`cat_songs` 已删除，分类内歌曲排序无消费方）
---

# 分类/专辑维度歌曲排序与未分类歌曲

为满足后台按分类/专辑整理歌曲（拖拽排序、移除、认领）的需求，引入两处决策：**维度顺序**与**未分类歌曲**（backend-next ADR 0007）。

## 维度顺序

歌曲同时属于一个分类（可空）与零或一个专辑，两个维度的顺序彼此独立，单一 sort 列无法表达。决定在 `songs` 表加两列 `category_sort` / `album_sort`（默认 0，展示按 `sort ASC, id DESC` 兜底），而非新建 `category_songs`/`album_songs` 关联表——分类/专辑与歌曲本就是外键直连，重构为关联表代价大且无收益。

这是**遗留协议门面的行为变更**：`cat_songs` 原按全局设置 `apiCatPostOrderBy` 排 id，`album_songs` 原按歌名排；现均改为维度顺序优先。响应字段结构不变，仅顺序变，App 端（含老版）无需改动即接受新顺序。设置项 `apiCatOrderBy` / `apiCatPostOrderBy` 保留在 settings 表中但对这两个接口失效。

**顺序方向（用户修订）**：新歌应展示在最前。实现上保持 `sort ASC` 存储，新加入（创建、换归属、认领）写入 `min(sort)-1` 插入最前；id 兜底取 **DESC**——未手动排序过的存量歌按加入时间倒序展示，与旧系统默认的“最新在前”一致（修订前为追加末尾 max+1 + id ASC）。

## 未分类歌曲

「从分类移除」要求数据上存在无分类状态，故 `songs.category_id` 改为可空。未分类歌曲对 App 完全不可见——所有内容接口经 `songStatusFilter`（含 `category: { status: true }` 关系过滤）天然排除空分类，无需在门面各 handler 单独处理；后台通过分类抽屉的「添加歌曲」弹窗（只列孤儿）认领。

配套规则：**歌须先有分类才能进专辑**（后台服务端校验 + 前端拦截），否则一首未分类歌挂在专辑里会在 App 专辑页被静默隐藏，造成"后台有、App 无"的困惑。分类维度的 FK 删除行为保持 RESTRICT：删除仍有歌曲的分类依旧被阻止（Prisma 因关系变可选自动生成 SET NULL，迁移 SQL 中已纠正）。

## Considered Options

- 单一 `sort` 列共用两维度：改分类顺序会连带改专辑顺序，被否。
- 引入"未分类"为真实 Category 行：会出现在 App 分类列表，且伪装归属语义扭曲，被否。
- 门面排序分两步（先后台、后 App）：需两次改门面，且顺序变化对 App 无结构风险，一次到位。

## Consequences

- 存量歌曲 sort 均为 0，首次排序前 App 分类/专辑页顺序为 `id DESC`（与旧系统默认的 desc 分支一致）；手动排序后按管理员固定顺序展示。
- 插入最前采用 min−1 递减，sort 值可持续为负（INT 范围内无实际风险）。
- `apiCatOrderBy` / `apiCatPostOrderBy` 成为死配置，仅存档。
