<template>
  <el-pagination
    v-model:current-page="page"
    v-model:page-size="size"
    :total="total"
    :page-sizes="[10, 20, 50, 100, 200, 1000]"
    layout="total, sizes, prev, pager, next, jumper"
    @current-change="scheduleLoad"
    @size-change="onSizeChange"
    style="margin-top: 12px"
  />
</template>

<script setup lang="ts">
// 列表页通用分页条：统一页大小选项与事件去重，改页大小时重置回第 1 页
const page = defineModel<number>("page", { required: true });
const size = defineModel<number>("size", { required: true });
defineProps<{ total: number }>();
const emit = defineEmits<{ load: [] }>();

let timer: ReturnType<typeof setTimeout> | undefined;
// size-change 与 current-change 可能同一帧连发，合并为一次加载
function scheduleLoad() {
  clearTimeout(timer);
  timer = setTimeout(() => emit("load"), 0);
}

function onSizeChange() {
  page.value = 1;
  scheduleLoad();
}
</script>
