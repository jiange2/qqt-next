<template>
  <div style="max-width: 560px">
    <el-form label-width="90px">
      <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
      <el-form-item label="内容"><el-input v-model="form.message" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="跳转链接"><el-input v-model="form.url" placeholder="可选" /></el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="sending" @click="send">发送推送</el-button>
      </el-form-item>
    </el-form>
    <el-alert
      type="info"
      :closable="false"
      title="通过 OneSignal 向全部用户（All segment）推送；需先在「应用设置」中配置 OneSignal App ID 与 REST Key。"
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { api } from "../api";

const form = reactive({ title: "", message: "", url: "" });
const sending = ref(false);

async function send() {
  if (!form.title.trim() || !form.message.trim()) {
    ElMessage.warning("标题和内容不能为空");
    return;
  }
  await ElMessageBox.confirm(`确认向全部用户推送「${form.title}」？`, "发送推送", { type: "warning" });
  sending.value = true;
  try {
    await api.post("/admin/notifications", {
      title: form.title,
      message: form.message,
      url: form.url || undefined,
    });
    ElMessage.success("推送已提交");
    form.title = "";
    form.message = "";
    form.url = "";
  } finally {
    sending.value = false;
  }
}
</script>
