<template>
  <div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="歌曲" width="160">
        <template #default="{ row }">{{ row.song?.title ?? `#${row.songId}` }}</template>
      </el-table-column>
      <el-table-column label="举报用户" width="180">
        <template #default="{ row }">{{ row.user?.name ?? "-" }}（{{ row.user?.email ?? "-" }}）</template>
      </el-table-column>
      <el-table-column prop="report" label="举报内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-model:current-page="page" :total="total" layout="total, prev, pager, next" @current-change="load" style="margin-top: 12px" />
  </div>
</template>

<script setup lang="ts">
import { ElMessageBox } from "element-plus";
import { api } from "../api";
import { usePagedList } from "../useList";

type Row = {
  id: number; songId: number; report: string; createdAt: string;
  song?: { id: number; title: string } | null;
  user?: { id: number; name: string; email: string } | null;
};

const { items, total, page, loading, load } = usePagedList<Row>("/admin/reports");

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除举报 #${row.id}？`, "删除", { type: "warning" });
  await api.delete(`/admin/reports/${row.id}`);
  await load();
}

load();
</script>
