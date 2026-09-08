<template>
  <div>
    <!-- 网格视图：正方形统一网格（ADR 0010）；表格视图仅在管理页提供 -->
    <div v-if="!isTable" v-loading="oss.loading && oss.items.length === 0" class="grid">
      <div v-for="row in oss.items" :key="row.key" class="card">
        <div class="media" :class="{ pick: isPick }">
          <template v-if="isImage(row.key)">
            <!-- 管理页：点图放大原图；选择器：点卡即选中，角标放大 -->
            <el-image
              v-if="!isPick"
              class="pic"
              :src="objectUrl(row.key)"
              :preview-src-list="[objectUrl(row.key)]"
              preview-teleported
              fit="cover"
              loading="lazy"
            />
            <img
              v-else
              class="pic"
              :src="objectUrl(row.key)"
              loading="lazy"
              alt=""
              @click="emit('select', baseName(row.key))"
            />
            <el-icon
              v-if="isPick"
              class="zoom"
              :size="18"
              title="查看原图"
              @click.stop="viewer = objectUrl(row.key)"
            >
              <ZoomIn />
            </el-icon>
          </template>
          <div v-else class="doc">
            <el-icon :size="40" color="#909399"><Document /></el-icon>
          </div>
          <!-- 管理页勾选角标：批量缓存头操作的选择集（Oss.vue 持有，全选=过滤结果跨页） -->
          <el-checkbox
            v-if="!isPick"
            class="pick-check"
            :model-value="hasSel(row.key)"
            @change="(v: CheckboxValueType) => toggleRow(row.key, v)"
            @click.stop
          />
          <!-- 管理页 hover 浮现操作按钮 -->
          <div v-if="!isPick" class="ops">
            <el-button size="small" @click.stop="emit('copy', row)">复制 URL</el-button>
            <el-button size="small" type="danger" @click.stop="emit('remove', row)">删除</el-button>
          </div>
        </div>
        <el-tooltip :content="tip(row)" placement="top">
          <!-- 优先显示解密原名（ADR 0008），存量明文 key / 解密失败回退 key 尾段 -->
          <div class="name">{{ row.originalName ?? baseName(row.key) }}</div>
        </el-tooltip>
        <!-- 缓存头会话内状态（CONTEXT「缓存头」）：未查可点查看，不查不付 head 代价 -->
        <div v-if="!isPick" class="cache-line">
          <span v-if="row.cacheFailed" class="cache-fail">查询失败</span>
          <el-link
            v-else-if="row.cacheControl === undefined"
            type="primary"
            @click="emit('query', row)"
          >查看缓存头</el-link>
          <span v-else-if="row.cacheControl === null" class="cache-unset">未设置</span>
          <span v-else>{{ row.cacheControl }}</span>
        </div>
      </div>
    </div>
    <!-- 表格视图（管理页） -->
    <el-table
      v-else
      v-loading="oss.loading && oss.items.length === 0"
      :data="oss.items"
      size="small"
    >
      <el-table-column v-if="!isPick" width="44" align="center">
        <template #header>
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="someSelected && !allSelected"
            @change="toggleAllRows"
          />
        </template>
        <template #default="{ row }">
          <el-checkbox
            :model-value="hasSel(row.key)"
            @change="(v: CheckboxValueType) => toggleRow(row.key, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="预览" width="80">
        <template #default="{ row }">
          <el-image
            v-if="isImage(row.key)"
            class="thumb"
            :src="objectUrl(row.key)"
            :preview-src-list="[objectUrl(row.key)]"
            preview-teleported
            fit="cover"
            loading="lazy"
          />
          <el-icon v-else :size="24" color="#909399"><Document /></el-icon>
        </template>
      </el-table-column>
      <!-- 优先显示解密原名（ADR 0008），存量明文 key / 解密失败回退 key 尾段 -->
      <el-table-column label="原名" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.originalName ?? baseName(row.key) }}</template>
      </el-table-column>
      <el-table-column label="Key" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.key }}</template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ fmtSize(row.size) }}</template>
      </el-table-column>
      <el-table-column label="修改时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.lastModified) }}</template>
      </el-table-column>
      <el-table-column v-if="!isPick" label="缓存头" width="150">
        <template #default="{ row }">
          <span v-if="row.cacheFailed" class="cache-fail">查询失败</span>
          <el-link v-else-if="row.cacheControl === undefined" type="primary" @click="emit('query', row)">查看</el-link>
          <span v-else-if="row.cacheControl === null" class="cache-unset">未设置</span>
          <span v-else>{{ row.cacheControl }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="emit('copy', row)">复制 URL</el-button>
          <el-button size="small" type="danger" @click="emit('remove', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!oss.loading && oss.items.length === 0"
      :description="oss.keyword ? '无匹配对象' : '暂无对象'"
    />
    <el-alert
      v-if="oss.truncated"
      type="warning"
      :closable="false"
      title="对象数超出上限，列表已截断"
      style="margin-top: 12px"
    />
    <AppPagination v-model:page="oss.page" v-model:size="oss.pageSize" :total="oss.total" />
    <el-image-viewer v-if="viewer" :url-list="[viewer]" @close="viewer = ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { Document, ZoomIn } from "@element-plus/icons-vue";
import type { CheckboxValueType } from "element-plus";
import { objectUrl } from "../media";
import { fmtSize, fmtTime, type OssObject, type useOssList } from "../oss";
import AppPagination from "./AppPagination.vue";

const props = defineProps<{
  oss: ReturnType<typeof useOssList>;
  /** manage=管理页（hover 操作 + 点图放大）；pick=选择器（点卡选中） */
  mode?: "manage" | "pick";
  /** 网格=正方形统一网格；表格=管理页专属（选择器强制网格） */
  view?: "grid" | "table";
  /** 勾选集（manage 专属，Oss.vue 持有）：批量缓存头操作的选择来源 */
  selected?: Set<string>;
}>();
const emit = defineEmits<{
  (e: "select", name: string): void;
  (e: "copy", row: OssObject): void;
  (e: "remove", row: OssObject): void;
  (e: "query", row: OssObject): void;
  (e: "toggle", key: string, checked: boolean): void;
  (e: "toggleAll", checked: boolean): void;
}>();

const isPick = props.mode === "pick";
const isTable = computed(() => props.view === "table" && !isPick);
const viewer = ref("");

function isImage(key: string): boolean {
  return key.startsWith("images/");
}

function baseName(key: string): string {
  return key.split("/").pop() ?? key;
}

// 管理页与选择器预览一律直显原图（ADR 0011 修订：OSS 页不展示缩略图）

function tip(row: OssObject): string {
  const name = row.originalName ? `${row.originalName}\n` : "";
  return `${name}${row.key}\n${fmtSize(row.size)} · ${fmtTime(row.lastModified)}`;
}

// ---- 勾选辅助（选择集在 Oss.vue；全选=当前过滤结果跨页，不止当前页）----
function hasSel(key: string): boolean {
  return props.selected?.has(key) ?? false;
}
function toggleRow(key: string, v: CheckboxValueType): void {
  emit("toggle", key, v === true);
}
function toggleAllRows(v: CheckboxValueType): void {
  emit("toggleAll", v === true);
}
const filteredKeys = computed(() => (props.oss.filtered ?? []).map((o) => o.key));
const allSelected = computed(
  () => filteredKeys.value.length > 0 && filteredKeys.value.every((k) => hasSel(k)),
);
const someSelected = computed(() => filteredKeys.value.some((k) => hasSel(k)));
</script>

<style scoped>
/* 正方形统一网格（ADR 0010）：等宽等高整齐优先于保比例；列数随容器宽度自适应 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}
.card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  background: var(--el-bg-color);
}
.media {
  position: relative;
  background: var(--el-fill-color-lighter);
}
.media .pic {
  display: block;
  width: 100%;
  aspect-ratio: 1 / 1;
}
.media.pick .pic {
  object-fit: cover;
  cursor: pointer;
}
/* 选择器：右上角悬浮放大入口 */
.media .zoom {
  position: absolute;
  top: 6px;
  right: 6px;
  padding: 4px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  color: var(--el-color-primary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}
.media:hover .zoom {
  opacity: 1;
}
/* 非图片文件的图标占位（对齐正方形网格） */
.doc {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1 / 1;
}
/* 表格视图小缩略图 */
.thumb {
  display: block;
  width: 48px;
  height: 48px;
}
/* 管理页网格勾选角标 */
.pick-check {
  position: absolute;
  top: 6px;
  left: 6px;
  z-index: 2;
  margin-right: 0;
  padding: 2px 5px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.85);
}
.table-check {
  margin-right: 0;
}
/* 缓存头状态行（网格卡名下） */
.cache-line {
  padding: 0 8px 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cache-unset {
  color: var(--el-color-warning);
}
.cache-fail {
  color: var(--el-color-danger);
}
/* hover 操作浮层 */
.ops {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.15s;
}
.media:hover .ops {
  opacity: 1;
}
.name {
  padding: 6px 8px;
  font-size: 12px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
