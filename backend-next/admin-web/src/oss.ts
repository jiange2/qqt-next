// OSS 对象列举通用逻辑（管理页与选择器共用，ADR 0008）：
// 服务端全量返回某目录对象（含解密原名），keyword 过滤与分页均在前端本地做
import { computed, reactive, ref, watch } from "vue";
import { api } from "./api";
import { loadMediaBase } from "./media";

export type OssObject = {
  key: string;
  size: number;
  lastModified: string;
  /** key 密文段解出的原名；存量明文 key / 解密失败时缺省 */
  originalName?: string;
};

/** 可浏览的媒体目录（与服务端白名单一致，ADR 0004） */
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
  const all = ref<OssObject[]>([]) as { value: OssObject[] };
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
    total: computed(() => filtered.value.length),
    loading,
    truncated,
    refresh,
  });
}
