<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名 / 邮箱"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button @click="onSearch">搜索</el-button>
    </div>

    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="用户名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="phone" label="手机" width="130" />
      <el-table-column prop="userType" label="类型" width="100" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status ? 'success' : 'danger'">{{ row.status ? "正常" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" :type="row.status ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status ? "停用" : "启用" }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-model:current-page="page" :total="total" layout="total, prev, pager, next" @current-change="load" style="margin-top: 12px" />
  </div>
</template>

<script setup lang="ts">
import { ElMessageBox, ElMessage } from "element-plus";
import { api } from "../api";
import { usePagedList } from "../useList";

type Row = {
  id: number; userType: string; name: string; email: string;
  phone: string | null; status: boolean; createdAt: string;
};

const { items, total, page, keyword, loading, load } = usePagedList<Row>("/admin/users");

function onSearch() {
  page.value = 1;
  load();
}

async function toggle(row: Row) {
  await api.put(`/admin/users/${row.id}/status`, { status: !row.status });
  row.status = !row.status;
}

async function remove(row: Row) {
  await ElMessageBox.confirm(`确认删除用户「${row.name}」？其收藏 / 评分 / 举报等数据将一并删除。`, "删除", { type: "warning" });
  await api.delete(`/admin/users/${row.id}`);
  ElMessage.success("已删除");
  await load();
}

load();
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>
