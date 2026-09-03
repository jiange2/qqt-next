# 分类-专辑层级化：分类下放专辑而非歌曲

内容组织从扁平双维度（歌曲同时挂分类与专辑两个独立外键）重构为层级归属链 **歌曲 → 专辑 → 分类**：App 分类页点击后展示该分类下的专辑列表（`cat_albums`），再进专辑看歌。决定在 `albums` 表新增 `categoryId`（可空，单值——一个专辑只属于一个分类）与 `category_sort` 列，并彻底删除门面方法 `cat_songs`（App 随新版同步发版，不留旧版兼容）。动机：分类直接罗列成百上千首歌无法浏览，专辑才是自然的浏览与策展单元。

## 模型变更

- `Album.categoryId`: 可空 `Int?`。为空即「未分类专辑」：仅存在于后台，App 分类下不可见，**其内歌曲级联不可见**（可见性完全由 `album → category` 链推导）。
- `Album.categorySort`: 复制 ADR 0007 的「维度顺序」范式——展示按 `sort ASC, id DESC`；专辑新归入分类插入最前（`min(sort)-1`），移出归零；后台分类维度抽屉支持拖拽（多选整块移动）与「按名称排序」预览，走显式保存链路。
- 删除约束改写：分类下**仍有专辑**即阻止删除分类（原为"仍有歌曲"）。
- ADR 0007 的「歌须先有分类才能进专辑」约束**废弃**：歌曲自身分类不再影响任何 App 行为，批量改专辑/单首归专辑不再校验 `categoryId`。

## 过渡期：Song.categoryId 暂留（二期拆除）

`Song.categoryId` / `Song.category_sort` 字段及其后台 UI（批量修改分类、歌曲列表分类过滤、编辑表单分类字段）**本期保留**，但已无任何消费方（`cat_songs` 已删、分类内歌曲排序抽屉已下线）——纯后台组织标签，无行为语义。待层级结构上线验证无误后二期统一拆除字段与 UI。期间禁止新增依赖该字段的逻辑。

## 门面协议

- 新增 `cat_albums`（分页）：返回 `category_id` 下 `status=true` 的专辑，按 `categorySort ASC, id DESC`，行结构复用 `album_list`（`aid / album_name / album_image / album_image_thumb / total_records`），不附带专辑内歌曲数。
- 删除 `cat_songs` 方法；安卓端 `CategorySongsScreen` 由新的分类专辑网格页替代。
- 死配置 `apiCatOrderBy` / `apiCatPostOrderBy` 从 Settings 表单移除，settings 表列保留不动（避免波及 import/migrate 脚本）。

## 数据迁移

存量专辑 `categoryId` **全部置空**，不做自动推断；上线后在后台逐个编辑专辑分配分类（专辑数量少，人工分配最准确）。

## Considered Options

- 维持扁平双维度（分类直接挂歌 + 专辑独立）：App 分类页无法二级浏览，被否。
- 专辑多分类（多对多）：「分类内专辑顺序」失去落点（同一专辑在多个分类中无法共用一个 sort），被否。
- `Song.categoryId` 本期即删：牵涉批量操作/过滤/表单一并改动，风险叠加；保留过渡期字段、锁定"无消费方"边界，二期再拆，被采纳。
- 保留 `cat_songs` 兼容旧版 App：两端同步发版、无旧版用户负担，死代码徒增误导，被否。

## Consequences

- 歌曲的 App 可见性条件变为：`status=true` 且所属专辑 `status=true` 且专辑已归入 `status=true` 的分类。未归专辑歌曲、未分类专辑内歌曲对 App 均不可见。
- 分类页、专辑页的排序来源统一为维度顺序范式；`category_sort`（专辑）与 `album_sort`（歌曲）语义对称。
- 二期拆除 `Song.categoryId` 时需再出一次 schema 迁移与后台 UI 清理，见上节边界。
