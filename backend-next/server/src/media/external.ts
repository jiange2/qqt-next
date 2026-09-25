// 外链歌曲转入 OSS（ADR 0006）：下载 external 音频 → 上传 OSS（ADR 0008 加密命名）→ 歌曲改回 local
// 任意 http(s) 外链均可转入（Q7）；失败时保持 external 原状，由调用方报错，可重试
import { Readable } from "node:stream";
import { OBFUSCATED_META, obfuscateStream } from "./obfuscate.js";
import { putStream } from "./oss.js";
import { resolveName } from "./save.js";

export type TransferResult = { ok: true; name: string } | { ok: false; error: string };

/**
 * 下载 song.audioUrl 并上传 OSS，返回落库用的 key 文件名（uploads/<name> 的 <name>）。
 * 只做下载与上传，不改库——由 admin 路由在同一请求内更新歌曲，避免半更新状态。
 *
 * 命名原文优先用歌曲 title（ADR 0008 修订：外链文件名可能是乱码长哈希），为空回退 URL 末段；
 * 扩展名取 URL 末段（不像扩展名则回退 .mp3），最终经 resolveName 加密进 key。
 */
export async function transferExternalToOss(
  audioUrl: string,
  title = "",
): Promise<TransferResult> {
  const url = audioUrl.trim();
  if (!/^https?:\/\//i.test(url)) return { ok: false, error: "audio_url 不是 http(s) 外链" };

  let res: Response;
  try {
    res = await fetch(url, { redirect: "follow" });
  } catch (e) {
    return { ok: false, error: `下载失败: ${e instanceof Error ? e.message : e}` };
  }
  if (!res.ok || !res.body) return { ok: false, error: `下载失败: HTTP ${res.status}` };

  // URL 末段文件名：扩展名来源 + title 为空时的命名兜底
  let fallback: string;
  try {
    fallback = decodeURIComponent(new URL(url).pathname.split("/").pop() ?? "");
  } catch {
    fallback = "";
  }
  const rawExt = /\.[a-z0-9]{1,8}$/i.exec(fallback)?.[0] ?? "";
  const ext = rawExt.toLowerCase() || ".mp3";
  const stem = title.trim() || fallback.slice(0, fallback.length - rawExt.length);
  if (!stem) return { ok: false, error: "audio_url 无法解析出文件名且歌曲无 title" };

  const key = await resolveName("uploads", `${stem}${ext}`);
  try {
    // 下载流经 +31 混淆后写入（仓库级 ADR 0011），encrypted 元数据同 put 原子写入
    await putStream(
      `uploads/${key}`,
      Readable.fromWeb(res.body as import("node:stream/web").ReadableStream).pipe(obfuscateStream()),
      { meta: OBFUSCATED_META },
    );
  } catch (e) {
    return { ok: false, error: `上传 OSS 失败: ${e instanceof Error ? e.message : e}` };
  }
  return { ok: true, name: key };
}
