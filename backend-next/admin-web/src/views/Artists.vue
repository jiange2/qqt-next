<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索名称" style="width: 220px" clearable @keyup.enter="search" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button type="success" @click="openCreate">新建艺术家</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <el-image v-if="row.image" :src="thumbUrl(row.image)" style="width: 48px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load"
      style="margin-top: 12px"
    />

    <el-dialog v-model="dialog" :title="form.id ? '编辑艺术家' : '新建艺术家'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="图片">
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
import { reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { api, formBody } from "../api";
import { usePagedList } from "../useList";
import { thumbUrl } from "../media";

type Row = { id: number; name: string; image: string };

const { items, total, page, keyword, loading, load } = usePagedList<Row>("/admin/artists");

const dialog = ref(false);
const saving = ref(false);
const imageFile = ref<File | null>(null);
const form = reactive<{ id: number; name: string }>({ id: 0, name: "" });

function search() {
  page.value = 1;
  void load();
}
function onFile(e: Event) {
  imageFile.value = (e.target as HTMLInputElement).files?.[0] ?? null;
}
function openCreate() {
  Object.assign(form, { id: 0, name: "" });
  imageFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, { id: row.id, name: row.name });
  imageFile.value = null;
  dialog.value = true;
}
async function save() {
  saving.value = true;
  try {
    const body = formBody({ name: form.name }, { image: imageFile.value });
    if (form.id) await api.put(`/admin/artists/${form.id}`, body);
    else await api.post("/admin/artists", body);
    dialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除艺术家「${row.name}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/artists/${row.id}`);
  await load();
}

load();
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
