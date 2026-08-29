import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发期转发到本地 server（生产由 app 托管静态产物；媒体在 OSS，无需代理）
      "/admin": "http://localhost:8000",
      "/api.php": "http://localhost:8000",
    },
  },
});
