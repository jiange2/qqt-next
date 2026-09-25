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
      <!-- 缩略图目录仅浏览：派生物不设上传入口（服务端上传白名单同样拒绝 thumbs） -->
      <el-upload
        v-if="oss.dir !== 'images/thumbs'"
        :show-file-list="false"
        :auto-upload="false"
        :on-change="uploadChange"
      >
        <el-button type="success" :loading="uploading">上传到当前目录</el-button>
      </el-upload>
      <!-- 批量缓存头：勾选集操作，全选=当前过滤结果跨页（CONTEXT「缓存头」） -->
      <el-checkbox
        :model-value="allSelected"
        :indeterminate="someSelected && !allSelected"
        :disabled="batchRunning"
        @change="toggleAll"
      >全选</el-checkbox>
      <!-- 批量操作入口：勾选相关项（缓存头见 CONTEXT，媒体混淆见仓库级 ADR 0011）+ 全量项 -->
      <el-dropdown trigger="click" @command="onBatchCommand">
        <el-button>
          {{ selected.size ? `批量操作（${selected.size}）` : "批量操作" }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="set" :disabled="!selected.size || batchRunning">设置缓存头</el-dropdown-item>
            <el-dropdown-item command="query" :disabled="!selected.size || batchRunning">查看缓存头</el-dropdown-item>
            <el-dropdown-item command="encrypt" :disabled="!selected.size || batchRunning">批量加密</el-dropdown-item>
            <el-dropdown-item command="encryptAll" :disabled="batchRunning" divided>加密全部未混淆</el-dropdown-item>
            <el-dropdown-item command="export" :disabled="batchRunning" divided>导出 CDN 刷新列表</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
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

    <!-- 批量执行反馈：进度条 + 失败清单（失败片可整组重试，断点续传） -->
    <el-progress
      v-if="batchRunning"
      :percentage="progressPct"
      :stroke-width="8"
      class="batch-progress"
    />
    <el-alert v-if="!batchRunning && failures.length" type="error" class="batch-alert" @close="failures = []">
      <template #title>
        <span>{{ failTitle }}失败 {{ failures.length }} 个（仅展示前 5 条）</span>
        <el-button size="small" link type="primary" @click="retryFailures">重试失败项</el-button>
      </template>
      <div v-for="f in failures.slice(0, 5)" :key="f.key" class="fail-line">{{ f.key }}：{{ f.error }}</div>
    </el-alert>

    <OssGrid
      :oss="oss"
      mode="manage"
      :view="viewMode"
      :selected="selected"
      @copy="(r) => copyUrl(objectUrl(r.key))"
      @remove="remove"
      @query="queryOne"
      @toggle="toggleSel"
      @toggle-all="toggleAll"
    />

    <!-- 批量设置弹窗：预设档位 + 自定义时长，归一为 max-age=<秒> -->
    <el-dialog v-model="setDialogVisible" title="批量设置缓存头" width="440px">
      <p class="dialog-tip">
        将为 {{ selected.size }} 个选中对象写入 <code>Cache-Control: max-age=N</code>：
        OSS 服务端复制到自己改元数据（无流量），修改时间会刷新，Content-Type 保留。
      </p>
      <el-radio-group v-model="preset" class="preset-group">
        <el-radio-button v-for="p in PRESETS" :key="p.v" :value="p.v">{{ p.label }}</el-radio-button>
        <el-radio-button :value="0">自定义</el-radio-button>
      </el-radio-group>
      <div v-if="preset === 0" class="custom-row">
        <el-input-number
          v-model="customValue"
          :min="1"
          :max="100000"
          :controls="false"
          placeholder="时长"
          style="width: 110px"
        />
        <el-select v-model="customUnit" style="width: 90px">
          <el-option label="秒" :value="1" />
          <el-option label="分钟" :value="60" />
          <el-option label="小时" :value="3600" />
          <el-option label="天" :value="86400" />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="setDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!maxAgeValid" @click="runSet()">确定设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowDown, Grid, Tickets } from "@element-plus/icons-vue";
import type { UploadFile } from "element-plus";
import { api } from "../api";
import { dirUrl, objectUrl } from "../media";
import {
  OSS_DIRS,
  encryptBatch,
  queryCacheControlBatch,
  setCacheControlBatch,
  useOssList,
  type OssObject,
} from "../oss";
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

// ===== 批量缓存头（勾选集操作；值为会话内状态，列表刷新即失，不落库，CONTEXT「缓存头」）=====
const PRESETS = [
  { label: "1 小时", v: 3600 },
  { label: "1 天", v: 86400 },
  { label: "7 天", v: 604800 },
  { label: "30 天", v: 2592000 },
  { label: "365 天", v: 31536000 },
];

const selected = ref(new Set<string>());
const batchRunning = ref(false);
const progress = ref({ done: 0, total: 0 });
const failures = ref<{ key: string; error: string }[]>([]);
const lastAction = ref<"set" | "query" | "encrypt">("query");
const setDialogVisible = ref(false);
const preset = ref(31536000);
const customValue = ref<number | undefined>(undefined);
const customUnit = ref(86400);

const progressPct = computed(() =>
  progress.value.total ? Math.round((progress.value.done / progress.value.total) * 100) : 0,
);
const failTitle = computed(() =>
  lastAction.value === "set" ? "缓存头设置" : lastAction.value === "encrypt" ? "加密" : "缓存头查询",
);
const allSelected = computed(
  () => oss.filtered.length > 0 && oss.filtered.every((o) => selected.value.has(o.key)),
);
const someSelected = computed(() => oss.filtered.some((o) => selected.value.has(o.key)));
// 自定义模式：时长为正且换算后不超服务端上限（10 年整数秒）
const maxAgeValid = computed(
  () =>
    preset.value !== 0 ||
    ((customValue.value ?? 0) > 0 && (customValue.value ?? 0) * customUnit.value <= 10 * 365 * 86400),
);
const maxAge = computed(() =>
  preset.value === 0 ? Math.floor((customValue.value ?? 0) * customUnit.value) : preset.value,
);

// 列表刷新（上传/删除/换目录）后行对象重建：勾选与缓存头会话态一并失效
watch(
  () => oss.loading,
  (loading) => {
    if (loading) selected.value = new Set();
  },
);

function toggleSel(key: string, checked: boolean): void {
  const next = new Set(selected.value);
  if (checked) next.add(key);
  else next.delete(key);
  selected.value = next;
}

function toggleAll(v: boolean | string | number): void {
  selected.value = v === true ? new Set(oss.filtered.map((o) => o.key)) : new Set();
}

async function runSet(keys?: string[]): Promise<void> {
  const target = keys ?? [...selected.value];
  if (!target.length || batchRunning.value) return;
  const targetSet = new Set(target);
  setDialogVisible.value = false;
  batchRunning.value = true;
  lastAction.value = "set";
  failures.value = [];
  progress.value = { done: 0, total: target.length };
  try {
    const failed = await setCacheControlBatch(target, maxAge.value, (done, total) => {
      progress.value = { done, total };
    });
    failures.value = failed;
    // 成功者本地直接写新值（不重查）；失败者交给失败清单重试
    const failedSet = new Set(failed.map((f) => f.key));
    for (const o of oss.all) {
      if (targetSet.has(o.key) && !failedSet.has(o.key)) {
        o.cacheControl = `max-age=${maxAge.value}`;
        o.cacheFailed = false;
      }
    }
    if (!failed.length) ElMessage.success(`已为 ${target.length} 个对象设置 max-age=${maxAge.value}`);
  } finally {
    batchRunning.value = false;
  }
}

async function runQuery(keys?: string[]): Promise<void> {
  const target = keys ?? [...selected.value];
  if (!target.length || batchRunning.value) return;
  const targetSet = new Set(target);
  batchRunning.value = true;
  lastAction.value = "query";
  failures.value = [];
  progress.value = { done: 0, total: target.length };
  try {
    const { values, failed } = await queryCacheControlBatch(target, (done, total) => {
      progress.value = { done, total };
    });
    failures.value = failed;
    const failedSet = new Set(failed.map((f) => f.key));
    for (const o of oss.all) {
      if (values.has(o.key)) {
        o.cacheControl = values.get(o.key) ?? null;
        o.cacheFailed = false;
      } else if (failedSet.has(o.key)) {
        o.cacheFailed = true;
      }
    }
    if (!failed.length) ElMessage.success(`已查询 ${target.length} 个对象`);
  } finally {
    batchRunning.value = false;
  }
}

function queryOne(row: OssObject): void {
  void runQuery([row.key]);
}

// ===== 批量媒体混淆（仓库级 ADR 0011）：与缓存头批量共用进度/失败 UI；服务端跳过已混淆对象 =====
async function runEncrypt(keys?: string[]): Promise<void> {
  const target = keys ?? [...selected.value];
  if (!target.length || batchRunning.value) return;
  batchRunning.value = true;
  lastAction.value = "encrypt";
  failures.value = [];
  progress.value = { done: 0, total: target.length };
  try {
    const { done, skipped, failed } = await encryptBatch(target, (d, t) => {
      progress.value = { done: d, total: t };
    });
    failures.value = failed;
    if (!failed.length) ElMessage.success(`已加密 ${done} 个，跳过已加密 ${skipped} 个`);
  } finally {
    batchRunning.value = false;
  }
}

/** 媒体目录全量列举（lrc 不参与混淆；images 前缀递归含 thumbs）；超出列举上限时告警并只处理已列出部分 */
async function fetchMediaKeys(): Promise<string[]> {
  const keys: string[] = [];
  let truncated = false;
  for (const prefix of ["uploads", "images"]) {
    const { data } = await api.get("/admin/oss/objects", { params: { prefix } });
    keys.push(...(data.items as { key: string }[]).map((o) => o.key));
    truncated = truncated || !!data.truncated;
  }
  if (truncated) ElMessage.warning("对象数超出列举上限，仅处理已列出的部分");
  return keys;
}

async function encryptAll(): Promise<void> {
  if (batchRunning.value) return;
  await ElMessageBox.confirm(
    "将把 uploads/、images/（含缩略图）全部未混淆对象逐字节 +31 加密，歌词 lrc/ 不参与；已加密对象自动跳过。音频大文件耗时较长，中断后重跑即可续传。",
    "加密全部未混淆",
    { type: "warning" },
  );
  const keys = await fetchMediaKeys();
  if (!keys.length) return void ElMessage.info("没有可处理的对象");
  await runEncrypt(keys);
}

/** 全量 URL 列表导出（.txt）：供 CDN 控制台刷新同 key 重写后的旧缓存，头部附目录刷新写法 */
async function exportRefreshList(): Promise<void> {
  const keys = await fetchMediaKeys();
  const lines = [
    `# CDN URL 刷新列表（共 ${keys.length} 条）：粘贴到阿里云 CDN 控制台「刷新预热 → URL 刷新」，单次上限 2000 条，超量分批提交`,
    "# 备选：目录刷新（3 条覆盖全部媒体目录，日配额 100 条）",
    `# ${dirUrl("uploads")}`,
    `# ${dirUrl("images")}`,
    `# ${dirUrl("images/thumbs")}`,
    ...keys.map((k) => objectUrl(k)),
  ];
  const blob = new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = "cdn-refresh-urls.txt";
  a.click();
  URL.revokeObjectURL(a.href);
}

async function retryFailures(): Promise<void> {
  const keys = failures.value.map((f) => f.key);
  if (lastAction.value === "set") await runSet(keys);
  else if (lastAction.value === "encrypt") await runEncrypt(keys);
  else await runQuery(keys);
}

// 下拉菜单命令分派：与旧按钮的一对一处理函数完全对应
function onBatchCommand(cmd: string): void {
  if (cmd === "set") setDialogVisible.value = true;
  else if (cmd === "query") void runQuery();
  else if (cmd === "encrypt") void runEncrypt();
  else if (cmd === "encryptAll") void encryptAll();
  else if (cmd === "export") void exportRefreshList();
}

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
.batch-progress {
  margin-bottom: 12px;
}
.batch-alert {
  margin-bottom: 12px;
}
.fail-line {
  font-size: 12px;
  word-break: break-all;
}
.dialog-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.preset-group {
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.custom-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.view-toggle {
  margin-left: auto;
}
.vt-icon {
  vertical-align: -2px;
  margin-right: 2px;
}
</style>
