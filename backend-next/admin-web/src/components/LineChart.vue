<template>
  <VChart :option="option" autoresize :style="{ height }" />
</template>

<script setup lang="ts">
import { use } from "echarts/core";
import { LineChart as ELineChart } from "echarts/charts";
import { GridComponent, LegendComponent, TooltipComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import VChart from "vue-echarts";
import type { EChartsOption } from "echarts";

use([ELineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

withDefaults(defineProps<{ option: EChartsOption; height?: string }>(), { height: "200px" });
</script>
