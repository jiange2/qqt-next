<template>
  <div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="songTitle" label="求歌名" width="160" />
      <el-table-column label="用户" width="180">
        <template #default="{ row }">{{ row.user?.name ?? "-" }}（{{ row.user?.email ?? "-" }}）</template>
      </el-table-column>
      <el-table-column prop="message" label="留言" min-width="240" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <AppPagination v-model:page="page" v-model:size="size" :total="total" @load="load" />
  </div>
</template>

<script setup lang="ts">
import { ElMessageBox } from "element-plus";
import { api } from "../api";
import { usePagedList } from "../useList";
import AppPagination from "../components/AppPagination.vue";

type Row = {
  id: number; songTitle: string; message: string; createdAt: string;
  user?: { id: number; name: string; email: string } | null;
};

const { items, total, page, size, loading, load } = usePagedList<Row>("/admin/suggestions");

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除求歌「${row.songTitle}」？`, "删除", { type: "warning" });
  await api.delete(`/admin/suggestions/${row.id}`);
  await load();
}

load();
</script>
