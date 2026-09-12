// 客户端下载页只读端点：版本号展示（app-meta）与安装包跳转（apk）。
// 不是 App 接口——不经遗留协议门面，不适用 ADR 0001 的签名要求；数据源为 settings 单行（ADR 0012）。
import type { FastifyInstance } from "fastify";
import { getSettings } from "../services/settings.js";

export async function downloadRoutes(app: FastifyInstance): Promise<void> {
  // 静态页前缀补斜杠，保证页面内相对路径（app-meta / apk / icon.png）解析正确
  app.get("/download", (_req, reply) => reply.redirect("/download/"));

  app.get("/download/app-meta", async () => {
    const s = await getSettings();
    return { version: s.appNewVersion };
  });

  app.get("/download/apk", async (_req, reply) => {
    const s = await getSettings();
    const url = (s.appRedirectUrl ?? "").trim();
    if (!url) {
      return reply.code(503).type("text/plain; charset=utf-8").send("安装包地址尚未配置");
    }
    // 302（临时）+ no-store：后台改地址立即生效，不被浏览器/中间层缓存
    return reply.header("Cache-Control", "no-store").redirect(url);
  });
}
