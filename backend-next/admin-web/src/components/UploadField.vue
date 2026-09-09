<template>
  <div class="upload-field">
    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="false"
      :accept="accept"
      :on-change="onChange"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
    </el-upload>
    <div v-if="modelValue" class="picked">
      <!-- string = 已绑定 OSS key：缩略图预览 + 可点击验证；替换只靠重新选择，不提供解绑 -->
      <template v-if="typeof modelValue === 'string'">
        <el-image
          v-if="isImage"
          :src="deobfSrc(thumbUrl(modelValue))"
          style="width: 48px; height: 48px"
          fit="cover"
        />
        <el-link :href="objectUrl(`${ossDir}/${modelValue}`)" target="_blank" type="primary">
          {{ decodedName || modelValue }}
        </el-link>
        <el-tag size="small" type="success">OSS 绑定</el-tag>
      </template>
      <span v-else class="picked-name">{{ modelValue.name }}</span>
      <el-button
        v-if="typeof modelValue !== 'string'"
        link
        type="danger"
        size="small"
        @click="emit('update:modelValue', null)"
      >
        移除
      </el-button>
    </div>
    <el-button size="small" class="pick-btn" @click="pickerVisible = true">从 OSS 选择</el-button>
    <OssPicker v-model="pickerVisible" :dir="ossDir" @select="onPick" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { UploadFile } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import { api } from "../api";
import { objectUrl, thumbUrl } from "../media";
import { deobfSrc } from "../deobf";
import OssPicker from "./OssPicker.vue";

const props = defineProps<{
  modelValue: File | string | null;
  accept?: string;
  /** 绑定目录（uploads | images | lrc），决定 OSS 选择器的作用域与链接拼接 */
  dir?: string;
}>();
const emit = defineEmits<{ (e: "update:modelValue", v: File | string | null): void }>();

const pickerVisible = ref(false);
const ossDir = computed(() => props.dir ?? "uploads");
// 图片类字段显示同名缩略图预览（images/ 与 images/thumbs/ 同名同源）
const isImage = computed(() => !!props.accept?.includes("image"));

function onChange(file: UploadFile) {
  emit("update:modelValue", (file.raw as File) ?? null);
}

// 绑定态（string）调服务端解密（ADR 0008）：链接文字显示原名，解不开回退 key 文件名
const decodedName = ref("");
watch(
  () => props.modelValue,
  async (v) => {
    decodedName.value = "";
    if (typeof v !== "string") return;
    try {
      const { data } = await api.get("/admin/oss/decode", { params: { name: v } });
      decodedName.value = data.originalName ?? "";
    } catch {
      /* 解密失败回退显示 key，不打断表单 */
    }
  },
  { immediate: true },
);

function onPick(name: string) {
  emit("update:modelValue", name);
}
</script>

<style scoped>
.upload-field {
  width: 100%;
}
.upload-field :deep(.el-upload),
.upload-field :deep(.el-upload-dragger) {
  width: 100%;
}
.upload-field :deep(.el-upload-dragger) {
  padding: 16px 0;
  height: auto;
}
.picked {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 13px;
}
.picked-name {
  color: #606266;
  word-break: break-all;
}
.picked .el-link {
  max-width: 420px;
}
.picked :deep(.el-link__inner) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick-btn {
  margin-top: 4px;
}
</style>
