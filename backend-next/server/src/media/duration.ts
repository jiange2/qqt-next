// 音频时长探测（仓库级 ADR 0008 修订）：管理端「修复时长」读音频对象 → 逆混淆 → 解析真实时长。
// 上传对象一律为混淆态（App 播放侧同口径无条件还原），故直接逆变换、不看 encrypted 元数据标记
// （该标记可被 setCacheControl 的 REPLACE 重置丢失，不可作判据）。
import { parseBuffer } from "music-metadata";
import { getObject } from "./oss.js";
import { deobfuscateBuffer } from "./obfuscate.js";

/** 探测音频对象时长（秒）。对象缺失、内容非音频或解析不出有效时长时抛错，由路由折算为失败原因 */
export async function probeAudioDuration(key: string): Promise<number> {
  const buffer = deobfuscateBuffer(await getObject(key));
  const meta = await parseBuffer(buffer, undefined, { duration: true });
  const seconds = Math.round(meta.format.duration ?? 0);
  if (!Number.isFinite(seconds) || seconds <= 0) throw new Error("unparseable duration");
  return Math.min(seconds, 7200); // 与「时长写回」通道同口径钳制
}
