<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="catFilter"
        filterable
        clearable
        placeholder="全部分类"
        style="width: 180px"
        @change="search"
      >
        <el-option label="未分类专辑" :value="0" />
        <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
      </el-select>
      <el-button type="success" @click="openCreate">新建专辑</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.image" :src="thumbUrl(row.image)" style="width: 48px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column label="分类" width="120">
        <template #default="{ row }">{{ row.category?.name ?? "未分类" }}</template>
      </el-table-column>
      <el-table-column label="歌曲数" width="80">
        <template #default="{ row }">{{ row._count?.songs ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="艺术家" width="200">
        <template #default="{ row }">
          {{ artistNames(row) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{ row }">
          <el-button size="small" @click="openSongs(row)">歌曲</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑专辑' : '新建专辑'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类">
          <!-- 可空下拉 + 「未分类」出口（backend-next ADR 0009，哨兵 0 → null） -->
          <el-select v-model="form.categoryId" filterable style="width: 100%">
            <el-option label="未分类" :value="0" />
            <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="艺术家">
          <el-select v-model="form.artistIds" multiple filterable style="width: 100%">
            <el-option v-for="a in artistOptions" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" /></el-form-item>
        <el-form-item label="封面">
          <UploadField v-model="imageFile" accept="image/*" dir="images/thumbs" />
        </el-form-item>
      </el-form>
      <el-progress v-if="pct > 0 && pct < 100" :percentage="pct" style="margin-top: 4px" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 专辑内歌曲抽屉（backend-next ADR 0007）：排序/移除/认领 -->
    <DimensionDrawer v-if="drawerParent" v-model="songsOpen" kind="albums" :parent="drawerParent" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { api, formBody, saveForm } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";
import { thumbUrl } from "../media";
import UploadField from "../components/UploadField.vue";
import DimensionDrawer from "../components/DimensionDrawer.vue";

type Row = {
  id: number;
  name: string;
  image: string;
  status: boolean;
  artists: { artistId: number }[];
  category: { id: number; name: string } | null;
  _count: { songs: number };
};
type Opt = { id: number; name: string };

// 分类过滤哨兵 0 = 未分类专辑（backend-next ADR 0009，与歌曲页「未归专辑」对称）
const catFilter = ref<number>();
const { items, total, page, size, loading, load } = usePagedList<Row>("/admin/albums", () => ({
  category_id: catFilter.value ?? "",
}));
const artistOptions = ref<Opt[]>([]);
const categoryOptions = ref<Opt[]>([]);

function search() {
  page.value = 1;
  void load();
}

const dialog = ref(false);
const saving = ref(false);
const pct = ref(0);
// File = 新上传；string = 已绑定 OSS key（formBody 会转为 image 文本字段提交）
const imageFile = ref<File | string | null>(null);
const form = reactive<{ id: number; name: string; status: boolean; artistIds: number[]; categoryId: number }>({
  id: 0, name: "", status: true, artistIds: [], categoryId: 0,
});

function artistNames(row: Row): string {
  const map = new Map(artistOptions.value.map((a) => [a.id, a.name]));
  return row.artists.map((x) => map.get(x.artistId) ?? `#${x.artistId}`).join(", ");
}
function openCreate() {
  Object.assign(form, { id: 0, name: "", status: true, artistIds: [], categoryId: 0 });
  imageFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    status: row.status,
    artistIds: row.artists.map((a) => a.artistId),
    categoryId: row.category?.id ?? 0,
  });
  imageFile.value = row.image || null; // 回填当前绑定的 OSS 对象（空串归 null）
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    const body = formBody(
      {
        name: form.name,
        status: form.status ? 1 : 0,
        artist_ids: form.artistIds.join(","),
        category_id: form.categoryId, // 0 = 未分类，服务端映射 null
      },
      { image: imageFile.value },
    );
    await saveForm("/admin/albums", body, form.id || undefined, pct);
    dialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除专辑「${row.name}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/albums/${row.id}`);
  await load();
}

// 维度歌曲抽屉
const songsOpen = ref(false);
const drawerParent = ref<{ id: number; name: string } | null>(null);
function openSongs(row: Row) {
  drawerParent.value = { id: row.id, name: row.name };
  songsOpen.value = true;
}

onMounted(async () => {
  const [artists, categories] = await Promise.all([
    api.get("/admin/artists", { params: { size: 200 } }),
    api.get("/admin/categories", { params: { size: 200 } }),
  ]);
  artistOptions.value = artists.data.items;
  categoryOptions.value = categories.data.items;
});
load();
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
