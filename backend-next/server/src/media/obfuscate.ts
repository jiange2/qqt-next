// 媒体字节混淆（仓库级 ADR 0011）：逐字节 +31（模 256）的确定性变换，无密钥，
// 目标仅是抬高直链抓取门槛。已混淆对象以自定义元数据 encrypted=1 标记，
// 与混淆内容同一次 put 原子写入；歌词（lrc/）不参与（ADR 0010 真加密，另有口令）。
import { Transform } from "node:stream";

export const SHIFT = 31;

/** OSS 自定义元数据形态（ali-oss put/putStream 的 meta 入参） */
export const OBFUSCATED_META = { encrypted: "1" } as const;

/** 整块混淆（图片原图/缩略图等 ≤20MB 对象） */
export function obfuscateBuffer(buf: Buffer): Buffer {
  const out = Buffer.allocUnsafe(buf.length);
  for (let i = 0; i < buf.length; i++) out[i] = (buf[i] + SHIFT) & 0xff;
  return out;
}

/** 整块还原（时长探测解析音频用；与 obfuscateBuffer 互为逆变换） */
export function deobfuscateBuffer(buf: Buffer): Buffer {
  const out = Buffer.allocUnsafe(buf.length);
  for (let i = 0; i < buf.length; i++) out[i] = (buf[i] - SHIFT) & 0xff;
  return out;
}

/** 流式混淆（音频大文件用，不驻留整个文件；管道串接即可） */
export function obfuscateStream(): Transform {
  return new Transform({
    transform(chunk: Buffer, _enc, cb) {
      const out = Buffer.allocUnsafe(chunk.length);
      for (let i = 0; i < chunk.length; i++) out[i] = (chunk[i] + SHIFT) & 0xff;
      cb(null, out);
    },
  });
}
