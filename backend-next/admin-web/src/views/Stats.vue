<template>
  <div>
    <!-- 当日访问分解（条数口径，ADR 0008） -->
    <div class="stat-row" v-loading="summaryLoading">
      <div class="stat">
        <div class="num">{{ summary.total }}</div>
        <div class="label">今日装载</div>
      </div>
      <div class="stat">
        <div class="num hit">{{ summary.hits }}</div>
        <div class="label">命中</div>
      </div>
      <div class="stat">
        <div class="num">{{ summary.misses }}</div>
        <div class="label">未命中</div>
      </div>
      <div class="stat">
        <div class="num">{{ summary.devices }}</div>
        <div class="label">活跃设备</div>
      </div>
      <div class="stat">
        <div class="num hit">{{ (summary.hitRate * 100).toFixed(1) }}%</div>
        <div class="label">命中率</div>
      </div>
    </div>

    <!-- 在线人数趋势 -->
    <div class="section">
      <div class="section-head">
        <span>在线人数趋势（5 分钟采样；30 天视图为小时平均）</span>
        <el-radio-group v-model="range" size="small" @change="loadTrend">
          <el-radio-button value="24h">24 小时</el-radio-button>
          <el-radio-button value="7d">7 天</el-radio-button>
          <el-radio-button value="30d">30 天</el-radio-button>
        </el-radio-group>
      </div>
      <div v-if="trend.length" class="chart">
        <svg :viewBox="`0 0 ${W} ${H}`">
          <line :x1="PAD" :y1="H - PAD" :x2="W - PAD" :y2="H - PAD" class="axis" />
          <line :x1="PAD" :y1="PAD" :x2="W - PAD" :y2="PAD" class="grid" />
          <polyline :points="linePoints" class="line" />
          <text :x="W - PAD" :y="PAD - 6" text-anchor="end" class="grid-label">峰值 {{ maxV }}</text>
          <text v-for="l in xLabels" :key="l.i" :x="l.x" :y="H - 6" :text-anchor="l.anchor" class="x-label">
            {{ l.text }}
          </text>
        </svg>
      </div>
      <el-empty v-else description="暂无采样数据" :image-size="60" />
    </div>

    <!-- 设备存储快照：每设备最新一条事实的快照值 -->
    <div class="section">
      <div class="section-head"><span>设备存储快照（每设备取最新事实）</span></div>
      <el-table :data="devices" v-loading="devicesLoading" stripe size="small">
        <el-table-column label="设备" min-width="130">
          <template #default="{ row }">
            <span class="mono" :title="row.deviceId">{{ shortId(row.deviceId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="缓存预算" width="100">
          <template #default="{ row }">{{ fmtBytes(row.allocatedStorage) }}</template>
        </el-table-column>
        <el-table-column label="实际占用" width="100">
          <template #default="{ row }">{{ fmtBytes(row.usedStorage) }}</template>
        </el-table-column>
        <el-table-column label="事实数" width="80">
          <template #default="{ row }">{{ Number(row.facts) }}</template>
        </el-table-column>
        <el-table-column label="最近访问" width="170">
          <template #default="{ row }">{{ new Date(row.lastSeen).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
      </el-table>
    </div>

    <!-- 访问明细 -->
    <div class="section">
      <div class="section-head"><span>访问明细（无清理任务，最多保留 1000 条）</span></div>
      <el-table :data="items" v-loading="loading" stripe size="small">
        <el-table-column label="歌曲" min-width="180">
          <template #default="{ row }">{{ row.songTitle ?? `已删除歌曲 #${row.songId}` }}</template>
        </el-table-column>
        <el-table-column label="设备" min-width="130">
          <template #default="{ row }">
            <span class="mono" :title="row.deviceId">{{ shortId(row.deviceId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="命中" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.cacheHit ? 'success' : 'info'">
              {{ row.cacheHit ? "命中" : "未命中" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ new Date(row.accessedAt).toLocaleString() }}</template>
        </el-table-column>
      </el-table>
      <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { api } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";

// ---- 当日访问分解 ----

type Summary = { total: number; hits: number; misses: number; devices: number; hitRate: number };
const summary = reactive<Summary>({ total: 0, hits: 0, misses: 0, devices: 0, hitRate: 0 });
const summaryLoading = ref(false);

async function loadSummary() {
  summaryLoading.value = true;
  try {
    Object.assign(summary, (await api.get<Summary>("/admin/stats/summary")).data);
  } finally {
    summaryLoading.value = false;
  }
}

// ---- 在线趋势：手写 SVG 折线，不引图表库 ----

type TrendPoint = { t: number; v: number };
const range = ref("24h");
const trend = ref<TrendPoint[]>([]);

const W = 720;
const H = 200;
const PAD = 30;

const maxV = computed(() => Math.max(1, ...trend.value.map((p) => p.v)));

const linePoints = computed(() => {
  const n = trend.value.length;
  if (!n) return "";
  const span = W - 2 * PAD;
  return trend.value
    .map((p, i) => {
      const x = PAD + (n === 1 ? span / 2 : (i / (n - 1)) * span);
      const y = H - PAD - (p.v / maxV.value) * (H - 2 * PAD);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");
});

const xLabels = computed(() => {
  const n = trend.value.length;
  if (!n) return [];
  const idx = n === 1 ? [0] : [0, Math.floor((n - 1) / 2), n - 1];
  return idx.map((i) => ({
    i,
    x: PAD + (n === 1 ? (W - 2 * PAD) / 2 : (i / (n - 1)) * (W - 2 * PAD)),
    anchor: i === 0 ? "start" : i === n - 1 ? "end" : "middle",
    text: fmtTime(trend.value[i].t),
  }));
});

function fmtTime(t: number): string {
  const d = new Date(t);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hm = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  return range.value === "30d" ? `${mm}-${dd}` : `${mm}-${dd} ${hm}`;
}

async function loadTrend() {
  // 24h/7d 返回 sampledAt（Prisma ISO），30d 返回 bucket（本地时间无时区后缀），统一转毫秒
  type RawPoint = { bucket?: string; sampledAt?: string; totalOnline: number | string };
  const { data } = await api.get<{ points: RawPoint[] }>("/admin/stats/online-trend", {
    params: { range: range.value },
  });
  trend.value = data.points
    .map((p) => ({ t: new Date(p.bucket ?? p.sampledAt ?? "").getTime(), v: Number(p.totalOnline) || 0 }))
    .filter((p) => !Number.isNaN(p.t));
}

// ---- 设备存储快照 ----

type DeviceRow = {
  deviceId: string;
  allocatedStorage: number | string | null;
  usedStorage: number | string | null;
  userAgent: string | null;
  lastSeen: string;
  facts: number | string;
};
const devices = ref<DeviceRow[]>([]);
const devicesLoading = ref(false);

async function loadDevices() {
  devicesLoading.value = true;
  try {
    devices.value = (await api.get<{ items: DeviceRow[] }>("/admin/stats/devices")).data.items;
  } finally {
    devicesLoading.value = false;
  }
}

function fmtBytes(v: number | string | null): string {
  if (v === null || v === undefined || v === "") return "-";
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0) return "-";
  if (n >= 1024 ** 3) return (n / 1024 ** 3).toFixed(2) + " GB";
  if (n >= 1024 ** 2) return (n / 1024 ** 2).toFixed(1) + " MB";
  if (n >= 1024) return (n / 1024).toFixed(0) + " KB";
  return n + " B";
}

// ---- 访问明细 ----

type FactRow = {
  id: number;
  songId: number;
  songTitle: string | null;
  deviceId: string;
  cacheHit: boolean | number;
  accessedAt: string;
};
const { items, total, page, size, loading, load } = usePagedList<FactRow>("/admin/stats/facts");

// ---- 公共 ----

function shortId(id: string): string {
  return id.length > 8 ? id.slice(0, 8) + "…" : id;
}

onMounted(() => {
  void loadSummary();
  void loadTrend();
  void loadDevices();
  void load();
});
</script>

<style scoped>
.stat-row {
  display: flex;
  gap: 32px;
  padding: 16px 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
.stat .num {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.2;
}
.stat .num.hit {
  color: var(--el-color-success);
}
.stat .label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.section {
  margin-top: 20px;
}
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  color: var(--el-text-color-regular);
}
.chart svg {
  width: 100%;
  height: 200px;
}
.chart .line {
  fill: none;
  stroke: var(--el-color-primary);
  stroke-width: 2;
}
.chart .axis {
  stroke: var(--el-border-color);
}
.chart .grid {
  stroke: var(--el-border-color-lighter);
  stroke-dasharray: 4 4;
}
.chart .x-label,
.chart .grid-label {
  font-size: 11px;
  fill: var(--el-text-color-secondary);
}
.mono {
  font-family: monospace;
  font-size: 12px;
}
</style>
