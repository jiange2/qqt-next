<template>
  <el-container class="layout">
    <el-aside width="200px">
      <div class="brand">QQT 管理面板</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/songs">歌曲管理</el-menu-item>
        <el-menu-item index="/categories">分类管理</el-menu-item>
        <el-menu-item index="/artists">艺术家管理</el-menu-item>
        <el-menu-item index="/albums">专辑管理</el-menu-item>
        <el-menu-item index="/banners">横幅管理</el-menu-item>
        <el-menu-item index="/playlists">播放列表</el-menu-item>
        <el-menu-item index="/trending">热门榜</el-menu-item>
        <el-menu-item index="/users">用户管理</el-menu-item>
        <el-menu-item index="/reports">举报管理</el-menu-item>
        <el-menu-item index="/suggestions">歌曲建议</el-menu-item>
        <el-menu-item index="/notifications">消息推送</el-menu-item>
        <el-menu-item index="/settings">应用设置</el-menu-item>
        <el-menu-item index="/oss">OSS 管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { clearToken } from "../api";

const route = useRoute();
const router = useRouter();
// 退出登录入口放在菜单底部（简化：右键/后续可加按钮）
if (false) clearToken();
void router;
</script>

<style scoped>
.layout {
  height: 100vh;
}
.brand {
  font-weight: 600;
  text-align: center;
  padding: 16px 0;
  color: #409eff;
}
.el-menu {
  border-right: none;
}
</style>
