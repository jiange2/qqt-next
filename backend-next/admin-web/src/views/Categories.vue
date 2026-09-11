<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索名称" style="width: 220px" clearable @keyup.enter="search" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-select v-model="typeFilter" clearable placeholder="全部类型" style="width: 140px" @change="search">
        <el-option label="音乐分类" value="music" />
        <el-option label="书籍分类" value="book" />
      </el-select>
      <el-button type="success" @click="openCreate">新建分类</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="图片" width="80">
        <template #default="{ row }">
          <el-image v-if="row.image" :src="deobfSrc(thumbUrl(row.image))" style="width: 48px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="row.type === 'book' ? 'warning' : 'primary'">{{ row.type === "book" ? "书籍" : "音乐" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="隐私" width="70">
        <template #default="{ row }">
          <el-tag :type="row.isPrivate ? 'warning' : 'success'" size="small">{{ row.isPrivate ? "隐私" : "公开" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{ row }">
          <el-button size="small" @click="openDimension(row)">{{ row.type === "book" ? "书籍" : "专辑" }}</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑分类' : '新建分类'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <!-- 类型创建后不可改（书籍阅读域 ADR 0011）：编辑时禁用 -->
          <el-radio-group v-model="form.type" :disabled="!!form.id">
            <el-radio value="music">音乐</el-radio>
            <el-radio value="book">书籍</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="隐私">
          <el-switch v-model="form.isPrivate" active-text="隐私" inactive-text="公开" />
        </el-form-item>
        <el-form-item label="图片">
          <UploadField v-model="imageFile" accept="image/*" dir="images/thumbs" />
        </el-form-item>
      </el-form>
      <el-progress v-if="pct > 0 && pct < 100" :percentage="pct" style="margin-top: 4px" />
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分类内专辑/书籍维度抽屉：按分类类型分流（backend-next ADR 0009 / 0011） -->
    <DimensionDrawer v-if="drawerParent" v-model="songsOpen" :kind="drawerKind" :parent="drawerParent" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessageBox } from "element-plus";
import { api, formBody, saveForm } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";
import { thumbUrl } from "../media";
import { deobfSrc } from "../deobf";
import UploadField from "../components/UploadField.vue";
import DimensionDrawer from "../components/DimensionDrawer.vue";

type Row = { id: number; name: string; type: string; image: string; status: boolean; isPrivate: boolean };

// 类型筛选（书籍阅读域 ADR 0011）：音乐/书籍分类分流管理
const typeFilter = ref<string>();
const { items, total, page, size, keyword, loading, load } = usePagedList<Row>("/admin/categories", () => ({
  type: typeFilter.value || undefined,
}));

const dialog = ref(false);
const saving = ref(false);
const pct = ref(0);
// File = 新上传；string = 已绑定 OSS key（formBody 会转为 image 文本字段提交）
const imageFile = ref<File | string | null>(null);
const form = reactive<{ id: number; name: string; type: string; status: boolean; isPrivate: boolean }>({
  id: 0, name: "", type: "music", status: true, isPrivate: true,
});

function search() {
  page.value = 1;
  void load();
}

function openCreate() {
  Object.assign(form, { id: 0, name: "", type: "music", status: true, isPrivate: true });
  imageFile.value = null;
  dialog.value = true;
}

function openEdit(row: Row) {
  Object.assign(form, { id: row.id, name: row.name, type: row.type, status: row.status, isPrivate: row.isPrivate });
  imageFile.value = row.image || null; // 回填当前绑定的 OSS 对象（空串归 null）
  dialog.value = true;
}

async function save() {
  saving.value = true;
  try {
    // type 编辑时也传：服务端编辑路径不触碰 type（类型仅创建可定，ADR 0011）
    const body = formBody(
      { name: form.name, type: form.type, status: form.status ? 1 : 0, is_private: form.isPrivate ? 1 : 0 },
      { image: imageFile.value },
    );
    await saveForm("/admin/categories", body, form.id || undefined, pct);
    dialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/categories/${row.id}`);
  await load();
}

// 分类内专辑/书籍维度抽屉：按分类类型分流（ADR 0009 / 0011）
const songsOpen = ref(false);
const drawerKind = ref<"categories" | "books">("categories");
const drawerParent = ref<{ id: number; name: string } | null>(null);
function openDimension(row: Row) {
  drawerKind.value = row.type === "book" ? "books" : "categories";
  drawerParent.value = { id: row.id, name: row.name };
  songsOpen.value = true;
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
