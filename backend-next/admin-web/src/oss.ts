// OSS 对象列举通用逻辑（管理页与选择器共用，ADR 0008）：
// 服务端全量返回某目录对象（含解密原名），keyword 过滤与分页均在前端本地做
import { computed, reactive, ref, watch, type Ref } from "vue";
import { api } from "./api";
import { loadMediaBase } from "./media";

export type OssObject = {
  key: string;
  size: number;
  lastModified: string;
  /** key 密文段解出的原名；存量明文 key / 解密失败时缺省 */
  originalName?: string;
  /** 缓存头会话内状态：undefined=未查，null=未设置，其余为 max-age 原值；不落库，列表刷新即失 */
  cacheControl?: string | null;
  /** 该对象缓存头查询/设置失败标记（展示优先于 cacheControl） */
  cacheFailed?: boolean;
};

/** 可浏览的媒体目录（与服务端列举白名单一致，ADR 0004）；缩略图仅浏览、无上传入口（ADR 0011 修订） */
export const OSS_DIRS = [
  { value: "uploads", label: "音频 uploads/" },
  { value: "images", label: "图片 images/" },
  { value: "images/thumbs", label: "缩略图 images/thumbs/" },
  { value: "lrc", label: "歌词 lrc/" },
];

export function fmtSize(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(2)} MB`;
}

export function fmtTime(s: string): string {
  return new Date(s).toLocaleString();
}

/**
 * 一次拉全量 + 本地过滤分页：refresh 拉取整目录并重置页码；
 * keyword 同时匹配解密原名与 key 尾段（存量明文 key 靠后者可搜）。
 */
export function useOssList(defaultDir: string, defaultPageSize = 20) {
  const dir = ref(defaultDir);
  const keyword = ref("");
  const page = ref(1);
  const pageSize = ref(defaultPageSize);
  const all = ref<OssObject[]>([]) as Ref<OssObject[]>;
  const loading = ref(false);
  const truncated = ref(false);

  const filtered = computed(() => {
    const kw = keyword.value.trim().toLowerCase();
    if (!kw) return all.value;
    return all.value.filter((o) => {
      const tail = o.key.split("/").pop() ?? o.key;
      return (
        (o.originalName ?? "").toLowerCase().includes(kw) || tail.toLowerCase().includes(kw)
      );
    });
  });
  // 分页切片交给 computed：keyword/page 变化自动重算，无需手动 load
  const items = computed(() =>
    filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
  );
  // 过滤条件变化后当前页可能超出总页数，重置回第 1 页
  watch([keyword, dir], () => {
    page.value = 1;
  });

  async function refresh(): Promise<void> {
    loading.value = true;
    try {
      // 预览 URL 需要媒体基地址
      await loadMediaBase();
      const { data } = await api.get("/admin/oss/objects", { params: { prefix: dir.value } });
      all.value = data.items;
      truncated.value = !!data.truncated;
      page.value = 1;
    } finally {
      loading.value = false;
    }
  }

  return reactive({
    dir,
    keyword,
    page,
    pageSize,
    items,
    all,
    filtered,
    total: computed(() => filtered.value.length),
    loading,
    truncated,
    refresh,
  });
}

export type CacheFail = { key: string; error: string };

// 缓存头批量操作分片大小：单请求时长可控（50 × ~150ms ≈ 8s），失败按片重试即断点续传
const CACHE_SHARD = 50;

/** 批量设置缓存头：分片提交，返回逐 key 失败明细；整片请求异常折算为逐 key 失败，进度经 onProgress 上报 */
export async function setCacheControlBatch(
  keys: string[],
  maxAgeSeconds: number,
  onProgress?: (done: number, total: number) => void,
): Promise<CacheFail[]> {
  const failed: CacheFail[] = [];
  let done = 0;
  for (let i = 0; i < keys.length; i += CACHE_SHARD) {
    const shard = keys.slice(i, i + CACHE_SHARD);
    try {
      const { data } = await api.post("/admin/oss/cache-control", {
        keys: shard,
        maxAge: maxAgeSeconds,
      });
      failed.push(...data.failed);
    } catch (e) {
      const error = e instanceof Error ? e.message : "请求失败";
      for (const key of shard) failed.push({ key, error });
    }
    done += shard.length;
    onProgress?.(done, keys.length);
  }
  return failed;
}

/** 批量查询缓存头：分片提交，返回 key → cacheControl（null=未设置）；失败 key 不入 values */
export async function queryCacheControlBatch(
  keys: string[],
  onProgress?: (done: number, total: number) => void,
): Promise<{ values: Map<string, string | null>; failed: CacheFail[] }> {
  const values = new Map<string, string | null>();
  const failed: CacheFail[] = [];
  let done = 0;
  for (let i = 0; i < keys.length; i += CACHE_SHARD) {
    const shard = keys.slice(i, i + CACHE_SHARD);
    try {
      const { data } = await api.post("/admin/oss/cache-control/query", { keys: shard });
      for (const it of data.items as { key: string; cacheControl: string | null }[]) {
        values.set(it.key, it.cacheControl);
      }
      failed.push(...data.failed);
    } catch (e) {
      const error = e instanceof Error ? e.message : "请求失败";
      for (const key of shard) failed.push({ key, error });
    }
    done += shard.length;
    onProgress?.(done, keys.length);
  }
  return { values, failed };
}

// 媒体混淆批量分片大小：逐 key 是流式下载+回写（音频可达数百 MB），调小分片控制单请求时长
const ENCRYPT_SHARD = 10;

export type EncryptBatchResult = { done: number; skipped: number; failed: CacheFail[] };

/** 批量媒体混淆（仓库级 ADR 0011）：分片提交，服务端按 encrypted 元数据跳过已混淆对象（幂等），
 *  返回实加密/跳过计数与逐 key 失败明细，整片请求异常折算为逐 key 失败 */
export async function encryptBatch(
  keys: string[],
  onProgress?: (done: number, total: number) => void,
): Promise<EncryptBatchResult> {
  const failed: CacheFail[] = [];
  let done = 0;
  let skipped = 0;
  let processed = 0;
  for (let i = 0; i < keys.length; i += ENCRYPT_SHARD) {
    const shard = keys.slice(i, i + ENCRYPT_SHARD);
    try {
      const { data } = await api.post("/admin/oss/encrypt", { keys: shard });
      done += data.done ?? 0;
      skipped += data.skipped ?? 0;
      failed.push(...(data.failed ?? []));
    } catch (e) {
      const error = e instanceof Error ? e.message : "请求失败";
      for (const key of shard) failed.push({ key, error });
    }
    processed += shard.length;
    onProgress?.(processed, keys.length);
  }
  return { done, skipped, failed };
}
