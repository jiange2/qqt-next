<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索歌名" style="width: 200px" clearable @keyup.enter="search" />
      <el-select v-model="categoryId" placeholder="全部分类" clearable style="width: 160px">
        <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button type="success" @click="openCreate">新建歌曲</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="缩略图" width="80">
        <template #default="{ row }">
          <el-image v-if="row.thumbnail" :src="thumbUrl(row.thumbnail)" style="width: 48px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="歌名" min-width="140" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="分类" width="120">
        <template #default="{ row }">{{ row.category?.name }}</template>
      </el-table-column>
      <el-table-column label="专辑" width="120">
        <template #default="{ row }">{{ row.album?.name ?? "-" }}</template>
      </el-table-column>
      <el-table-column prop="totalViews" label="播放" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'info'">{{ row.status ? "上架" : "下架" }}</el-tag>
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

    <el-dialog v-model="dialog" :title="form.id ? '编辑歌曲' : '新建歌曲'" width="640px">
      <el-form label-width="90px">
        <el-form-item label="歌名"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="local">本地上传</el-radio>
            <el-radio value="youtube">YouTube</el-radio>
            <el-radio value="external">外链</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" filterable style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="专辑">
          <el-select v-model="form.albumId" filterable clearable style="width: 100%">
            <el-option v-for="a in albumOptions" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="艺术家">
          <el-select v-model="form.artistIds" multiple filterable style="width: 100%">
            <el-option v-for="a in artistOptions" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.type === 'local'" label="音频文件">
          <input type="file" accept="audio/*" @change="onAudio" />
          <span v-if="form.id" class="hint">留空保持原文件</span>
        </el-form-item>
        <el-form-item v-else label="音频地址"><el-input v-model="form.audioUrl" /></el-form-item>

        <el-form-item label="缩略图">
          <input type="file" accept="image/*" @change="onThumb" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="歌词文本"><el-input v-model="form.lrcText" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="歌词文件">
          <input type="file" accept=".lrc,text/plain" @change="onLrc" />
        </el-form-item>
        <el-form-item label="上架"><el-switch v-model="form.status" /></el-form-item>
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
import { ElMessageBox, ElMessage } from "element-plus";
import { api, formBody } from "../api";
import { usePagedList } from "../useList";
import { thumbUrl } from "../media";

type Row = {
  id: number; title: string; type: string; thumbnail: string; description: string;
  status: boolean; totalViews: number;
  categoryId: number; albumId: number | null; lrcText: string | null;
  category?: { id: number; name: string };
  album?: { id: number; name: string } | null;
  artists: { artistId: number }[];
};
type Opt = { id: number; name: string };

const { items, total, page, keyword, loading, load } = usePagedList<Row>("/admin/songs", () => ({
  category_id: categoryId.value,
}));
const categoryOptions = ref<Opt[]>([]);
const albumOptions = ref<Opt[]>([]);
const artistOptions = ref<Opt[]>([]);
const categoryId = ref<number | undefined>();

const dialog = ref(false);
const saving = ref(false);
const audioFile = ref<File | null>(null);
const thumbFile = ref<File | null>(null);
const lrcFile = ref<File | null>(null);
const form = reactive({
  id: 0, title: "", type: "local", categoryId: 0, albumId: null as number | null,
  artistIds: [] as number[], audioUrl: "", description: "", lrcText: "", status: true,
});

function search() {
  page.value = 1;
  void load();
}
function onAudio(e: Event) { audioFile.value = (e.target as HTMLInputElement).files?.[0] ?? null; }
function onThumb(e: Event) { thumbFile.value = (e.target as HTMLInputElement).files?.[0] ?? null; }
function onLrc(e: Event) { lrcFile.value = (e.target as HTMLInputElement).files?.[0] ?? null; }

function openCreate() {
  Object.assign(form, { id: 0, title: "", type: "local", categoryId: 0, albumId: null, artistIds: [], audioUrl: "", description: "", lrcText: "", status: true });
  audioFile.value = thumbFile.value = lrcFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, {
    id: row.id, title: row.title, type: row.type, categoryId: row.categoryId,
    albumId: row.albumId, artistIds: row.artists.map((a) => a.artistId),
    audioUrl: "", description: row.description, lrcText: row.lrcText ?? "", status: row.status,
  });
  audioFile.value = thumbFile.value = lrcFile.value = null;
  dialog.value = true;
}

async function save() {
  if (!form.title) return void ElMessage.warning("请填写歌名");
  if (!form.categoryId) return void ElMessage.warning("请选择分类");
  saving.value = true;
  try {
    const fields: Record<string, unknown> = {
      title: form.title, type: form.type, category_id: form.categoryId,
      artist_ids: form.artistIds.join(","), description: form.description,
      status: form.status ? 1 : 0,
    };
    if (form.type === "local" && form.id && !audioFile.value) fields["audio_url"] = "";
    if (form.type !== "local") fields["audio_url"] = form.audioUrl;
    if (form.albumId) fields["album_id"] = form.albumId;
    if (form.lrcText) fields["lrc_text"] = form.lrcText;
    const body = formBody(fields, { audio: audioFile.value, thumbnail: thumbFile.value, lrc: lrcFile.value });
    if (form.id) await api.put(`/admin/songs/${form.id}`, body);
    else await api.post("/admin/songs", body);
    dialog.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除歌曲「${row.title}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/songs/${row.id}`);
  await load();
}

onMounted(async () => {
  const [cats, albums, artists] = await Promise.all([
    api.get("/admin/categories", { params: { size: 200 } }),
    api.get("/admin/albums", { params: { size: 200 } }),
    api.get("/admin/artists", { params: { size: 200 } }),
  ]);
  categoryOptions.value = cats.data.items;
  albumOptions.value = albums.data.items;
  artistOptions.value = artists.data.items;
});
load();
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.hint { margin-left: 8px; color: #999; font-size: 12px; }
</style>
