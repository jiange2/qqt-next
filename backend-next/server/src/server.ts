// 入口：dotenv 在 config.ts 中加载
import { buildApp } from "./app.js";
import { config } from "./config.js";
import { startOnlineSampler } from "./services/stats.js";

const app = await buildApp();
// 在线采样（仓库级 ADR 0008）：进程内定时器，立即采一行后每 5 分钟一行
startOnlineSampler(app.log);
app.listen({ port: config.port, host: "0.0.0.0" }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});
