// 客户端下载页只读端点：版本号展示（app-meta）与安装包跳转（apk）。
// 不是 App 接口——不经遗留协议门面，不适用 ADR 0001 的签名要求；数据源为 settings 单行（ADR 0012）。
// 下载二维码令牌入口 /download/q/:token 见仓库级 ADR 0013（落点页与下载跳转均在此路由，先于静态通配匹配）。
import type { FastifyInstance, FastifyReply } from "fastify";
import { getSettings } from "../services/settings.js";
import { qrTokenValid, renderQrPage } from "./qr.js";

// 与「路由不存在」的默认响应同形：未知/过期令牌不泄漏任何信息
function notFound(reply: FastifyReply): FastifyReply {
  return reply.code(404).send({ message: "Not Found", error: "Not Found", statusCode: 404 });
}

// 302（临时）+ no-store：后台改地址立即生效，不被浏览器/中间层缓存
async function redirectToApk(reply: FastifyReply): Promise<FastifyReply> {
  const s = await getSettings();
  const url = (s.appRedirectUrl ?? "").trim();
  if (!url) {
    return reply.code(503).type("text/plain; charset=utf-8").send("安装包地址尚未配置");
  }
  return reply.header("Cache-Control", "no-store").redirect(url);
}

export async function downloadRoutes(app: FastifyInstance): Promise<void> {
  app.get("/download", (_req, reply) => reply.redirect("/download/"));

  app.get("/download/app-meta", async () => {
    const s = await getSettings();
    return { version: s.appNewVersion };
  });

  app.get("/download/apk", async (_req, reply) => redirectToApk(reply));

  // 二维码扫码落点页：有效令牌才渲染（token 随请求逐个校验，无缓存语义）
  app.get<{ Params: { token: string } }>("/download/q/:token", async (req, reply) => {
    const s = await getSettings();
    const { token } = req.params;
    if (!qrTokenValid(s, token)) return notFound(reply);
    return reply
      .header("Cache-Control", "no-store")
      .type("text/html; charset=utf-8")
      .send(renderQrPage(token, s.appNewVersion));
  });

  // 落点页之后的下载跳转：再校验一次令牌（页面滞留/被缓存也不能绕开有效期）
  app.get<{ Params: { token: string } }>("/download/q/:token/apk", async (req, reply) => {
    const s = await getSettings();
    if (!qrTokenValid(s, req.params.token)) return notFound(reply);
    return redirectToApk(reply);
  });
}
