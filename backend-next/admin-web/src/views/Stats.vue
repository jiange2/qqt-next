<template>
  <div>
    <!-- 当日访问分解（条数口径，ADR 0008）；「在线设备」非当日窗口，为「在线状态」的即时推导 -->
    <div class="stat-row" v-loading="summaryLoading">
      <el-tooltip content="刷新" placement="top">
        <el-button class="row-refresh" circle text :icon="Refresh" :loading="summaryLoading" @click="loadSummary()" />
      </el-tooltip>
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
      <el-tooltip content="预期下线未过期的设备数（即时推导；与趋势折线的 5 分钟采样不同源）" placement="top">
        <div class="stat">
          <div class="num">{{ summary.online }}</div>
          <div class="label">在线设备</div>
        </div>
      </el-tooltip>
      <div class="stat">
        <div class="num hit">{{ (summary.hitRate * 100).toFixed(1) }}%</div>
        <div class="label">命中率</div>
      </div>
    </div>

    <!-- 设备快照 / 访问明细 / 两张趋势图；后三者首次点开才加载（懒加载 tab） -->
    <el-tabs v-model="tab" class="section">
      <el-tab-pane label="设备快照" name="devices">
        <div class="section-head">
          <span>每设备取最新事实（点击行查看该设备访问明细）</span>
          <el-tooltip content="刷新" placement="top">
            <el-button circle text :icon="Refresh" :loading="devicesLoading" @click="loadDevices()" />
          </el-tooltip>
        </div>
        <el-table :data="devices" v-loading="devicesLoading" stripe size="small" class="clickable-rows" @row-click="openDeviceFacts">
          <el-table-column type="index" label="序号" width="60" :index="deviceRowIndex" />
          <el-table-column label="设备" min-width="290">
            <template #default="{ row }">
              <span class="mono">{{ row.deviceId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="在线" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.isOnline ? 'success' : 'info'">{{ row.isOnline ? "在线" : "离线" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="版本" width="80">
            <template #default="{ row }">{{ row.appVersion || "未知" }}</template>
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
          <el-table-column label="首次创建" width="170">
            <template #default="{ row }">{{ new Date(row.firstSeen).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column label="所用时长" width="90">
            <template #default="{ row }">{{ fmtDuration(row.usedDuration) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">
              <div class="time-cell">
                <div class="window">时间（{{ fmtWindow(row.usedDuration) }}）</div>
                <div>{{ new Date(row.lastSeen).toLocaleString() }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近上报 IP" width="150">
            <template #default="{ row }"><span class="mono">{{ row.ipAddress }}</span></template>
          </el-table-column>
          <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
        </el-table>
        <AppPagination v-model:page="devicesPage" v-model:size="devicesSize" :total="devicesTotal" @load="loadDevices" />
      </el-tab-pane>

      <el-tab-pane label="访问明细" name="facts" lazy>
        <div class="section-head">
          <span>无清理任务，全量保留</span>
          <el-tooltip content="刷新" placement="top">
            <el-button circle text :icon="Refresh" :loading="loading" @click="loadFacts()" />
          </el-tooltip>
        </div>
        <el-table :data="items" v-loading="loading" stripe size="small">
          <el-table-column label="歌曲" min-width="180">
            <template #default="{ row }">{{ row.songTitle ?? `已删除歌曲 #${row.songId}` }}</template>
          </el-table-column>
          <el-table-column label="设备" min-width="290">
            <template #default="{ row }">
              <span class="mono">{{ row.deviceId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="IP" width="140">
            <template #default="{ row }"><span class="mono">{{ row.ipAddress }}</span></template>
          </el-table-column>
          <el-table-column label="版本" width="80">
            <template #default="{ row }">{{ row.appVersion || "未知" }}</template>
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
        <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="loadFacts" />
      </el-tab-pane>

      <el-tab-pane label="每日装载趋势" name="daily" lazy>
        <div class="section-head">
          <span>已结束的自然日，不含今天；空日补零</span>
          <div class="head-actions">
            <el-radio-group v-model="dailyRange" size="small" @change="loadDailyTrend">
              <el-radio-button value="7d">7 天</el-radio-button>
              <el-radio-button value="30d">30 天</el-radio-button>
              <el-radio-button value="90d">90 天</el-radio-button>
              <el-radio-button value="1y">1 年</el-radio-button>
            </el-radio-group>
            <el-tooltip content="刷新" placement="top">
              <el-button circle text :icon="Refresh" :loading="dailyLoading" @click="loadDailyTrend" />
            </el-tooltip>
          </div>
        </div>
        <div v-loading="dailyLoading" class="chart-area">
          <LineChart v-if="daily.length" :option="dailyOption" />
          <el-empty v-else-if="!dailyLoading" description="暂无装载数据" :image-size="60" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="在线设备趋势" name="online" lazy>
        <div class="section-head">
          <span>5 分钟采样；30 天视图为小时平均</span>
          <div class="head-actions">
            <el-radio-group v-model="range" size="small" @change="loadTrend">
              <el-radio-button value="24h">24 小时</el-radio-button>
              <el-radio-button value="7d">7 天</el-radio-button>
              <el-radio-button value="30d">30 天</el-radio-button>
            </el-radio-group>
            <el-tooltip content="刷新" placement="top">
              <el-button circle text :icon="Refresh" :loading="trendLoading" @click="loadTrend" />
            </el-tooltip>
          </div>
        </div>
        <div v-loading="trendLoading" class="chart-area">
          <LineChart v-if="trend.length" :option="trendOption" />
          <el-empty v-else-if="!trendLoading" description="暂无采样数据" :image-size="60" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 设备下钻抽屉：快照行点击触发，按设备 ID 收窄的访问明细（同一 facts 列表） -->
    <el-drawer v-model="deviceDrawer" :title="`访问明细 · ${drawerDeviceId}`" size="70%">
      <div class="section-head">
        <span>该设备全部访问事实，最新在前</span>
        <el-tooltip content="刷新" placement="top">
          <el-button circle text :icon="Refresh" :loading="deviceLoading" @click="loadDeviceFacts()" />
        </el-tooltip>
      </div>
      <el-table :data="deviceItems" v-loading="deviceLoading" stripe size="small">
        <el-table-column type="index" label="序号" width="60" :index="drawerRowIndex" />
        <el-table-column label="歌曲" min-width="180">
          <template #default="{ row }">{{ row.songTitle ?? `已删除歌曲 #${row.songId}` }}</template>
        </el-table-column>
        <el-table-column label="IP" width="140">
          <template #default="{ row }"><span class="mono">{{ row.ipAddress }}</span></template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">{{ row.appVersion || "未知" }}</template>
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
      <AppPagination v-model:page="devicePage" v-model:size="deviceSize" :total="deviceTotal" @load="loadDeviceFacts" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import type { EChartsOption } from "echarts";
import { api } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";
import LineChart from "../components/LineChart.vue";

// ---- 当日访问分解 ----

type Summary = {
  total: number;
  hits: number;
  misses: number;
  devices: number;
  hitRate: number;
  online: number;
};
const summary = reactive<Summary>({ total: 0, hits: 0, misses: 0, devices: 0, hitRate: 0, online: 0 });
const summaryLoading = ref(false);

// silent：轮询刷新不转 loading 遮罩，避免打断阅读
async function loadSummary(silent = false) {
  if (!silent) summaryLoading.value = true;
  try {
    Object.assign(summary, (await api.get<Summary>("/admin/stats/summary")).data);
  } finally {
    if (!silent) summaryLoading.value = false;
  }
}

// ---- 图表：ECharts（LineChart 组件内按需注册折线/网格/图例/提示） ----

// 取 Element Plus 主题变量，图颜色跟随后台主题而非 ECharts 默认色板
function cssVar(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}
const COLORS = {
  primary: cssVar("--el-color-primary", "#409eff"),
  success: cssVar("--el-color-success", "#67c23a"),
  info: cssVar("--el-color-info", "#909399"),
};

// ---- 在线设备趋势：24h/7d 原始 5 分钟采样，30d 小时平均 ----

type TrendPoint = { t: number; v: number };
const range = ref("24h");
const trend = ref<TrendPoint[]>([]);
const trendLoading = ref(false);

const trendOption = computed<EChartsOption>(() => ({
  color: [COLORS.primary],
  grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
  tooltip: { trigger: "axis" },
  // time 轴按真实时刻等距：采样中断按实际时长展开，不再被按点等距拉伸
  xAxis: { type: "time", axisLabel: { formatter: (v: number) => fmtTime(v) } },
  yAxis: { type: "value", minInterval: 1 },
  series: [
    { name: "在线设备", type: "line", showSymbol: false, data: trend.value.map((p) => [p.t, p.v]) },
  ],
}));

function fmtTime(t: number): string {
  const d = new Date(t);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hm = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  return range.value === "30d" ? `${mm}-${dd}` : `${mm}-${dd} ${hm}`;
}

async function loadTrend() {
  trendLoading.value = true;
  try {
    // 24h/7d 返回 sampledAt（Prisma ISO），30d 返回 bucket（本地时间无时区后缀），统一转毫秒
    type RawPoint = { bucket?: string; sampledAt?: string; totalOnline: number | string };
    const { data } = await api.get<{ points: RawPoint[] }>("/admin/stats/online-trend", {
      params: { range: range.value },
    });
    trend.value = data.points
      .map((p) => ({ t: new Date(p.bucket ?? p.sampledAt ?? "").getTime(), v: Number(p.totalOnline) || 0 }))
      .filter((p) => !Number.isNaN(p.t));
  } finally {
    trendLoading.value = false;
  }
}

// ---- 每日装载趋势：已结束的北京自然日，三线（装载/命中/未命中） ----

type DailyPoint = { date: string; total: number; hits: number; misses: number };
const dailyRange = ref("7d");
const daily = ref<DailyPoint[]>([]);
const dailyLoading = ref(false);

const dailyOption = computed<EChartsOption>(() => ({
  color: [COLORS.primary, COLORS.success, COLORS.info],
  grid: { left: 8, right: 16, top: 16, bottom: 30, containLabel: true },
  tooltip: { trigger: "axis" },
  legend: { data: ["装载", "命中", "未命中"], bottom: 0, icon: "rect", itemWidth: 14, itemHeight: 2 },
  xAxis: {
    type: "category",
    boundaryGap: false,
    data: daily.value.map((p) => p.date),
    // 标签由库按宽度抽稀；1 年视图用「YY-MM」避免月日歧义
    axisLabel: {
      formatter: (v: string) =>
        dailyRange.value === "1y" ? `${v.slice(2, 4)}-${v.slice(5, 7)}` : v.slice(5),
    },
  },
  yAxis: { type: "value", minInterval: 1 },
  series: [
    { name: "装载", type: "line", showSymbol: false, data: daily.value.map((p) => p.total) },
    { name: "命中", type: "line", showSymbol: false, data: daily.value.map((p) => p.hits) },
    { name: "未命中", type: "line", showSymbol: false, data: daily.value.map((p) => p.misses) },
  ],
}));

async function loadDailyTrend() {
  dailyLoading.value = true;
  try {
    const { data } = await api.get<{ points: DailyPoint[] }>("/admin/stats/daily-trend", {
      params: { range: dailyRange.value },
    });
    daily.value = data.points;
  } finally {
    dailyLoading.value = false;
  }
}

// ---- 设备快照 ----

type DeviceRow = {
  deviceId: string;
  allocatedStorage: number | string | null;
  usedStorage: number | string | null;
  userAgent: string | null;
  appVersion: string | null;
  usedDuration: number | string;
  ipAddress: string;
  lastSeen: string;
  facts: number | string;
  firstSeen: string;
  isOnline: boolean;
};
const {
  items: devices,
  total: devicesTotal,
  page: devicesPage,
  size: devicesSize,
  loading: devicesLoading,
  load: loadDevices,
} = usePagedList<DeviceRow>("/admin/stats/devices");

// 序号跨页连续
function deviceRowIndex(i: number) {
  return (devicesPage.value - 1) * devicesSize.value + i + 1;
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

// 预期下线推导实际采用的 songs.duration（秒）；0 = 时长缺失（仅 +1 分钟冗余）
function fmtDuration(v: number | string | null): string {
  const n = Number(v);
  if (!Number.isFinite(n) || n <= 0) return "未知";
  return `${Math.floor(n / 60)}:${String(Math.round(n) % 60).padStart(2, "0")}`;
}

// 在线窗口长度：所用时长 + 1 分钟冗余（口径同「在线状态」词条），四舍五入到分钟；时长缺失时为 +1 分钟
function fmtWindow(v: number | string | null): string {
  const n = Number(v);
  const secs = (Number.isFinite(n) && n > 0 ? n : 0) + 60;
  return `+${Math.round(secs / 60)}分钟`;
}

// ---- 访问明细 ----

type FactRow = {
  id: number;
  songId: number;
  songTitle: string | null;
  deviceId: string;
  cacheHit: boolean | number;
  appVersion: string | null;
  ipAddress: string;
  accessedAt: string;
};
const { items, total, page, size, loading, load: loadFacts } = usePagedList<FactRow>("/admin/stats/facts");

// ---- 设备下钻抽屉：快照行点击 → 该设备访问明细（同一 facts 列表，按设备 ID 收窄） ----

const deviceDrawer = ref(false);
const drawerDeviceId = ref("");
const {
  items: deviceItems,
  total: deviceTotal,
  page: devicePage,
  size: deviceSize,
  loading: deviceLoading,
  load: loadDeviceFacts,
} = usePagedList<FactRow>("/admin/stats/facts", () => ({ deviceId: drawerDeviceId.value }));

function openDeviceFacts(row: DeviceRow) {
  drawerDeviceId.value = row.deviceId;
  devicePage.value = 1;
  deviceDrawer.value = true;
  void loadDeviceFacts();
}

// 序号跨页连续
function drawerRowIndex(i: number) {
  return (devicePage.value - 1) * deviceSize.value + i + 1;
}

// ---- Tab：各页首次激活才加载，之后切回不重拉（内存缓存） ----

const tab = ref("devices");
const loadedTabs = new Set<string>();
watch(tab, (name) => {
  if (loadedTabs.has(name)) return;
  if (name === "facts") {
    loadedTabs.add(name);
    void loadFacts();
  } else if (name === "daily") {
    loadedTabs.add(name);
    void loadDailyTrend();
  } else if (name === "online") {
    loadedTabs.add(name);
    void loadTrend();
  }
});

// ---- 公共 ----

// 在线数字靠轮询保鲜（口径为即时推导，非采样值）：每 5 分钟静默刷新整栏，页面不可见时跳过
const SUMMARY_POLL_MS = 5 * 60 * 1000;
let summaryTimer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  void loadSummary();
  void loadDevices();
  summaryTimer = setInterval(() => {
    if (!document.hidden) void loadSummary(true);
  }, SUMMARY_POLL_MS);
});

onUnmounted(() => clearInterval(summaryTimer));
</script>

<style scoped>
.stat-row {
  position: relative;
  display: flex;
  gap: 32px;
  padding: 16px 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
.row-refresh {
  position: absolute;
  top: 10px;
  right: 10px;
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 加载遮罩需占位高度；图表高度由 LineChart 的 height 属性决定 */
.chart-area {
  min-height: 200px;
}
.mono {
  font-family: monospace;
  font-size: 12px;
}
.time-cell .window {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.clickable-rows :deep(.el-table__row) {
  cursor: pointer;
}
</style>
