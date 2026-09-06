// 在线状态推导与采样（仓库级 ADR 0008）
// 在线 ≡ 预期下线时间晚于当前时刻；预期下线时间 = 设备最新事实时刻 + 曲时长 + 1 分钟冗余，
// 时长缺失/为 0 时仅加冗余。采样为进程内定时任务，重启漏采不补。
import { prisma } from "../prisma.js";

const SAMPLE_INTERVAL_MS = 5 * 60 * 1000;

type OnlineCountRow = { total: number | bigint };

/** 当下在线设备数：每设备最新事实 + 该曲时长推导（时长取 songs.duration，App 写回）。
 *  「每设备最新」用 MAX(id) 回连而非 ROW_NUMBER()：窗口函数需 MySQL 8.0+，部署库是 5.7 */
export async function computeOnlineCount(): Promise<number> {
  const rows = await prisma.$queryRaw<OnlineCountRow[]>`
    SELECT COUNT(*) AS total
    FROM access_facts f
    INNER JOIN (
      SELECT MAX(id) AS max_id
      FROM access_facts
      GROUP BY device_id
    ) latest ON latest.max_id = f.id
    LEFT JOIN songs s ON s.id = f.song_id
    WHERE f.accessed_at + INTERVAL (IFNULL(s.duration, 0) + 60) SECOND > NOW()
  `;
  return Number(rows[0]?.total ?? 0);
}

/** 采样一行（sampledAt 截到分钟作幂等键，重复调度下 INSERT IGNORE 落空） */
async function takeSample(log: { info: (o: unknown, msg?: string) => void }): Promise<void> {
  const now = new Date();
  now.setSeconds(0, 0);
  const totalOnline = await computeOnlineCount();
  await prisma.onlineSample.createMany({
    data: [{ sampledAt: now, totalOnline }],
    skipDuplicates: true,
  });
  log?.info({ totalOnline }, "[online-sampler] sampled");
}

/** 启动进程内采样器：立即采一次，此后每 5 分钟一行；失败仅记日志不影响服务 */
export function startOnlineSampler(log: { info: (o: unknown, msg?: string) => void }): void {
  const run = () => {
    takeSample(log).catch((err) => log.info({ err }, "[online-sampler] sample failed"));
  };
  run();
  setInterval(run, SAMPLE_INTERVAL_MS).unref();
}
