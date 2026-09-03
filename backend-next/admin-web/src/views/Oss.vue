<template>
  <div>
    <div class="toolbar">
      <el-select v-model="oss.dir" style="width: 200px" @change="oss.refresh">
        <el-option v-for="d in OSS_DIRS" :key="d.value" :value="d.value" :label="d.label" />
      </el-select>
      <!-- keyword 本地响应式过滤（同时匹配解密原名与 key），无需请求 -->
      <el-input
        v-model="oss.keyword"
        placeholder="按原名/文件名过滤"
        style="width: 220px"
        clearable
      />
      <el-upload :show-file-list="false" :auto-upload="false" :on-change="uploadChange">
        <el-button type="success" :loading="uploading">上传到当前目录</el-button>
      </el-upload>
      <!-- 视图偏好持久化在 localStorage，默认网格 -->
      <el-radio-group v-model="viewMode" class="view-toggle">
        <el-radio-button value="grid">
          <el-icon class="vt-icon"><Grid /></el-icon>网格
        </el-radio-button>
        <el-radio-button value="table">
          <el-icon class="vt-icon"><Tickets /></el-icon>表格
        </el-radio-button>
      </el-radio-group>
    </div>

    <OssGrid
      :oss="oss"
      mode="manage"
      :view="viewMode"
      @copy="(r) => copyUrl(objectUrl(r.key))"
      @remove="remove"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Grid, Tickets } from "@element-plus/icons-vue";
import type { UploadFile } from "element-plus";
import { api } from "../api";
import { objectUrl } from "../media";
import { OSS_DIRS, useOssList, type OssObject } from "../oss";
import OssGrid from "../components/OssGrid.vue";

const oss = useOssList("uploads");
const uploading = ref(false);

// 视图切换：网格（正方形统一网格）/ 表格，偏好持久化（ADR 0010）
const VIEW_KEY = "oss.viewMode";
const viewMode = ref<"grid" | "table">(
  localStorage.getItem(VIEW_KEY) === "table" ? "table" : "grid",
);
watch(viewMode, (v) => localStorage.setItem(VIEW_KEY, v));
void oss.refresh();

async function uploadChange(file: UploadFile): Promise<void> {
  const f = file.raw;
  if (!f) return;
  const fd = new FormData();
  fd.append("file", f);
  uploading.value = true;
  try {
    // dir 走 query（multipart 字段顺序不定，query 更可靠）；images 目录服务端会同步生成缩略图
    await api.post(`/admin/oss/upload?dir=${encodeURIComponent(oss.dir)}`, fd);
    ElMessage.success(`「${f.name}」已上传`);
    await oss.refresh();
  } finally {
    uploading.value = false;
  }
}

async function copyUrl(url: string): Promise<void> {
  await navigator.clipboard.writeText(url);
  ElMessage.success("已复制");
}

async function remove(row: OssObject): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除 ${row.key}？被歌曲/分类等业务数据引用的对象会被拒绝删除。`,
    "删除对象",
    { type: "warning" },
  );
  await api.delete("/admin/oss/objects", { params: { key: row.key } });
  ElMessage.success("已删除");
  await oss.refresh();
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.view-toggle {
  margin-left: auto;
}
.vt-icon {
  vertical-align: -2px;
  margin-right: 2px;
}
</style>
