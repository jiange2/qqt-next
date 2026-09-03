// 从 prod 下载图片/缩略图/歌词文件并按同名 key 上传 OSS（ADR 0006）
// 范围：images/、images/thumbs/、lrc/ —— mp3 不在此脚本，由后台「转入 OSS」面板功能负责
// 用法：pnpm fill:media [--base=http://47.111.25.157/]（默认读 fetch-prod 的 dump 汇总基址）
//
// 覆盖说明：DB 引用的 key 就是 prod 原文件名，若 OSS 已有同名对象（同名不同内容的旧图），
// 以 prod 内容为准直接覆盖，保证库内引用与对象一致；不做冲突后缀（后缀会破坏库内引用）。
import "dotenv/config";
import fs from "node:fs";
import path from "node:path";
import { putObject } from "../src/media/oss.js";

const DUMP_DIR = path.resolve(import.meta.dirname, "dump");

const dumpBase = (() => {
  const summaryFile = path.join(DUMP_DIR, "summary.json");
  if (fs.existsSync(summaryFile)) {
    try {
      return String(JSON.parse(fs.readFileSync(summaryFile, "utf8")).base ?? "");
    } catch {
      /* 忽略，走 --base */
    }
  }
  return "";
})();
const BASE = (() => {
  const arg = process.argv.find((a) => a.startsWith("--base="));
  const b = arg ? arg.split("=")[1] : dumpBase || process.env.PROD_BASE_URL || "http://47.111.25.157/";
  return b.replace(/\/?$/, "/");
})();

type Row = Record<string, string>;

const load = <T>(name: string): T =>
  JSON.parse(fs.readFileSync(path.join(DUMP_DIR, name), "utf8")) as T;

const str = (v: unknown) => (v == null ? "" : String(v)).trim();
/** 响应里只有完整 URL；落库/上传的 key 取末段文件名 */
const basename = (v: unknown) => str(v).split(/[\\/]/).pop() ?? "";

let warnings = 0;
const warn = (msg: string) => {
  warnings++;
  console.warn("  [warn] " + msg);
};

// ---------------------------------------------------------------- 收集 {key, url} 清单

function collect(): Map<string, string> {
  const items = new Map<string, string>(); // key → prod 下载 URL
  const addImage = (v: unknown) => {
    const name = basename(v);
    if (name) items.set(`images/${name}`, `${BASE}images/${encodeURIComponentSafe(name)}`);
  };
  const categories = load<Row[]>("categories.json");
  const artists = load<Row[]>("artists.json");
  const albums = load<Row[]>("albums.json");
  const playlists = load<Row[]>("playlists.json");
  const banners = load<Row[]>("banners.json");
  const songs = load<Row[]>("songs.json");
  const settings = load<Row>("settings.json");
  const lrc = load<Record<string, { txt: string; url: string }>>("lrc.json");

  for (const c of categories) addImage(c["category_image"]);
  for (const a of artists) addImage(a["artist_image"]);
  for (const a of albums) addImage(a["album_image"]);
  for (const p of playlists) addImage(p["playlist_image"]);
  for (const b of banners) addImage(b["banner_image"]);
  for (const s of songs) addImage(s["mp3_thumbnail_b"]);
  // app_logo 存的是库名（无域名字段），同样落到 images/
  const logo = basename(settings["app_logo"]);
  if (logo) items.set(`images/${logo}`, `${BASE}images/${encodeURIComponentSafe(logo)}`);
  for (const e of Object.values(lrc)) {
    const name = basename(e.url);
    if (name) items.set(`lrc/${name}`, `${BASE}lrc/${encodeURIComponentSafe(name)}`);
  }
  return items;
}

/** 路径段编码：保留 /，其余按 URI 编码（中文、空格等） */
function encodeURIComponentSafe(name: string): string {
  return name
    .split("/")
    .map((seg) => encodeURIComponent(seg))
    .join("/");
}

// ---------------------------------------------------------------- main

console.log(`prod: ${BASE}`);
const items = collect();
console.log(`待补齐对象：${items.size} 个`);
let done = 0;
for (const [key, url] of items) {
  try {
    const res = await fetch(url, { redirect: "follow" });
    if (!res.ok) {
      warn(`${key}: HTTP ${res.status}（${url}）`);
      continue;
    }
    const body = Buffer.from(await res.arrayBuffer());
    if (body.length === 0) {
      warn(`${key}: 空文件`);
      continue;
    }
    await putObject(key, body);
    done++;
    if (done % 50 === 0) console.log(`  进度 ${done}/${items.size}`);
  } catch (e) {
    warn(`${key}: ${e instanceof Error ? e.message : e}`);
  }
}
console.log(`\n补齐完成：${done}/${items.size} 上传 OSS${warnings ? `，共 ${warnings} 条警告（见上）` : "，无警告"}`);
console.log("mp3 不在本脚本范围：请在后台歌曲列表对 external 歌曲执行「转入 OSS」");
