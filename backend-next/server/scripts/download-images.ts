// 从 dump 的 albums.json / categories.json 下载图片到本地，按 URL 目录结构落盘
// 范围：各行 *_image / *_image_thumb 字段（album_image(_thumb)、category_image(_thumb)）
// 用法：pnpm download:images [--out=<dir>] [--force]
//   默认输出根 = scripts/dump，即 URL 路径 images/xxx.jpg → dump/images/xxx.jpg、
//   images/thumbs/xxx.jpg → dump/images/thumbs/xxx.jpg；--out 换输出根，--force 覆盖重下
// 已存在且非空的文件默认跳过，可重复执行续传
import fs from "node:fs";
import path from "node:path";

const DUMP_DIR = path.resolve(import.meta.dirname, "dump");

const OUT_ROOT = path.resolve((() => {
  const arg = process.argv.find((a) => a.startsWith("--out="));
  return arg ? arg.split("=")[1] : DUMP_DIR;
})());
const FORCE = process.argv.includes("--force");

const FILES = ["albums.json", "categories.json"] as const;

type Row = Record<string, string>;

const load = <T>(name: string): T =>
  JSON.parse(fs.readFileSync(path.join(DUMP_DIR, name), "utf8")) as T;

const str = (v: unknown) => (v == null ? "" : String(v)).trim();
/** 图片字段：*_image 与缩略图 *_image_thumb */
const isImageField = (key: string) => /_image(_thumb)?$/.test(key);

let warnings = 0;
const warn = (msg: string) => {
  warnings++;
  console.warn("  [warn] " + msg);
};

// ---------------------------------------------------------------- 收集 {url → 本地相对路径}

function collect(): Map<string, string> {
  const items = new Map<string, string>(); // URL → 相对 OUT_ROOT 路径（同名 URL 去重）
  for (const file of FILES) {
    for (const row of load<Row[]>(file)) {
      for (const [key, value] of Object.entries(row)) {
        if (!isImageField(key)) continue;
        const url = str(value);
        if (!/^https?:\/\//.test(url)) {
          if (url) warn(`${file} ${key}: 非 http(s) URL（${url}）`);
          continue;
        }
        const u = new URL(url);
        let rel = "";
        try {
          rel = decodeURIComponent(u.pathname);
        } catch {
          rel = u.pathname; // % 序列畸形时退回原样
        }
        rel = rel.replace(/\/+/g, "/").replace(/^\/+/, "");
        if (!rel) {
          warn(`${file} ${key}: URL 无路径（${url}）`);
          continue;
        }
        items.set(url, rel);
      }
    }
  }
  return items;
}

// ---------------------------------------------------------------- main

console.log(`out: ${OUT_ROOT}${FORCE ? "（--force 覆盖已有文件）" : ""}`);
const items = collect();
console.log(`待下载：${items.size} 个`);
let done = 0, skipped = 0, i = 0;
for (const [url, rel] of items) {
  i++;
  const dest = path.join(OUT_ROOT, rel);
  // 路径安全：.. 或绝对段经解析后必须仍在输出根内
  if (!path.resolve(dest).startsWith(OUT_ROOT + path.sep)) {
    warn(`${rel}: 路径越界，跳过`);
    continue;
  }
  if (!FORCE && fs.existsSync(dest) && fs.statSync(dest).size > 0) {
    skipped++;
    continue;
  }
  try {
    const res = await fetch(url, { redirect: "follow" });
    if (!res.ok) {
      warn(`${rel}: HTTP ${res.status}（${url}）`);
      continue;
    }
    const body = Buffer.from(await res.arrayBuffer());
    if (body.length === 0) {
      warn(`${rel}: 空文件`);
      continue;
    }
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, body);
    done++;
    if (done % 20 === 0) console.log(`  进度 ${i}/${items.size}`);
  } catch (e) {
    warn(`${rel}: ${e instanceof Error ? e.message : e}`);
  }
}
console.log(
  `\n完成：新增 ${done}，已存在跳过 ${skipped} / ${items.size}` +
    (warnings ? `，共 ${warnings} 条警告（见上）` : "，无警告"),
);
