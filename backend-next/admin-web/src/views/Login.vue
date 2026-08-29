<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>QQT 管理面板</h2>
      <el-form @submit.prevent="onLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { api, setToken } from "../api";

const username = ref("");
const password = ref("");
const loading = ref(false);
const router = useRouter();

async function onLogin() {
  loading.value = true;
  try {
    const { data } = await api.post("/admin/login", {
      username: username.value,
      password: password.value,
    });
    setToken(data.token);
    router.push("/");
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}
void ElMessage;
</script>

<style scoped>
.login-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f0f2f5;
}
.login-card {
  width: 360px;
}
.login-card h2 {
  text-align: center;
  margin-bottom: 24px;
}
</style>
