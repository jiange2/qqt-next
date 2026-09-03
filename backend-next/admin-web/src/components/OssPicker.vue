<template>
  <el-dialog
    :model-value="modelValue"
    title="从 OSS 选择文件"
    width="860px"
    @update:model-value="emit('update:modelValue', $event as boolean)"
    @open="onOpen"
  >
    <div class="toolbar">
      <!-- 表单绑定场景（dir 已指定）锁定目录：绑定目录必须与显示目录一致，用户不可切换 -->
      <el-select
        v-model="oss.dir"
        style="width: 200px"
        :disabled="!!props.dir"
        @change="oss.refresh"
      >
        <el-option v-for="d in OSS_DIRS" :key="d.value" :value="d.value" :label="d.label" />
      </el-select>
      <!-- keyword 本地响应式过滤（同时匹配解密原名与 key），无需请求 -->
      <el-input
        v-model="oss.keyword"
        placeholder="按原名/文件名过滤"
        style="width: 220px"
        clearable
      />
    </div>

    <!-- 内部滚动容器：限高避免对话框过长，分页条在 OssGrid 内 -->
    <div class="picker-body">
      <OssGrid :oss="oss" mode="pick" @select="pick" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { OSS_DIRS, useOssList } from "../oss";
import OssGrid from "./OssGrid.vue";

const props = defineProps<{ modelValue: boolean; dir?: string }>();
const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "select", name: string): void;
}>();

const oss = useOssList("uploads");

// 每次打开重置到指定目录首页（缺省 uploads），避免残留上次浏览状态
function onOpen(): void {
  if (props.dir) oss.dir = props.dir;
  void oss.refresh();
}

/** 绑定语义是"业务字段只存文件名"，故回传相对当前目录的文件名 */
function pick(name: string): void {
  emit("select", name);
  emit("update:modelValue", false);
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.picker-body {
  max-height: 60vh;
  overflow: auto;
}
</style>
