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
  return `${mediaBase}images/${name}`;
}

export function thumbUrl(name: string): string {
  return `${mediaBase}images/thumbs/${name}`;
}
