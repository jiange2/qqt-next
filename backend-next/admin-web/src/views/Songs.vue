<template>
  <div>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索歌名" style="width: 200px" clearable @keyup.enter="search" />
      <el-select v-model="categoryId" placeholder="全部分类" clearable style="width: 160px">
        <el-option :value="0" label="未分类" />
        <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
      </el-select>
      <el-select v-model="albumId" placeholder="全部专辑" clearable filterable style="width: 160px">
        <el-option :value="0" label="未归专辑" />
        <el-option v-for="a in albumOptions" :key="a.id" :value="a.id" :label="a.name" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button type="success" @click="openCreate">新建歌曲</el-button>
      <el-button type="primary" plain :disabled="selected.length === 0" @click="openBatchCover">批量绑定封面（{{ selected.length }}）</el-button>
      <el-button type="primary" plain :disabled="selected.length === 0" @click="openBatchCategory">批量修改分类（{{ selected.length }}）</el-button>
      <el-button type="primary" plain :disabled="selected.length === 0" @click="openBatchAlbum">批量修改专辑（{{ selected.length }}）</el-button>
      <el-button type="warning" plain :disabled="selected.length === 0" @click="batchSetPrivate(true)">批量设为隐私（{{ selected.length }}）</el-button>
      <el-button type="success" plain :disabled="selected.length === 0" @click="batchSetPrivate(false)">批量取消隐私（{{ selected.length }}）</el-button>
      <el-button
        type="warning"
        :disabled="selectedExternal.length === 0"
        :loading="transferring"
        @click="transferSelected"
      >转入 OSS（{{ selectedExternal.length }}）</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe @selection-change="onSelectionChange">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="缩略图" width="80">
        <template #default="{ row }">
          <el-image v-if="row.thumbnail" :src="deobfSrc(thumbUrl(row.thumbnail))" style="width: 48px" fit="cover" />
        </template>
      </el-table-column>
      <el-table-column prop="title" label="歌名" min-width="140" />
      <el-table-column prop="type" label="类型" width="90" />
      <el-table-column label="分类" width="120">
        <template #default="{ row }">{{ row.category?.name ?? "未分类" }}</template>
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
      <el-table-column label="隐私" width="70">
        <template #default="{ row }">
          <el-tag :type="row.isPrivate ? 'warning' : 'success'" size="small">{{ row.isPrivate ? "隐私" : "公开" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.type === 'external'"
            size="small"
            type="warning"
            :disabled="transferring"
            @click="transferSingle(row)"
          >转入 OSS</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />

    <el-dialog v-model="transferDialog" title="批量转入 OSS" width="420px" :close-on-click-modal="false">
      <p>正在下载并上传 {{ transferTotal }} 首歌曲，请勿关闭…</p>
      <el-progress :percentage="transferPct" />
      <p v-if="transferFail.length" class="hint">失败 {{ transferFail.length }} 首：{{ transferFail.map((s) => s.title).join("、") }}（保持外链，可重试）</p>
    </el-dialog>

    <el-dialog v-model="batchCoverDialog" title="批量绑定封面" width="480px" :close-on-click-modal="false">
      <p class="hint">将为选中的 {{ selected.length }} 首歌曲设置同一张封面（共享同一文件，不各自复制）。</p>
      <el-form label-width="90px" style="margin-top: 12px">
        <el-form-item label="封面">
          <UploadField v-model="batchCoverFile" accept="image/*" dir="images/thumbs" />
        </el-form-item>
      </el-form>
      <el-progress v-if="batchPct > 0 && batchPct < 100" :percentage="batchPct" />
      <template #footer>
        <el-button @click="batchCoverDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="saveBatchCover">应用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchCatDialog" title="批量修改分类" width="420px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="目标分类">
          <el-select v-model="batchCategoryId" filterable style="width: 100%">
            <el-option :value="0" label="未分类" />
            <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchCatDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="saveBatchCategory">应用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchAlbumDialog" title="批量修改专辑" width="420px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="目标专辑">
          <el-select v-model="batchAlbumId" filterable style="width: 100%">
            <el-option :value="0" label="未归专辑" />
            <el-option v-for="a in albumOptions" :key="a.id" :value="a.id" :label="a.name" />
          </el-select>
          <div class="hint">归入专辑要求歌曲已属于某分类（未分类歌曲会被整体拒绝）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchAlbumDialog = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="saveBatchAlbum">应用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="form.id ? '编辑歌曲' : '新建歌曲'" width="640px">
      <el-form label-width="90px">
        <el-form-item label="歌名"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio value="local">本地上传</el-radio>
            <el-radio value="external">外链</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" filterable clearable placeholder="未分类" style="width: 100%">
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
          <UploadField v-model="audioFile" accept="audio/*" dir="uploads" />
        </el-form-item>
        <el-form-item v-else label="音频地址"><el-input v-model="form.audioUrl" /></el-form-item>

        <el-form-item label="缩略图">
          <UploadField v-model="thumbFile" accept="image/*" dir="images/thumbs" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="歌词文本"><el-input v-model="form.lrcText" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="歌词文件">
          <UploadField v-model="lrcFile" accept=".lrc,text/plain" dir="lrc" />
        </el-form-item>
        <el-form-item label="上架"><el-switch v-model="form.status" /></el-form-item>
        <el-form-item label="隐私"><el-switch v-model="form.isPrivate" /></el-form-item>
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
import { ElMessageBox, ElMessage } from "element-plus";
import { api, formBody, saveForm } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";
import { thumbUrl } from "../media";
import { deobfSrc } from "../deobf";
import UploadField from "../components/UploadField.vue";

type Row = {
  id: number; title: string; type: string; thumbnail: string; description: string;
  audioUrl: string; lrcUrl: string | null; status: boolean; totalViews: number;
  categoryId: number | null; albumId: number | null; lrcText: string | null;
  category?: { id: number; name: string } | null;
  album?: { id: number; name: string } | null;
  artists: { artistId: number }[];
  isPrivate: boolean;
};
type Opt = { id: number; name: string };

const { items, total, page, size, keyword, loading, load } = usePagedList<Row>("/admin/songs", () => ({
  category_id: categoryId.value,
  album_id: albumId.value,
}));
const categoryOptions = ref<Opt[]>([]);
const albumOptions = ref<Opt[]>([]);
const artistOptions = ref<Opt[]>([]);
const categoryId = ref<number | undefined>();
const albumId = ref<number | undefined>();

const dialog = ref(false);
const saving = ref(false);
const pct = ref(0);
// File = 新上传；string = 已绑定 OSS key（音频/lrc 需映射到 audio_url/lrc_url 文本字段，thumbnail 同名直传）
const audioFile = ref<File | string | null>(null);
const thumbFile = ref<File | string | null>(null);
const lrcFile = ref<File | string | null>(null);
const form = reactive({
  id: 0, title: "", type: "local", categoryId: null as number | null, albumId: null as number | null,
  artistIds: [] as number[], audioUrl: "", description: "", lrcText: "", status: true, isPrivate: true,
});

// ---- 转入 OSS（ADR 0006）：单首直接调端点，批量由前端逐首驱动以展示进度
const selected = ref<Row[]>([]);
const selectedExternal = ref<Row[]>([]);
const transferring = ref(false);
const transferDialog = ref(false);
const transferTotal = ref(0);
const transferPct = ref(0);
const transferFail = ref<{ id: number; title: string }[]>([]);

function onSelectionChange(rows: Row[]): void {
  selected.value = rows;
  selectedExternal.value = rows.filter((r) => r.type === "external");
}

// ---- 批量操作：绑定封面（共享 media key，不复制文件）/ 修改分类；仅作用当前页勾选
const batchCoverDialog = ref(false);
const batchCatDialog = ref(false);
const batchSaving = ref(false);
const batchPct = ref(0);
// File = 新上传（先经 OSS 直传拿 key 再批量绑定）；string = 已绑定 OSS key
const batchCoverFile = ref<File | string | null>(null);
const batchCategoryId = ref<number | undefined>();
const batchAlbumDialog = ref(false);
const batchAlbumId = ref<number | undefined>();

function openBatchCover(): void {
  batchCoverFile.value = null;
  batchPct.value = 0;
  batchCoverDialog.value = true;
}

async function saveBatchCover(): Promise<void> {
  if (!batchCoverFile.value) return void ElMessage.warning("请先上传或从 OSS 选择封面");
  batchSaving.value = true;
  try {
    let thumbnail: string;
    if (typeof batchCoverFile.value === "string") {
      thumbnail = batchCoverFile.value; // 绑定已有 OSS 对象，直接引用同一 key
    } else {
      const fd = new FormData();
      fd.append("file", batchCoverFile.value);
      const res = await api.post<{ name: string }>("/admin/oss/upload?dir=images", fd, {
        onUploadProgress: (e: { loaded: number; total?: number }) => {
          batchPct.value = e.total ? Math.round((e.loaded / e.total) * 100) : 0;
        },
      });
      thumbnail = res.data.name;
      batchPct.value = 0;
    }
    await api.patch("/admin/songs/batch", { ids: selected.value.map((r) => r.id), thumbnail });
    batchCoverDialog.value = false;
    ElMessage.success(`已为 ${selected.value.length} 首歌曲绑定封面`);
    await load();
  } finally {
    batchSaving.value = false;
  }
}

function openBatchCategory(): void {
  batchCategoryId.value = undefined;
  batchCatDialog.value = true;
}

async function saveBatchCategory(): Promise<void> {
  if (!batchCategoryId.value) return void ElMessage.warning("请选择目标分类");
  const target = batchCategoryId.value;
  batchSaving.value = true;
  try {
    await api.patch("/admin/songs/batch", {
      ids: selected.value.map((r) => r.id),
      categoryId: target > 0 ? target : null, // 0 = 未分类
    });
    batchCatDialog.value = false;
    ElMessage.success(`已修改 ${selected.value.length} 首歌曲分类`);
    await load();
  } finally {
    batchSaving.value = false;
  }
}

function openBatchAlbum(): void {
  batchAlbumId.value = undefined;
  batchAlbumDialog.value = true;
}

async function saveBatchAlbum(): Promise<void> {
  if (!batchAlbumId.value) return void ElMessage.warning("请选择目标专辑");
  const target = batchAlbumId.value;
  batchSaving.value = true;
  try {
    await api.patch("/admin/songs/batch", {
      ids: selected.value.map((r) => r.id),
      albumId: target > 0 ? target : null, // 0 = 未归专辑
    });
    batchAlbumDialog.value = false;
    ElMessage.success(`已修改 ${selected.value.length} 首歌曲专辑`);
    await load();
  } finally {
    batchSaving.value = false;
  }
}

async function batchSetPrivate(isPrivate: boolean): Promise<void> {
  const label = isPrivate ? "设为隐私" : "取消隐私";
  batchSaving.value = true;
  try {
    await api.patch("/admin/songs/batch", {
      ids: selected.value.map((r) => r.id),
      isPrivate,
    });
    ElMessage.success(`已${label} ${selected.value.length} 首歌曲`);
    await load();
  } finally {
    batchSaving.value = false;
  }
}

async function transferOne(row: Row): Promise<boolean> {
  try {
    await api.post(`/admin/songs/${row.id}/to-oss`);
    return true;
  } catch {
    return false; // 错误详情已由 api.ts 拦截器 toast
  }
}

async function transferSingle(row: Row): Promise<void> {
  transferring.value = true;
  const ok = await transferOne(row);
  transferring.value = false;
  if (ok) ElMessage.success(`「${row.title}」已转入 OSS`);
  await load();
}

async function transferSelected(): Promise<void> {
  const targets = selectedExternal.value;
  if (targets.length === 0) return;
  await ElMessageBox.confirm(
    `将下载并上传选中的 ${targets.length} 首外链歌曲到 OSS（原名，冲突自动加后缀），继续？`,
    "转入 OSS",
    { type: "warning" },
  );
  transferring.value = true;
  transferDialog.value = true;
  transferTotal.value = targets.length;
  transferPct.value = 0;
  transferFail.value = [];
  for (const [i, row] of targets.entries()) {
    if (!(await transferOne(row))) transferFail.value.push({ id: row.id, title: row.title });
    transferPct.value = Math.round(((i + 1) / targets.length) * 100);
  }
  transferring.value = false;
  if (transferFail.value.length === 0) {
    transferDialog.value = false;
    ElMessage.success(`全部 ${targets.length} 首已转入 OSS`);
  }
  await load();
}

function search() {
  page.value = 1;
  void load();
}

function openCreate() {
  Object.assign(form, { id: 0, title: "", type: "local", categoryId: null, albumId: null, artistIds: [], audioUrl: "", description: "", lrcText: "", status: true });
  audioFile.value = thumbFile.value = lrcFile.value = null;
  dialog.value = true;
}
function openEdit(row: Row) {
  Object.assign(form, {
    id: row.id, title: row.title, type: row.type, categoryId: row.categoryId ?? null,
    albumId: row.albumId, artistIds: row.artists.map((a) => a.artistId),
    audioUrl: "", description: row.description, lrcText: row.lrcText ?? "", status: row.status, isPrivate: row.isPrivate,
  });
  // 回填当前绑定值（DB 存的文件名即媒体 Key），空串归 null；未改动保存为同值幂等提交
  audioFile.value = row.type === "local" ? row.audioUrl || null : null;
  thumbFile.value = row.thumbnail || null;
  lrcFile.value = row.lrcUrl || null;
  dialog.value = true;
}

async function save() {
  if (!form.title) return void ElMessage.warning("请填写歌名");
  // 分类可空（backend-next ADR 0007）：不选分类即未分类；但专辑歌曲须先有分类
  if (form.albumId && !form.categoryId) return void ElMessage.warning("专辑歌曲须先选择分类");
  saving.value = true;
  try {
    const fields: Record<string, unknown> = {
      title: form.title, type: form.type, category_id: form.categoryId ?? 0,
      artist_ids: form.artistIds.join(","), description: form.description,
      status: form.status ? 1 : 0, is_private: form.isPrivate ? 1 : 0,
    };
    if (form.type !== "local") fields["audio_url"] = form.audioUrl;
    else if (typeof audioFile.value === "string") fields["audio_url"] = audioFile.value; // 绑定已有 OSS 音频
    if (typeof lrcFile.value === "string") fields["lrc_url"] = lrcFile.value; // 绑定已有 OSS 歌词
    if (form.albumId) fields["album_id"] = form.albumId;
    if (form.lrcText) fields["lrc_text"] = form.lrcText;
    const body = formBody(fields, {
      audio: typeof audioFile.value === "string" ? null : audioFile.value,
      thumbnail: thumbFile.value,
      lrc: typeof lrcFile.value === "string" ? null : lrcFile.value,
    });
    await saveForm("/admin/songs", body, form.id || undefined, pct);
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
.hint { margin-top: 4px; color: #999; font-size: 12px; }
</style>
