// 媒体 URL 构造：基地址由 /admin/config 运行时下发（换 CDN 只改服务端配置，无需重建前端）
import { api } from "./api";

let mediaBase = "/"; // 兜底：未加载时按本站相对路径

/** 登录后各列表加载前调用一次（usePagedList 内已接入） */
export async function loadMediaBase(): Promise<void> {
  if (mediaBase !== "/") return;
  try {
    mediaBase = (await api.get("/admin/config")).data.mediaBase ?? "/";
  } catch {
    // 拉取失败保持兜底，列表照常渲染
  }
}

export function imageUrl(name: string): string {
  return `${mediaBase}images/${encodeURIComponent(name)}`;
}

export function thumbUrl(name: string): string {
  return `${mediaBase}images/thumbs/${encodeURIComponent(name)}`;
}

/** 书籍封面（images/books/ 与 images/books/thumbs/ 同名同源，ADR 0011） */
export function bookCoverUrl(name: string): string {
  return `${mediaBase}images/books/${encodeURIComponent(name)}`;
}

export function bookThumbUrl(name: string): string {
  return `${mediaBase}images/books/thumbs/${encodeURIComponent(name)}`;
}

/** local 类型歌曲的音频完整链接（rand 前缀 key，文件名部分 encode） */
export function audioFileUrl(name: string): string {
  return `${mediaBase}uploads/${encodeURIComponent(name)}`;
}

/** 完整 key（如 uploads/x.mp3、images/thumbs/x.jpg）→ 对外 URL：目录段保留，文件名部分 encode */
export function objectUrl(key: string): string {
  const i = key.lastIndexOf("/");
  const dir = i >= 0 ? key.slice(0, i + 1) : "";
  const name = i >= 0 ? key.slice(i + 1) : key;
  return `${mediaBase}${dir}${encodeURIComponent(name)}`;
}
