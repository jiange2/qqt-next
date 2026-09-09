// 媒体解混淆（仓库级 ADR 0011）：CDN 上的图片字节为 +31 混淆态，浏览器 fetch 后
// 按魔数判定并 -31 还原成 blob URL。明文（迁移期 CDN/浏览器旧缓存）按魔数直认不处理；
// 其余一律还原。URL → blobURL 结果模块级缓存，同一 URL 只拉一次。
import { reactive } from "vue";

const SHIFT = 31;

const cache = reactive(new Map<string, string>());

/** 图片展示统一走本函数：首次调用触发异步拉取，就绪前返回空串（el-image 短暂占位） */
export function deobfSrc(url: string): string {
  if (!url || url.startsWith("blob:") || url.startsWith("data:")) return url;
  const hit = cache.get(url);
  if (hit !== undefined) return hit;
  cache.set(url, "");
  void load(url);
  return "";
}

async function load(url: string): Promise<void> {
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const raw = new Uint8Array(await res.arrayBuffer());
    const bytes = imageMime(raw) ? raw : deobfuscate(raw);
    const blobUrl = URL.createObjectURL(
      new Blob([bytes], { type: imageMime(bytes) ?? "image/jpeg" }),
    );
    cache.set(url, blobUrl);
  } catch {
    // 拉取失败回退原始 URL，交给 el-image 的错误态展示
    cache.set(url, url);
  }
}

/** 魔数判定：明文图片返回 MIME，混淆态（或未知格式）返回 null */
function imageMime(b: Uint8Array): string | null {
  const at = (i: number) => b[i] ?? 0;
  if (b.length >= 3 && at(0) === 0xff && at(1) === 0xd8 && at(2) === 0xff) return "image/jpeg";
  if (
    b.length >= 8 &&
    at(0) === 0x89 && at(1) === 0x50 && at(2) === 0x4e && at(3) === 0x47 &&
    at(4) === 0x0d && at(5) === 0x0a && at(6) === 0x1a && at(7) === 0x0a
  ) {
    return "image/png";
  }
  if (b.length >= 4 && at(0) === 0x47 && at(1) === 0x49 && at(2) === 0x46 && at(3) === 0x38) {
    return "image/gif";
  }
  if (
    b.length >= 12 &&
    at(0) === 0x52 && at(1) === 0x49 && at(2) === 0x46 && at(3) === 0x46 &&
    at(8) === 0x57 && at(9) === 0x45 && at(10) === 0x42 && at(11) === 0x50
  ) {
    return "image/webp";
  }
  if (b.length >= 2 && at(0) === 0x42 && at(1) === 0x4d) return "image/bmp";
  if (b.length >= 8 && at(4) === 0x66 && at(5) === 0x74 && at(6) === 0x79 && at(7) === 0x70) {
    return "image/avif";
  }
  return null;
}

/** 逐字节 -31 还原 */
function deobfuscate(b: Uint8Array): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(b.length);
  for (let i = 0; i < b.length; i++) out[i] = (b[i] + 256 - SHIFT) & 0xff;
  return out;
}
