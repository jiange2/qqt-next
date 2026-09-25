// 媒体 key 原名加解密（ADR 0008）：原名 AES-256-ECB 加密后以 Base64url 写入对象 key，
// 彻底隐藏 OSS 上的明文原名；扩展名不参与加密（Content-Type 推断与前端预览依赖）。
// 密文带 enc1: 版本前缀，便于将来换算法/换密钥时区分旧格式。
// 另含歌词内容加密（docs/adr/0010）：LRC 文件内容整体加密为 elrc1: 前缀文本后落 OSS（歌词密文）。
import crypto from "node:crypto";
import { config } from "../config.js";

const PREFIX = "enc1:";

/** 任意字符串密钥派生 AES-256 密钥（sha256 定长 32 字节） */
function deriveKey(): Buffer {
  return crypto.createHash("sha256").update(config.oss.nameSecret).digest();
}

/** 加密原名 → `enc1:<base64url>`；Base64url 不含 `/`，不会被 OSS 解析为目录分隔符 */
export function encryptName(name: string): string {
  const cipher = crypto.createCipheriv("aes-256-ecb", deriveKey(), null);
  const enc = Buffer.concat([cipher.update(name, "utf8"), cipher.final()]);
  return PREFIX + enc.toString("base64url");
}

/**
 * 解密 key 尾段 → 原名；非密文（存量明文 key）/ 密钥不符 / 格式错误一律返回 null，
 * 调用方回退显示 key 原文，绝不因解密失败打断列表或表单。
 */
export function decryptName(value: string): string | null {
  if (!value.startsWith(PREFIX)) return null;
  try {
    const decipher = crypto.createDecipheriv("aes-256-ecb", deriveKey(), null);
    const dec = Buffer.concat([
      decipher.update(value.slice(PREFIX.length), "base64url"),
      decipher.final(),
    ]);
    return dec.toString("utf8") || null;
  } catch {
    return null;
  }
}

/** key 尾段去掉明文扩展名后的待解密部分（密文段不含点，直接截掉最后一个 .ext 即可） */
export function nameStem(filename: string): string {
  const dot = filename.lastIndexOf(".");
  return dot > 0 ? filename.slice(0, dot) : filename;
}

/**
 * 解密 key 文件名（含 rand_ 前缀）→ 原名。密文段不含 "_"，取首个 "_" 之后的段解密，
 * 兼容裸密文（无前缀）；明文 key / 解密失败返回 null，调用方回退显示 key。
 */
export function decryptFilename(filename: string): string | null {
  const idx = filename.indexOf("_");
  return decryptName(idx > 0 ? filename.slice(idx + 1) : filename);
}

// ── 歌词内容加密（docs/adr/0010）─────────────────────────────
// 与原名加密（enc1）的区别：加密对象是文件内容而非 key，且用 CBC 而非 ECB——
// 内容长、块结构重复多，固定 IV 的取舍见 ADR 0010；App 端 LrcCipher 按同派生方式解密。

const LRC_PREFIX = "elrc1:";

/** 歌词内容 key/IV 派生：sha256("域分隔串:口令")，key 取 32 字节、IV 取前 16 字节；域分隔防 key 与 IV 同源 */
function deriveLrc(info: string, length: number): Buffer {
  return crypto.createHash("sha256").update(`${info}:${config.lyricsContentSecret}`).digest().subarray(0, length);
}

/** 歌词明文 → `elrc1:<base64url(AES-256-CBC 密文)>`；唯一入口是 saveTextFile（歌曲表单与 OSS 直传两路汇于此） */
export function encryptLyrics(plain: string): string {
  const cipher = crypto.createCipheriv("aes-256-cbc", deriveLrc("elrc1-key", 32), deriveLrc("elrc1-iv", 16));
  const enc = Buffer.concat([cipher.update(plain, "utf8"), cipher.final()]);
  return LRC_PREFIX + enc.toString("base64url");
}
