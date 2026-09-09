<template>
  <div>
    <div class="toolbar">
      <el-button type="success" @click="openCreate">新建横幅</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <el-image v-if="row.image" :src="deobfSrc(imageUrl(row.image))" style="width: 80px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" />
      <el-table-column label="挂接歌曲" min-width="160">
        <template #default="{ row }">{{ songNames(row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
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

    <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑横幅' : '新建横幅'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="排序信息"><el-input v-model="form.sortInfo" /></el-form-item>
        <el-form-item label="图片">
          <UploadField v-model="imageFile" accept="image/*" dir="images" />
        </el-form-item>
        <el-form-item label="挂接歌曲">
          <el-select v-model="form.songIds" multiple filterable style="width: 100%">
            <el-option v-for="s in songOptions" :key="s.id" :value="s.id" :label="s.title" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.status" /></el-form-item>
      </el-form>
      <el-progress v-if="pct > 0 && pct < 100" :percentage="pct" style="margin-top: 4px" />
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
import { api, formBody, saveForm } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";
import { imageUrl } from "../media";
import { deobfSrc } from "../deobf";
import UploadField from "../components/UploadField.vue";

type Row = { id: number; title: string; image: string; status: boolean; songs: { songId: number }[] };

const { items, total, page, size, loading, load } = usePagedList<Row>("/admin/banners");
const songOptions = ref<{ id: number; title: string }[]>([]);

const dialog = ref(false);
const saving = ref(false);
const pct = ref(0);
// File = 新上传；string = 已绑定 OSS key（formBody 会转为 image 文本字段提交）
const imageFile = ref<File | string | null>(null);
const form = reactive<{ id: number; title: string; sortInfo: string; status: boolean; songIds: number[] }>({
  id: 0, title: "", sortInfo: "", status: true, songIds: [],
});

function songNames(row: Row): string {
  const map = new Map(songOptions.value.map((s) => [s.id, s.title]));
  return row.songs.map((x) => map.get(x.songId) ?? `#${x.songId}`).join(", ");
}
function openCreate() {
  Object.assign(form, { id: 0, title: "", sortInfo: "", status: true, songIds: [] });
  imageFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, {
    id: row.id, title: row.title, sortInfo: "", status: row.status,
    songIds: row.songs.map((s) => s.songId),
  });
  imageFile.value = row.image || null; // 回填当前绑定的 OSS 对象（空串归 null）
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    const body = formBody(
      { title: form.title, sort_info: form.sortInfo, status: form.status ? 1 : 0, song_ids: form.songIds.join(",") },
      { image: imageFile.value },
    );
    await saveForm("/admin/banners", body, form.id || undefined, pct);
    dialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除横幅「${row.title}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/banners/${row.id}`);
  await load();
}

onMounted(async () => {
  const { data } = await api.get("/admin/songs", { params: { size: 200 } });
  songOptions.value = data.items;
});
load();
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
