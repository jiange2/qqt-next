<template>
  <el-drawer v-model="open" :title="drawerTitle" size="560px" @closed="destroySortable">
    <!-- 抽屉级 loading：盖住整个内容区；空表时表格自身的遮罩太小，视觉上等于没有加载反馈 -->
    <div v-loading="loading" class="drawer-body">
    <div class="toolbar">
      <span class="count">共 {{ items.length }} {{ unit }}</span>
      <el-button v-if="!isBooks && !isChapters" type="primary" plain @click="openClaim">添加{{ entity }}</el-button>
      <el-button @click="sortByName">按名称排序</el-button>
      <el-button @click="sortByNameDesc">按名称倒序</el-button>
      <el-button type="success" :disabled="!dirty" :loading="saving" @click="saveOrder">保存顺序</el-button>
    </div>
    <el-alert
      v-if="dirty"
      title="顺序已调整，点击「保存顺序」后生效"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      title="拖动把手排序，勾选多项后拖动可整块移动；或点「按名称排序」/「按名称倒序」一键按名称自然升降序重排"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-table
      ref="tableRef"
      :data="items"
      row-key="id"
      size="small"
      @selection-change="(rows: Row[]) => (selected = rows)"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column width="40" align="center">
        <template #default>
          <el-icon class="drag-handle"><Rank /></el-icon>
        </template>
      </el-table-column>
      <el-table-column type="index" label="#" width="50" />
      <el-table-column v-if="!isChapters" label="图片" width="64">
        <template #default="{ row }">
          <el-image v-if="row.image" :src="deobfSrc(thumbUrl(row.image))" style="width: 40px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="isCategory || isBooks" :label="isBooks ? '章节数' : '歌曲数'" width="70">
        <template #default="{ row }">{{ row.songCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column v-if="!isChapters" label="状态" width="70">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'" size="small">{{ row.status ? "上架" : "下架" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!isBooks && !isChapters" label="操作" width="70">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-dialog v-model="claimDialog" :title="claimTitle" width="520px" append-to-body>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索名称"
          clearable
          style="width: 220px"
          @keyup.enter="searchAvail"
        />
        <el-button type="primary" @click="searchAvail">搜索</el-button>
      </div>
      <el-table
        :data="availItems"
        v-loading="availLoading"
        row-key="id"
        size="small"
        max-height="360"
        @selection-change="(rows: Row[]) => (availSelected = rows)"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      </el-table>
      <div class="pager">
        <el-button size="small" :disabled="availPage <= 1" @click="availPage--; loadAvail()">上一页</el-button>
        <span class="pager-info">共 {{ availTotal }} {{ unit }}（{{ claimHint }}）</span>
        <el-button size="small" :disabled="availPage * PAGE_SIZE >= availTotal" @click="availPage++; loadAvail()">下一页</el-button>
      </div>
      <template #footer>
        <el-button @click="claimDialog = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="availSelected.length === 0"
          :loading="claiming"
          @click="claim"
        >添加选中（{{ availSelected.length }}）</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
// 维度顺序抽屉（backend-next ADR 0007 / 0009 / 0011）：
// - kind="albums"：专辑内歌曲排序（移除 = 置空专辑归属；添加 = 认领无专辑且有分类的歌曲）
// - kind="categories"：分类内专辑排序（ADR 0009 层级化；移除 = 移出分类；添加 = 认领未分类专辑）
// - kind="books"：分类内书籍排序（ADR 0011；书籍必有分类，仅排序——无移除/认领）
// - kind="chapters"：书内章节顺序（ADR 0011 修订；章节顺序非维度顺序，仅复用交互——无移除/认领/图片/状态列）
// 列表按维度顺序展示；拖拽或「按名称排序」/「按名称倒序」调整（显式保存）；新加入插入最前（章节为追加书末）
import { computed, nextTick, ref, watch } from "vue";
import { Rank } from "@element-plus/icons-vue";
import Sortable from "sortablejs";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";
import { thumbUrl } from "../media";
import { deobfSrc } from "../deobf";

type Row = { id: number; name: string; image: string; status: boolean; songCount?: number };

const props = defineProps<{
  kind: "categories" | "albums" | "books" | "chapters";
  parent: { id: number; name: string };
}>();
const open = defineModel<boolean>({ required: true });

const PAGE_SIZE = 50;
const isCategory = computed(() => props.kind === "categories");
const isBooks = computed(() => props.kind === "books");
const isChapters = computed(() => props.kind === "chapters");
const entity = computed(() => (isCategory.value ? "专辑" : isBooks.value ? "书籍" : isChapters.value ? "章节" : "歌曲"));
const unit = computed(() => (isCategory.value ? "张" : isBooks.value ? "本" : isChapters.value ? "章" : "首"));
const base = computed(() =>
  isCategory.value
    ? `/admin/categories/${props.parent.id}/albums`
    : isBooks.value
      ? `/admin/categories/${props.parent.id}/books`
      : isChapters.value
        ? `/admin/books/${props.parent.id}/chapters`
        : `/admin/${props.kind}/${props.parent.id}/songs`,
);
const drawerTitle = computed(() => `${props.parent.name} — ${entity.value}`);
const claimTitle = computed(
  () => `添加${entity.value}到${isCategory.value || isBooks.value ? "分类" : "专辑"}「${props.parent.name}」`,
);
const claimHint = computed(() =>
  isCategory.value || isBooks.value ? "仅列未归属条目" : "仅列无专辑且有分类的歌曲",
);

// 服务端行结构四种形态：歌曲（title/thumbnail）、专辑（name/image/_count.songs）、
// 书籍（name/image/_count.chapters，image 已由服务端映射自 cover）、章节（title/contentLength），统一为展示行
function normalize(it: Record<string, unknown>): Row {
  if (isChapters.value) {
    // 章节行：id/标题/字数，无图片与状态（抽屉列已隐藏，占位默认值）
    return { id: it.id as number, name: it.title as string, image: "", status: true };
  }
  if (isCategory.value || isBooks.value) {
    const cnt = (it as { _count?: { songs?: number; chapters?: number } })._count;
    return {
      id: it.id as number,
      name: it.name as string,
      image: it.image as string,
      status: it.status as boolean,
      songCount: cnt?.songs ?? cnt?.chapters ?? 0,
    };
  }
  return {
    id: it.id as number,
    name: it.title as string,
    image: it.thumbnail as string,
    status: it.status as boolean,
  };
}

const items = ref<Row[]>([]);
const loading = ref(false);
const dirty = ref(false);
const saving = ref(false);

async function load() {
  loading.value = true;
  try {
    const { data } = await api.get(base.value);
    items.value = data.items.map(normalize);
    dirty.value = false;
  } catch {
    ElMessage.error("加载失败，请重试");
  } finally {
    loading.value = false;
  }
}

// ---- 拖拽排序：勾选多项后拖动可整块移动；拖动即调整本地顺序，显式「保存顺序」批量提交
const tableRef = ref<{ $el: HTMLElement } | null>(null);
const selected = ref<Row[]>([]);
let sortable: Sortable | null = null;

function destroySortable(): void {
  sortable?.destroy();
  sortable = null;
}

function initSortable(): void {
  destroySortable();
  const tbody = tableRef.value?.$el?.querySelector<HTMLElement>(".el-table__body-wrapper tbody");
  if (!tbody) return;
  sortable = Sortable.create(tbody, {
    handle: ".drag-handle",
    animation: 150,
    onEnd: ({ oldIndex, newIndex }) => {
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return;
      const arr = [...items.value];
      const draggedId = arr[oldIndex]?.id;
      if (draggedId == null) return;
      // 待移动集合：拖中的行在勾选集合内则整块移动（保持块内相对顺序），否则单个移动
      const movingSet = selected.value.some((s) => s.id === draggedId)
        ? new Set(arr.filter((s) => selected.value.some((sel) => sel.id === s.id)).map((s) => s.id))
        : new Set([draggedId]);
      const remaining = arr.filter((s) => !movingSet.has(s.id));
      // 落点 = 移动后 DOM 中新位置之前的非选中行数（被拖行除外）
      const before = arr.filter((_, i) => i !== oldIndex).slice(0, newIndex);
      const insertAt = before.filter((s) => !movingSet.has(s.id)).length;
      const block = arr.filter((s) => movingSet.has(s.id));
      remaining.splice(insertAt, 0, ...block);
      items.value = remaining;
      dirty.value = true;
    },
  });
}

watch(items, () => nextTick(initSortable));
// immediate：Albums/Categories 打开抽屉时 drawerParent 与 open 同 tick 置位，
// 组件挂载瞬间 open 已是 true，不带 immediate 的 watch 永远不会触发首次加载（首开空白无请求）
watch(
  open,
  (v) => {
    if (v) void load();
    else destroySortable();
  },
  { immediate: true },
);

// ---- 按名称排序：一键按名称自然升/降序本地重排（预览），与拖拽共用显式「保存顺序」链路
function sortByName() {
  items.value = sortItems((a, b) =>
    a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: "base" }),
  );
  dirty.value = true;
}

function sortByNameDesc() {
  items.value = sortItems((a, b) =>
    b.name.localeCompare(a.name, undefined, { numeric: true, sensitivity: "base" }),
  );
  dirty.value = true;
}

function sortItems(cmp: (a: Row, b: Row) => number): Row[] {
  return [...items.value].sort(cmp);
}

async function saveOrder() {
  saving.value = true;
  try {
    await api.put(`${base.value}/order`, { ids: items.value.map((s) => s.id) });
    dirty.value = false;
    ElMessage.success("顺序已保存");
  } finally {
    saving.value = false;
  }
}

// ---- 移除：置空归属（分类维度 = 移出分类；专辑维度 = 移出专辑），条目本身不删除
async function remove(row: Row) {
  const hint = isCategory.value
    ? "移出后专辑将变为未分类（App 不可见），可通过其他分类的「添加专辑」认领。"
    : "移出后歌曲不再属于该专辑，歌曲本身保留。";
  await ElMessageBox.confirm(`确认把「${row.name}」移出？${hint}`, "移除", { type: "warning" });
  await api.delete(`${base.value}/${row.id}`);
  await load();
}

// ---- 认领：候选 = 孤儿条目（分类维度：未分类专辑；专辑维度：无专辑且有分类的歌曲）
const claimDialog = ref(false);
const claiming = ref(false);
const keyword = ref("");
const availItems = ref<Row[]>([]);
const availTotal = ref(0);
const availPage = ref(1);
const availLoading = ref(false);
const availSelected = ref<Row[]>([]);

function openClaim() {
  keyword.value = "";
  availPage.value = 1;
  availSelected.value = [];
  claimDialog.value = true;
  void loadAvail();
}

function searchAvail() {
  availPage.value = 1;
  void loadAvail();
}

async function loadAvail() {
  availLoading.value = true;
  try {
    const { data } = await api.get(`${base.value}/available`, {
      params: { keyword: keyword.value || undefined, page: availPage.value, size: PAGE_SIZE },
    });
    availItems.value = data.items.map(normalize);
    availTotal.value = data.total;
  } catch {
    ElMessage.error("加载失败，请重试");
  } finally {
    availLoading.value = false;
  }
}

async function claim() {
  claiming.value = true;
  try {
    await api.post(base.value, { ids: availSelected.value.map((s) => s.id) });
    claimDialog.value = false;
    ElMessage.success(`已添加 ${availSelected.value.length} ${unit.value}（插入最前）`);
    await load();
  } finally {
    claiming.value = false;
  }
}
</script>

<style scoped>
.drawer-body { min-height: 300px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.count { color: #909399; font-size: 13px; margin-right: auto; }
.drag-handle { cursor: grab; color: #909399; }
.pager { display: flex; gap: 12px; align-items: center; justify-content: center; margin-top: 12px; }
.pager-info { color: #909399; font-size: 12px; }
</style>
