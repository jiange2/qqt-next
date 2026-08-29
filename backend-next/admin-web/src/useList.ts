// 分页列表通用逻辑
import { ref } from "vue";
import { api } from "./api";
import { loadMediaBase } from "./media";

export function usePagedList<T>(
  url: string,
  extraParams?: () => Record<string, unknown>,
) {
  const items = ref<T[]>([]) as { value: T[] };
  const total = ref(0);
  const page = ref(1);
  const size = ref(20);
  const keyword = ref("");
  const loading = ref(false);

  async function load() {
    loading.value = true;
    try {
      // 列表里的缩略图需要媒体基地址，先于首次拉取加载
      await loadMediaBase();
      const { data } = await api.get(url, {
        params: {
          page: page.value,
          size: size.value,
          keyword: keyword.value || undefined,
          ...(extraParams?.() ?? {}),
        },
      });
      items.value = data.items;
      total.value = data.total;
    } finally {
      loading.value = false;
    }
  }

  return { items, total, page, size, keyword, loading, load };
}
