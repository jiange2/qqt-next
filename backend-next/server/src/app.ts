// 应用组装：legacy 门面 + admin API 与面板静态托管（媒体文件走 OSS，见 ADR 0004）
import path from "node:path";
import fs from "node:fs";
import Fastify, { type FastifyInstance } from "fastify";
import cors from "@fastify/cors";
import multipart from "@fastify/multipart";
import fastifyStatic from "@fastify/static";
import formbody from "@fastify/formbody";
import { legacyRoutes } from "./legacy/routes.js";
import { adminRoutes } from "./admin/routes.js";

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: true, bodyLimit: 2 * 1024 * 1024 });

  await app.register(cors, { origin: true });
  // 旧客户端以 application/x-www-form-urlencoded 提交（data=...&sign=...）
  await app.register(formbody);
  // 上限为音频上限（Q19：音频 ≤500MB）；图片/文本大小在处理层另行校验
  await app.register(multipart, {
    limits: { fileSize: 500 * 1024 * 1024, files: 5 },
  });

  // 生产环境托管管理面板构建产物（admin-web/dist；开发时由 vite dev server 承担）
  // 编译产物与源码运行时目录层级不同（dist/src/ vs src/），依次探测；容器用 ADMIN_DIST 指定
  const adminDistCandidates = [
    process.env.ADMIN_DIST,
    path.resolve(import.meta.dirname, "../../../admin-web/dist"), // 编译产物 dist/src/
    path.resolve(import.meta.dirname, "../../admin-web/dist"), // 源码 src/
  ].filter((p): p is string => !!p);
  const adminDist = adminDistCandidates.find((p) => fs.existsSync(p));
  if (adminDist) {
    await app.register(fastifyStatic, {
      root: adminDist,
      prefix: "/admin/",
      decorateReply: false,
    });
  }

  await app.register(legacyRoutes);
  await app.register(adminRoutes);
  return app;
}
