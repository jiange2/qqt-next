<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="pickSongId"
        filterable
        remote
        :remote-method="searchSongs"
        :loading="searching"
        placeholder="搜索歌曲加入热门榜"
        style="width: 320px"
        @change="addSong"
      >
        <el-option v-for="s in songOptions" :key="s.id" :value="s.id" :label="s.title" />
      </el-select>
      <el-button type="primary" :loading="saving" @click="save">保存榜单</el-button>
      <span class="hint">最多 50 首，顺序即展示顺序</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column label="#" width="60">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="song.id" label="ID" width="70" />
      <el-table-column prop="song.title" label="歌名" min-width="160" />
      <el-table-column label="分类" width="140">
        <template #default="{ row }">{{ row.song.category?.name ?? "-" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ $index }">
          <el-button size="small" :disabled="$index === 0" @click="move($index, -1)">上移</el-button>
          <el-button size="small" :disabled="$index === list.length - 1" @click="move($index, 1)">下移</el-button>
          <el-button size="small" type="danger" @click="removeAt($index)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";

type Item = { songId: number; song: { id: number; title: string; category?: { id: number; name: string } | null } };

const list = ref<Item[]>([]);
const loading = ref(false);
const saving = ref(false);
const searching = ref(false);
const pickSongId = ref<number | undefined>(undefined);
const songOptions = ref<{ id: number; title: string }[]>([]);

async function load() {
  loading.value = true;
  try {
    const { data } = await api.get("/admin/trending");
    list.value = data.items;
  } finally {
    loading.value = false;
  }
}

async function searchSongs(keyword: string) {
  searching.value = true;
  try {
    const { data } = await api.get("/admin/songs", { params: { keyword, size: 20 } });
    songOptions.value = data.items.map((s: { id: number; title: string }) => ({ id: s.id, title: s.title }));
  } finally {
    searching.value = false;
  }
}

function addSong(songId: number | undefined) {
  if (!songId) return;
  if (list.value.some((x) => x.songId === songId)) {
    ElMessage.warning("该歌曲已在榜单中");
    pickSongId.value = undefined;
    return;
  }
  const opt = songOptions.value.find((s) => s.id === songId);
  list.value.push({
    songId,
    song: { id: songId, title: opt?.title ?? `#${songId}`, category: null },
  });
  pickSongId.value = undefined;
}

function move(index: number, delta: number) {
  const target = index + delta;
  const [item] = list.value.splice(index, 1);
  list.value.splice(target, 0, item);
}

function removeAt(index: number) {
  list.value.splice(index, 1);
}

async function save() {
  saving.value = true;
  try {
    await api.put("/admin/trending", { song_ids: list.value.map((x) => x.songId) });
    ElMessage.success("榜单已保存");
    await load();
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  load();
  searchSongs("");
});
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.hint { color: #909399; font-size: 12px; }
</style>
