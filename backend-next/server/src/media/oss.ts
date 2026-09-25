// 阿里云 OSS 客户端封装（ADR 0004）
// 上传经 internal 参数走同 region 内网 endpoint（免流量费）；
// 对外 URL 不由本文件产生，一律由 media/urls.ts 按 OSS_PUBLIC_BASE 运行时拼接。
import type { Readable } from "node:stream";
import OSS from "ali-oss";
import { config } from "../config.js";
import { decryptFilename, nameStem } from "./crypt.js";

let client: OSS | null = null;

function oss(): OSS {
  client ??= new OSS({
    region: config.oss.region,
    bucket: config.oss.bucket,
    accessKeyId: config.oss.accessKeyId,
    accessKeySecret: config.oss.accessKeySecret,
    authorizationV4: true,
    internal: config.oss.internal,
  });
  return client;
}

/** 查重：key 是否已存在（rand 命名撞名重摇检测） */
export async function objectExists(key: string): Promise<boolean> {
  try {
    await oss().head(key);
    return true;
  } catch (err) {
    if ((err as { status?: number }).status === 404) return false;
    throw err;
  }
}

// 新上传默认缓存头（CONTEXT「缓存头」），按目录区分：图片（images/ 含 thumbs、books）365 天、
// 歌词（lrc/）30 天、音频（uploads/）不设；存量靠管理面板批量补设
const UPLOAD_CACHE_HEADERS: Record<string, Record<string, string>> = {
  images: { "Cache-Control": "max-age=31536000" },
  lrc: { "Cache-Control": "max-age=2592000" },
};

/** 按目录取新上传默认缓存头；音频与未知目录返回空（不带 Cache-Control） */
function uploadHeaders(key: string): Record<string, string> {
  return UPLOAD_CACHE_HEADERS[key.split("/")[0] ?? ""] ?? {};
}

/** 小对象整块上传（图片原图/缩略图/lrc，≤20MB）；缓存头按目录默认（见 UPLOAD_CACHE_HEADERS）；
 *  meta 传入 OSS 自定义元数据（媒体混淆标记，见 obfuscate.ts） */
export async function putObject(
  key: string,
  body: Buffer,
  opts?: { meta?: Record<string, string> },
): Promise<void> {
  // @types/ali-oss 误将 PutObjectOptions 的 meta 标为必填 uid/pid 的 UserMeta，实际为任意自定义元数据键值
  const putOpts = {
    headers: uploadHeaders(key),
    ...(opts?.meta ? { meta: opts.meta } : {}),
  } as unknown as OSS.PutObjectOptions;
  await oss().put(key, body, putOpts);
}

/** 整块读取小对象内容（≤20MB 级图片；缩略图再生成等一次性脚本用） */
export async function getObject(key: string): Promise<Buffer> {
  const res = await oss().get(key);
  return res.content;
}

/** 音频流式上传（≤500MB，chunked encoding，不在内存驻留整个文件）；缓存头按目录默认（音频不设）；
 *  meta 传入 OSS 自定义元数据（媒体混淆标记，见 obfuscate.ts） */
export async function putStream(
  key: string,
  stream: Readable,
  opts?: { meta?: Record<string, string> },
): Promise<void> {
  // @types/ali-oss 误将 PutStreamOptions 的 timeout/mime/meta/callback 标为必填，实际均可省略
  const opts2 = {
    headers: uploadHeaders(key),
    ...(opts?.meta ? { meta: opts.meta } : {}),
  } as unknown as OSS.PutStreamOptions;
  await oss().putStream(key, stream, opts2);
}

/** 流式读取对象内容（批量混淆回写用：音频可达 500MB，不整块驻留内存） */
export async function getStream(key: string): Promise<Readable> {
  const res = await oss().getStream(key);
  return res.stream;
}

/** 对象是否已带混淆元数据（批量加密跳过判定，仓库级 ADR 0011）；对象缺失返回 null */
export async function isObfuscated(key: string): Promise<boolean | null> {
  try {
    const res = await oss().head(key);
    const headers = res.res.headers as Record<string, string | undefined>;
    return headers["x-oss-meta-encrypted"] === "1";
  } catch (err) {
    if ((err as { status?: number }).status === 404) return null;
    throw err;
  }
}

/** 管理面板对象条目；originalName 为 key 密文段解出的原名（ADR 0008），非密文 key 缺省 */
export type OssObject = { key: string; size: number; lastModified: string; originalName?: string };

/** 全量列举防御上限：超出即截断并标 truncated，防异常大 bucket 拖垮内存与响应 */
const LIST_LIMIT = 5000;

/**
 * 全量列举某 prefix 下对象并逐项解密原名（ADR 0008）。
 * 解密是本地计算零开销，故过滤/分页全部交给前端，服务端不再做 keyword 扫描与 token 游标。
 */
export async function listObjects(opts: {
  prefix?: string;
}): Promise<{ items: OssObject[]; truncated: boolean }> {
  const items: OssObject[] = [];
  let token: string | undefined;
  for (;;) {
    const res = await oss().listV2({
      prefix: opts.prefix || undefined,
      "max-keys": 1000,
      ...(token ? { "continuation-token": token } : {}),
    });
    for (const o of res.objects ?? []) {
      const tail = o.name.split("/").pop() ?? o.name;
      const original = decryptFilename(nameStem(tail)) ?? undefined;
      items.push({
        key: o.name,
        size: o.size,
        lastModified: o.lastModified,
        ...(original ? { originalName: original } : {}),
      });
    }
    if (!res.isTruncated) return { items, truncated: false };
    if (items.length >= LIST_LIMIT) return { items, truncated: true };
    token = res.nextContinuationToken;
  }
}

/** 删除单个对象（孤儿文件清理；是否被业务数据引用由路由层检查） */
export async function deleteObject(key: string): Promise<void> {
  await oss().delete(key);
}

/** 读取对象缓存头（管理端惰性查看用）：未设置或对象已缺失返回 null，其余错误上抛 */
export async function getCacheControl(key: string): Promise<string | null> {
  try {
    const res = await oss().head(key);
    const headers = res.res.headers as Record<string, string | undefined>;
    return headers["cache-control"] ?? null;
  } catch (err) {
    if ((err as { status?: number }).status === 404) return null;
    throw err;
  }
}

/**
 * CopyObject 复制到自己以改写缓存头（服务端完成，无数据传输；LastModified 随之刷新）。
 * 元数据按 REPLACE 全量重建：Content-Type 先 head 保留；其余标准头/用户元数据本就不设，清空无损失。
 */
export async function setCacheControl(key: string, maxAgeSeconds: number): Promise<void> {
  let contentType: string | undefined;
  try {
    const res = await oss().head(key);
    contentType = (res.res.headers as Record<string, string | undefined>)["content-type"];
  } catch (err) {
    if ((err as { status?: number }).status !== 404) throw err;
  }
  await oss().copy(key, key, {
    headers: {
      "Cache-Control": `max-age=${maxAgeSeconds}`,
      "x-oss-metadata-directive": "REPLACE",
      ...(contentType ? { "Content-Type": contentType } : {}),
    },
  });
}
