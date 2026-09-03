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
              :src="previewSrc(row.key)"
              :preview-src-list="[objectUrl(row.key)]"
              preview-teleported
              fit="cover"
              loading="lazy"
              @error="markBroken(row.key)"
            />
            <img
              v-else
              class="pic"
              :src="previewSrc(row.key)"
              loading="lazy"
              alt=""
              @click="emit('select', baseName(row.key))"
              @error="markBroken(row.key)"
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
      </div>
    </div>
    <!-- 表格视图（管理页） -->
    <el-table
      v-else
      v-loading="oss.loading && oss.items.length === 0"
      :data="oss.items"
      size="small"
    >
      <el-table-column label="预览" width="80">
        <template #default="{ row }">
          <el-image
            v-if="isImage(row.key)"
            class="thumb"
            :src="previewSrc(row.key)"
            :preview-src-list="[objectUrl(row.key)]"
            preview-teleported
            fit="cover"
            loading="lazy"
            @error="markBroken(row.key)"
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
import { computed, reactive, ref } from "vue";
import { Document, ZoomIn } from "@element-plus/icons-vue";
import { objectUrl } from "../media";
import { fmtSize, fmtTime, type OssObject, type useOssList } from "../oss";
import AppPagination from "./AppPagination.vue";

const props = defineProps<{
  oss: ReturnType<typeof useOssList>;
  /** manage=管理页（hover 操作 + 点图放大）；pick=选择器（点卡选中） */
  mode?: "manage" | "pick";
  /** 网格=正方形统一网格；表格=管理页专属（选择器强制网格） */
  view?: "grid" | "table";
}>();
const emit = defineEmits<{
  (e: "select", name: string): void;
  (e: "copy", row: OssObject): void;
  (e: "remove", row: OssObject): void;
}>();

const isPick = props.mode === "pick";
const isTable = computed(() => props.view === "table" && !isPick);
const viewer = ref("");
// 缩略图加载失败的对象（无同名 thumbs），回退显示原图
const broken = reactive(new Set<string>());

function isImage(key: string): boolean {
  return key.startsWith("images/");
}

function baseName(key: string): string {
  return key.split("/").pop() ?? key;
}

/** 网格预览用 thumbs 同名缩略图（thumbs 目录自身直接用当前 key），失败回退原图 */
function previewSrc(key: string): string {
  if (broken.has(key)) return objectUrl(key);
  return key.startsWith("images/thumbs/") ? objectUrl(key) : objectUrl(key.replace(/^images\//, "images/thumbs/"));
}

function markBroken(key: string): void {
  broken.add(key);
}

function tip(row: OssObject): string {
  const name = row.originalName ? `${row.originalName}\n` : "";
  return `${name}${row.key}\n${fmtSize(row.size)} · ${fmtTime(row.lastModified)}`;
}
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
