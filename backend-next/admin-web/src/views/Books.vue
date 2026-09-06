<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索书名" style="width: 200px" clearable @keyup.enter="search" />
      <el-button type="primary" @click="search">搜索</el-button>
      <el-select
        v-model="catFilter"
        filterable
        clearable
        placeholder="全部分类"
        style="width: 180px"
        @change="search"
      >
        <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
      </el-select>
      <el-button type="success" @click="openCreate">新建书籍</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.cover" :src="bookThumbUrl(row.cover)" style="width: 48px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="书名" />
      <el-table-column prop="author" label="作者" width="140" />
      <el-table-column label="分类" width="120">
        <template #default="{ row }">{{ row.category?.name ?? "-" }}</template>
      </el-table-column>
      <el-table-column label="章节数" width="80">
        <template #default="{ row }">{{ row._count?.chapters ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{ row }">
          <el-button size="small" @click="openChapters(row)">章节</el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑书籍' : '新建书籍'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="书名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="作者"><el-input v-model="form.author" /></el-form-item>
        <el-form-item label="分类">
          <!-- 书籍必有分类，且只列书籍分类（服务端同样校验 type=book，ADR 0011） -->
          <el-select v-model="form.categoryId" filterable placeholder="请选择书籍分类" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" /></el-form-item>
        <el-form-item label="封面">
          <UploadField v-model="imageFile" accept="image/*" dir="images/books" />
        </el-form-item>
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
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { api, formBody, saveForm } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";
import { bookThumbUrl } from "../media";
import UploadField from "../components/UploadField.vue";

type Row = {
  id: number;
  name: string;
  author: string;
  cover: string;
  status: boolean;
  category: { id: number; name: string } | null;
  _count: { chapters: number };
};
type Opt = { id: number; name: string };

const router = useRouter();
const catFilter = ref<number>();
const { items, total, page, size, keyword, loading, load } = usePagedList<Row>("/admin/books", () => ({
  category_id: catFilter.value ?? "",
}));
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
const form = reactive<{ id: number; name: string; author: string; categoryId: number; status: boolean }>({
  id: 0, name: "", author: "", categoryId: 0, status: true,
});

function openCreate() {
  Object.assign(form, { id: 0, name: "", author: "", categoryId: 0, status: true });
  imageFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, {
    id: row.id,
    name: row.name,
    author: row.author,
    categoryId: row.category?.id ?? 0,
    status: row.status,
  });
  imageFile.value = row.cover || null; // 回填当前绑定的 OSS 对象（空串归 null）
  dialog.value = true;
}
async function save() {
  if (!form.categoryId) {
    ElMessage.warning("请选择书籍分类");
    return;
  }
  saving.value = true;
  try {
    const body = formBody(
      {
        name: form.name,
        author: form.author,
        category_id: form.categoryId,
        status: form.status ? 1 : 0,
      },
      { image: imageFile.value },
    );
    await saveForm("/admin/books", body, form.id || undefined, pct);
    dialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row: Row) {
  await ElMessageBox.confirm(
    `确认删除书籍「${row.name}」？其全部章节将一并删除。`,
    "删除",
    { type: "warning" },
  );
  await api.delete(`/admin/books/${row.id}`);
  await load();
}

// 章节管理二级页（book_id 与书名经 query 传递，供顶栏展示）
function openChapters(row: Row) {
  void router.push(`/books/${row.id}/chapters?name=${encodeURIComponent(row.name)}`);
}

onMounted(async () => {
  const categories = await api.get("/admin/categories", { params: { size: 200, type: "book" } });
  categoryOptions.value = categories.data.items;
});
load();
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
