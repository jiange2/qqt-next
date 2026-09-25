// 媒体上传：sharp 内存压缩后直传 OSS（ADR 0004）；命名见 ADR 0008：
// 保留原名类型 rand_加密原名.<ext>（原名 AES-256-ECB + Base64url 进 key，扩展名明文），
// 固定标签类型 rand_<label><ext>（原名丢弃，沿用旧逻辑）
// DB 只存文件名（key = images/<name>），对外 URL 由 media/urls.ts 运行时拼接
import path from "node:path";
import sharp from "sharp";
import { encryptName } from "./crypt.js";
import { OBFUSCATED_META, obfuscateBuffer } from "./obfuscate.js";
import { objectExists, putObject } from "./oss.js";

/** 6 位随机数（旧逻辑 rand(0,99999) 扩大范围，进一步降低撞名概率） */
const rand = () => Math.floor(Math.random() * 1_000_000);

/**
 * rand 前缀命名：label 指定时用固定标签 `rand_<label><ext>`（歌曲缩略图/LRC/专辑/歌手/分类图）；
 * 否则原名加密 `rand_<Base64url(原名)>.<ext>`（ADR 0008）。加密是确定性的，同名密文相同，
 * 撞 key 靠重摇随机数（上限 10 次）。原名截 128 字符，防长名密文把 key 推近 OSS 1023 字节上限。
 */
export async function resolveName(
  dir: string,
  originalName: string,
  label?: string,
): Promise<string> {
  const ext = path.extname(originalName).toLowerCase();
  const tail = label
    ? `${label}${ext}`
    : `${encryptName([...originalName.slice(0, originalName.length - ext.length)].slice(0, 128).join(""))}${ext}`;
  for (let i = 0; i < 10; i++) {
    const name = `${rand()}_${tail}`;
    if (!(await objectExists(`${dir}/${name}`))) return name;
  }
  throw new Error(`resolveName: no available name in ${dir} after 10 attempts`);
}

/** 书籍封面缩略图边长：书单页 2 列大卡在 3x 屏显示宽约 540px，默认 300 会被放大 2 倍余致封面文字不可辨（ADR 0011 修订） */
export const BOOK_THUMB_SIZE = 720;

/** 保存原图（指定质量压缩）与缩略图到 OSS，返回文件名；label 用于固定标签命名；
 *  原图落 images/，缩略图同名落 images/thumbs/（所有业务图同库，无专属子目录，ADR 0011 修订）；
 *  thumbSize 缩略图边长（默认 300，音乐域展示位小；书籍封面传 BOOK_THUMB_SIZE） */
export async function saveImage(
  buffer: Buffer,
  originalName: string,
  quality = 80,
  label?: string,
  thumbSize = 300,
): Promise<string> {
  // 原图与缩略图同名异目录（images/ 与 images/thumbs/），查重只看原图 key
  const name = await resolveName("images", originalName, label);

  const img = sharp(buffer, { failOn: "none" }).rotate();
  // 原图与缩略图各自逐字节 +31 混淆后上传（仓库级 ADR 0011），encrypted 元数据同 put 原子写入
  const origin = obfuscateBuffer(await img.clone().toBuffer()); // 保留原格式，sharp 默认质量已足够
  // 缩略图 inside 缩边 + 小图不放大（音乐域 300，书籍大卡 720，ADR 0011 修订）
  const thumb = obfuscateBuffer(
    await img.clone().resize(thumbSize, thumbSize, { fit: "inside", withoutEnlargement: true }).jpeg({ quality }).toBuffer(),
  );
  await Promise.all([
    putObject(`images/${name}`, origin, { meta: OBFUSCATED_META }),
    putObject(`images/thumbs/${name}`, thumb, { meta: OBFUSCATED_META }),
  ]);
  return name;
}
