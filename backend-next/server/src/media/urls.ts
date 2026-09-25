// 存储抽象层（ADR 0003 决定 5 / Q12；OSS 迁移见 ADR 0004，rand 前缀命名回退见 save.ts）
// 所有对外媒体 URL 的生成集中在此：key 与 URL 分离，DB 只存 key，
// 换 CDN / 内网域名时只改 OSS_PUBLIC_BASE 配置，业务层与数据零改动。
import { config } from "../config.js";

/** 对外媒体基地址（公网 endpoint / 自定义域名 / CDN），尾部带斜杠 */
export function mediaBase(): string {
  const base = config.oss.publicBase;
  return base.endsWith("/") ? base : base + "/";
}

/** 文件名部分统一 encode：rand_原名 key 仍可能包含中文/& 等字符 */
const enc = (name: string) => encodeURIComponent(name);

export function imageUrl(base: string, fileName: string): string {
  return `${base}images/${enc(fileName)}`;
}

/** 缩略图与原图同名存放在 images/thumbs/ 下 */
export function thumbUrl(base: string, fileName: string): string {
  return `${base}images/thumbs/${enc(fileName)}`;
}

/** 本地类型歌曲的音频地址：key 为 uploads/ 下的文件名，其余类型存的是完整外址 */
export function audioUrl(base: string, type: string, audioUrl: string): string {
  if (type !== "local") return audioUrl;
  return `${base}uploads/${enc(audioUrl.split(/[\\/]/).pop() ?? audioUrl)}`;
}

/** 歌词文件地址：无歌词文件（key 为空）时返回空串，不拼伪 URL——App 端以空串判定「暂无歌词」，伪 URL 会被误当歌词文件下载（ADR 0014） */
export function lrcUrl(base: string, fileName: string | null): string {
  if (!fileName) return "";
  return `${base}lrc/${enc(fileName)}`;
}
