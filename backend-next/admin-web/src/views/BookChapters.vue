<template>
  <div>
    <div class="toolbar">
      <el-button @click="router.push('/books')">返回</el-button>
      <span class="book-title">《{{ bookName }}》章节管理</span>
      <span class="count">共 {{ chapters.length }} 章</span>
      <el-button type="warning" @click="pickTxt">导入 TXT</el-button>
      <el-button type="success" @click="openCreate">新增章节</el-button>
      <!-- 逐章录入通道：列表出于流量不带正文，编辑时单章回读 -->
      <input ref="fileRef" type="file" accept=".txt" style="display: none" @change="onTxtChange" />
    </div>

    <el-table :data="paged" v-loading="loading" stripe>
      <el-table-column label="#" width="70">
        <template #default="{ $index }">{{ (chapterPage - 1) * CHAPTER_PAGE_SIZE + $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="title" label="章标题" show-overflow-tooltip />
      <el-table-column label="字数" width="100">
        <template #default="{ row }">{{ row.contentLength }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 章节全量一次下发，前端分页足够 -->
    <el-pagination
      v-model:current-page="chapterPage"
      :page-size="CHAPTER_PAGE_SIZE"
      :total="chapters.length"
      layout="prev, pager, next, total"
      style="margin-top: 12px; justify-content: flex-end"
    />

    <!-- 导入 TXT：覆盖替换，必须经预览强确认（ADR 0011 单入口 + 单事务删旧写新） -->
    <el-dialog v-model="importDialog" title="导入 TXT — 预览" width="680px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`将删除该书现有 ${chapters.length} 章，写入切分出的 ${previewChapters.length} 章`"
        style="margin-bottom: 12px"
      />
      <el-table :data="previewChapters" max-height="360" size="small">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="title" label="章标题" show-overflow-tooltip />
        <el-table-column label="字数" width="90">
          <template #default="{ row }">{{ row.content.length }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="danger" :loading="importing" @click="doImport">确认导入（替换现有章节）</el-button>
      </template>
    </el-dialog>

    <!-- 单章编辑抽屉：纯文本正文（ADR 0011 无富文本） -->
    <el-drawer v-model="editDrawer" :title="editForm.id ? '编辑章节' : '新增章节'" size="50%">
      <el-form label-width="70px">
        <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="正文">
          <el-input v-model="editForm.content" type="textarea" :rows="22" placeholder="章节正文（纯文本）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDrawer = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveChapter">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";

type ChapterRow = { id: number; title: string; contentLength: number };
type SplitChapter = { title: string; content: string };

const route = useRoute();
const router = useRouter();
const bookId = Number(route.params.id);
const bookName = ref(String(route.query.name ?? ""));

const CHAPTER_PAGE_SIZE = 20;
const chapters = ref<ChapterRow[]>([]);
const loading = ref(false);
const chapterPage = ref(1);
const paged = computed(() =>
  chapters.value.slice((chapterPage.value - 1) * CHAPTER_PAGE_SIZE, chapterPage.value * CHAPTER_PAGE_SIZE),
);

async function loadChapters() {
  loading.value = true;
  try {
    const { data } = await api.get(`/admin/books/${bookId}/chapters`);
    chapters.value = data.items;
    // 删除末页章节后停在空页的兜底
    const maxPage = Math.max(1, Math.ceil(chapters.value.length / CHAPTER_PAGE_SIZE));
    if (chapterPage.value > maxPage) chapterPage.value = maxPage;
  } finally {
    loading.value = false;
  }
}

// ---- 导入 TXT：选文件 → 服务端切分预览（不入库）→ 强确认 → 单事务删旧写新
const fileRef = ref<HTMLInputElement | null>(null);
const importDialog = ref(false);
const importing = ref(false);
const previewChapters = ref<SplitChapter[]>([]);

function pickTxt() {
  fileRef.value?.click();
}

async function onTxtChange(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = ""; // 允许重选同一文件
  if (!file) return;
  try {
    const fd = new FormData();
    fd.append("file", file);
    const { data } = await api.post(`/admin/books/${bookId}/txt-preview`, fd);
    previewChapters.value = data.chapters;
    chapterPage.value = 1;
    importDialog.value = true;
  } catch {
    ElMessage.error("切分失败，请检查文件（TXT 上限 50MB）");
  }
}

async function doImport() {
  importing.value = true;
  try {
    await api.post(`/admin/books/${bookId}/txt-import`, { chapters: previewChapters.value });
    importDialog.value = false;
    ElMessage.success(`已导入 ${previewChapters.value.length} 章`);
    await loadChapters();
  } finally {
    importing.value = false;
  }
}

// ---- 新增 / 编辑抽屉
const editDrawer = ref(false);
const saving = ref(false);
const editForm = reactive<{ id: number; title: string; content: string }>({ id: 0, title: "", content: "" });

function openCreate() {
  Object.assign(editForm, { id: 0, title: "", content: "" });
  editDrawer.value = true;
}

async function openEdit(row: ChapterRow) {
  try {
    const { data } = await api.get(`/admin/chapters/${row.id}`);
    Object.assign(editForm, { id: data.id, title: data.title, content: data.content });
    editDrawer.value = true;
  } catch {
    ElMessage.error("章节加载失败");
  }
}

async function saveChapter() {
  if (!editForm.title.trim()) {
    ElMessage.warning("请填写章标题");
    return;
  }
  saving.value = true;
  try {
    if (editForm.id) {
      await api.put(`/admin/chapters/${editForm.id}`, {
        title: editForm.title,
        content: editForm.content,
      });
    } else {
      await api.post(`/admin/books/${bookId}/chapters`, {
        title: editForm.title,
        content: editForm.content,
      });
      chapterPage.value = Math.ceil((chapters.value.length + 1) / CHAPTER_PAGE_SIZE);
    }
    editDrawer.value = false;
    await loadChapters();
  } finally {
    saving.value = false;
  }
}

async function remove(row: ChapterRow) {
  await ElMessageBox.confirm(`确认删除章节「${row.title}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/chapters/${row.id}`);
  await loadChapters();
}

onMounted(async () => {
  // 顶栏书名兜底：query 缺失（如刷新丢失）时回读书籍信息
  if (!bookName.value) {
    try {
      bookName.value = (await api.get(`/admin/books/${bookId}`)).data.name;
    } catch {
      /* 书不存在时列表接口会报错，页面保持可返回 */
    }
  }
  await loadChapters();
});
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.book-title { font-weight: 600; font-size: 16px; }
.count { color: #909399; font-size: 13px; margin-right: auto; }
</style>
