<template>
  <el-container class="layout">
    <el-aside width="200px">
      <div class="brand">QQT 管理面板</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/songs">歌曲管理</el-menu-item>
        <el-menu-item index="/categories">分类管理</el-menu-item>
        <el-menu-item index="/artists">艺术家管理</el-menu-item>
        <el-menu-item index="/albums">专辑管理</el-menu-item>
        <el-menu-item index="/books">书籍管理</el-menu-item>
        <el-menu-item index="/banners">横幅管理</el-menu-item>
        <el-menu-item index="/playlists">播放列表</el-menu-item>
        <el-menu-item index="/trending">热门榜</el-menu-item>
        <el-menu-item index="/users">用户管理</el-menu-item>
        <el-menu-item index="/reports">举报管理</el-menu-item>
        <el-menu-item index="/suggestions">歌曲建议</el-menu-item>
        <el-menu-item index="/stats">数据统计</el-menu-item>
        <el-menu-item index="/notifications">消息推送</el-menu-item>
        <el-menu-item index="/settings">应用设置</el-menu-item>
        <el-menu-item index="/oss">OSS 管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header height="48px" class="topbar">
        <el-dropdown @command="onAccountCommand">
          <span class="account">
            {{ username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="pwdDialog" title="修改密码" width="420px" :close-on-click-modal="false">
    <el-form label-width="100px">
      <el-form-item label="当前密码">
        <el-input v-model="pwd.old" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="pwd.next" type="password" show-password placeholder="至少 8 位" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="pwd.confirm" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdDialog = false">取消</el-button>
      <el-button type="primary" :loading="pwdLoading" @click="submitPassword">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowDown } from "@element-plus/icons-vue";
import { api, clearToken, getUsername } from "../api";

const route = useRoute();
const router = useRouter();
const username = getUsername();

const pwdDialog = ref(false);
const pwdLoading = ref(false);
const pwd = ref({ old: "", next: "", confirm: "" });

function onAccountCommand(command: string): void {
  if (command === "logout") {
    clearToken();
    router.replace("/login");
    return;
  }
  pwd.value = { old: "", next: "", confirm: "" };
  pwdDialog.value = true;
}

async function submitPassword(): Promise<void> {
  if (!pwd.value.old) return void ElMessage.warning("请输入当前密码");
  if (pwd.value.next.length < 8) return void ElMessage.warning("新密码至少 8 位");
  if (pwd.value.next !== pwd.value.confirm) return void ElMessage.warning("两次输入的新密码不一致");
  pwdLoading.value = true;
  try {
    await api.post("/admin/password", {
      old_password: pwd.value.old,
      new_password: pwd.value.next,
    });
    pwdDialog.value = false;
    ElMessage.success("密码已修改，请重新登录");
    // 服务端不吊销已签发 JWT，改密后主动清 token 重登，避免旧 token 继续有效 7 天
    clearToken();
    router.replace("/login");
  } catch {
    // 拦截器已提示（当前密码错误等）
  } finally {
    pwdLoading.value = false;
  }
}
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
.topbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  border-bottom: 1px solid var(--el-border-color-light);
}
.account {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
  color: var(--el-text-color-primary);
}
</style>
