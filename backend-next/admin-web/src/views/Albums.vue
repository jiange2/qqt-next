<template>
  <div>
    <div class="toolbar">
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
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-model:current-page="page" :total="total" layout="total, prev, pager, next" @current-change="load" style="margin-top: 12px" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑专辑' : '新建专辑'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="艺术家">
          <el-select v-model="form.artistIds" multiple filterable style="width: 100%">
            <el-option v-for="a in artistOptions" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" /></el-form-item>
        <el-form-item label="封面">
          <input type="file" accept="image/*" @change="onFile" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { api, formBody } from "../api";
import { usePagedList } from "../useList";
import { thumbUrl } from "../media";

type Row = { id: number; name: string; image: string; status: boolean; artists: { artistId: number }[] };
type Opt = { id: number; name: string };

const { items, total, page, loading, load } = usePagedList<Row>("/admin/albums");
const artistOptions = ref<Opt[]>([]);

const dialog = ref(false);
const saving = ref(false);
const imageFile = ref<File | null>(null);
const form = reactive<{ id: number; name: string; status: boolean; artistIds: number[] }>({
  id: 0, name: "", status: true, artistIds: [],
});

function artistNames(row: Row): string {
  const map = new Map(artistOptions.value.map((a) => [a.id, a.name]));
  return row.artists.map((x) => map.get(x.artistId) ?? `#${x.artistId}`).join(", ");
}
function onFile(e: Event) {
  imageFile.value = (e.target as HTMLInputElement).files?.[0] ?? null;
}
function openCreate() {
  Object.assign(form, { id: 0, name: "", status: true, artistIds: [] });
  imageFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    status: row.status,
    artistIds: row.artists.map((a) => a.artistId),
  });
  imageFile.value = null;
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    const body = formBody(
      { name: form.name, status: form.status ? 1 : 0, artist_ids: form.artistIds.join(",") },
      { image: imageFile.value },
    );
    if (form.id) await api.put(`/admin/albums/${form.id}`, body);
    else await api.post("/admin/albums", body);
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

onMounted(async () => {
  const { data } = await api.get("/admin/artists", { params: { size: 200 } });
  artistOptions.value = data.items;
});
load();
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
