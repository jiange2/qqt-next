// Legacy 门面 —— 访问统计 method（record_song_access / update_song_duration）
// 仓库级 ADR 0008：事实 append-only、条数口径、时长由 App 写回；均为 fire-and-forget，响应不消费
import { prisma } from "../prisma.js";
import type { LegacyCtx } from "./handlers-content.js";

/** 解析可选整数字段（字节快照等），缺失/非法返回 null */
function optionalInt(raw: string | undefined): number | null {
  if (raw === undefined || raw === "") return null;
  const n = Number(raw);
  return Number.isFinite(n) ? Math.round(n) : null;
}

/**
 * 记录一次装载播放事实（单条）。命中与否由客户端在装载时刻判定（C4，见 ADR 0008），
 * 已下载歌曲的本地播放同样上报并计命中；IP、User-Agent 与时间由服务端补齐。
 */
export async function recordSongAccess(ctx: LegacyCtx): Promise<unknown> {
  const songId = Number(ctx.data["song_id"]);
  const deviceId = (ctx.data["device_id"] ?? "").trim();
  if (!Number.isFinite(songId) || songId <= 0 || !deviceId) return { success: "0" };

  await prisma.accessFact.create({
    data: {
      songId,
      deviceId,
      cacheHit: ctx.data["cache_hit"] === "1",
      allocatedStorage: optionalInt(ctx.data["allocated_storage"]),
      usedStorage: optionalInt(ctx.data["used_storage"]),
      ipAddress: ctx.ip,
      userAgent: ctx.userAgent || null,
    },
  });
  return { success: "1" };
}

/**
 * 时长写回：App 播放就绪后取得真实时长，与列表接口下发的现值比对、不一致才上报
 * （客户端负责比对与会话去重，服务端跳过同值写入避免触碰 updated_at）。
 * 秒数钳制 0–7200 防呆；歌已删除时 updateMany 落空，静默成功。
 */
export async function updateSongDuration(ctx: LegacyCtx): Promise<unknown> {
  const songId = Number(ctx.data["song_id"]);
  const seconds = Math.round(Number(ctx.data["duration"]));
  if (!Number.isFinite(songId) || songId <= 0) return { success: "0" };
  if (!Number.isFinite(seconds) || seconds <= 0 || seconds > 7200) return { success: "0" };

  await prisma.song.updateMany({
    where: { id: songId, NOT: { duration: seconds } },
    data: { duration: seconds },
  });
  return { success: "1" };
}
