// 通过 prod 只读端点导出全部后台数据为 JSON dump（ADR 0006）
// 无 prod 数据库凭据，数据只能从旧 PHP 站点 api.php 分页拉取：
//   all_songs（歌曲）/ cat_list / artist_list / album_list / playlist / banners
//   + 每横幅 banner_songs（近似顺序）/ 每专辑 album_songs（补 album_id）
//   + 每歌曲 single_song（lrc 文本；注意会令 prod total_views +1）
//   + app_details（settings 子集）/ home_new.trending_songs（热门榜初始顺序）
//
// 用法：pnpm fetch:prod [--base=http://47.111.25.157/]
// 产物：scripts/dump/*.json（已 gitignore），供 import-prod.ts 与 fill-media.ts 消费
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

// ---------------------------------------------------------------- 协议参数（取自 Android AppConfig.kt，逐字复刻）

const BASE = (() => {
  const arg = process.argv.find((a) => a.startsWith("--base="));
  return (arg ? arg.split("=")[1] : process.env.PROD_BASE_URL ?? "http://47.111.25.157/").replace(/\/?$/, "/");
})();
const PACKAGE_NAME = process.env.PROD_PACKAGE_NAME ?? "com.vpapps.onlinemp3";
const SIGN_KEY = process.env.PROD_SIGN_KEY ?? "viaviweb";
const SLEEP_MS = 150; // 逐首拉 lrc 的节流，别把 prod 打疼

const OUT_DIR = path.resolve(import.meta.dirname, "dump");

type Params = Record<string, string>;
type Row = Record<string, string> & { song_ids?: string[] };

const md5 = (s: string) => crypto.createHash("md5").update(s, "utf8").digest("hex");
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const basename = (v: unknown): string => {
  const s = String(v ?? "").trim();
  return s.split(/[\\/]/).pop() ?? "";
};

/** 复刻 ApiClient.buildData：json → urlencode → base64，表单字段再整体 urlencode（Retrofit @Field 行为） */
function buildData(params: Params): string {
  const salt = Date.now().toString();
  const json = JSON.stringify({
    ...params,
    package_name: PACKAGE_NAME,
    salt,
    sign: md5(SIGN_KEY + salt),
  });
  const encoded = encodeURIComponent(json);
  return Buffer.from(encoded, "utf8").toString("base64");
}

/** 调一个 method，返回 ONLINE_MP3 节点；验签失败/购买码闸门直接抛错 */
async function call<T = Row | Row[]>(method: string, params: Params = {}): Promise<T> {
  const res = await fetch(`${BASE}api.php`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `data=${encodeURIComponent(buildData({ method_name: method, ...params }))}`,
  });
  if (!res.ok) throw new Error(`${method}: HTTP ${res.status}`);
  const body = (await res.json()) as { ONLINE_MP3: unknown };
  const node = body?.ONLINE_MP3;
  if (
    Array.isArray(node) &&
    node.length === 1 &&
    typeof node[0] === "object" &&
    node[0] !== null &&
    "success" in (node[0] as Row)
  ) {
    throw new Error(`${method}: ${String((node[0] as Row).msg)}`);
  }
  return node as T;
}

/** 分页拉完整张列表（每页 total_records/total_songs 给总数） */
async function paged(
  method: string,
  params: Params,
  countKey: "total_records" | "total_songs",
  pageSize: number,
): Promise<Row[]> {
  const all: Row[] = [];
  for (let page = 1; ; page++) {
    const rows = (await call<Row[]>(method, { ...params, page: String(page) })) ?? [];
    all.push(...rows);
    const first = rows[0];
    const total = Number.parseInt(first?.[countKey] ?? "0", 10);
    const done = rows.length === 0 || (Number.isFinite(total) && all.length >= total);
    console.log(`  ${method} page ${page}: +${rows.length}（累计 ${all.length}${total ? `/${total}` : ""}）`);
    if (done) break;
  }
  return all;
}

let warnings = 0;
const warn = (msg: string) => {
  warnings++;
  console.warn(`  [warn] ${msg}`);
};

// ---------------------------------------------------------------- main

fs.mkdirSync(OUT_DIR, { recursive: true });
const dump = (name: string, data: unknown): void => {
  fs.writeFileSync(path.join(OUT_DIR, name), JSON.stringify(data, null, 2));
  console.log(`dump: ${name}`);
};

console.log(`prod: ${BASE}`);
const summary: Record<string, number> = {};

// 1. 歌曲（含 full mp3_url / 图片 URL，id/cat_id/计数齐全；不含 album_id 与 lrc）
const songs = await paged("all_songs", {}, "total_songs", 10);
summary.songs = songs.length;
dump("songs.json", songs);

// 2. 分类 / 艺术家 / 专辑 / 歌单
const categories = await paged("cat_list", {}, "total_records", 10);
summary.categories = categories.length;
dump("categories.json", categories);

const artists = await paged("artist_list", {}, "total_records", 10);
summary.artists = artists.length;
dump("artists.json", artists);

const albums = await paged("album_list", {}, "total_records", 10);
summary.albums = albums.length;
dump("albums.json", albums);

const playlists = await paged("playlist", {}, "total_records", 10);
summary.playlists = playlists.length;

// 3. 每专辑 album_songs → songId → albumId（all_songs 不含 album_id）
const songAlbum = new Map<string, string>();
for (const a of albums) {
  try {
    const rows = await paged("album_songs", { album_id: a["aid"] }, "total_records", 10);
    for (const r of rows) songAlbum.set(r["id"], r["album_id"]);
  } catch (e) {
    warn(`album_songs ${a["aid"]}: ${e instanceof Error ? e.message : e}`);
  }
}
dump("album-songs.json", Object.fromEntries(songAlbum));

// 4. 每歌单 playlist_songs → 挂歌顺序（page=1 即全量：按 song_id 逐个查；
//    不传 page 会令旧端 $limit 为负而 SQL 报错，必须带 page=1）
for (const p of playlists) {
  try {
    const rows = (await call<Row[]>("playlist_songs", { playlist_id: p["pid"], page: "1" })) ?? [];
    const ids = rows.flatMap((r) => (r["songs_list"] as unknown as Row[] | undefined)?.map((s) => s["id"]) ?? []);
    p["song_ids"] = ids;
  } catch (e) {
    p["song_ids"] = [];
    warn(`playlist_songs ${p["pid"]}: ${e instanceof Error ? e.message : e}`);
  }
}
dump("playlists.json", playlists);

// 5. 横幅 + 每横幅 banner_songs（顺序取接口返回序，可能与原挂接顺序有偏差，ADR 0006）
const banners = (await call<Row[]>("banners")) ?? [];
summary.banners = banners.length;
for (const b of banners) {
  try {
    const rows = await paged("banner_songs", { banner_id: b["bid"] }, "total_records", 10);
    b["song_ids"] = rows.map((r) => r["id"]);
  } catch (e) {
    b["song_ids"] = [];
    warn(`banner_songs ${b["bid"]}: ${e instanceof Error ? e.message : e}`);
  }
}
dump("banners.json", banners);

// 6. 每歌曲 single_song → lrc 文本（副作用：prod total_views +1，一次性成本，接受）
const lrc: Record<string, { txt: string; url: string }> = {};
for (const s of songs) {
  try {
    const rows = (await call<Row[]>("single_song", { song_id: s["id"] })) ?? [];
    const first = rows[0];
    if (first) {
      const txt = String(first["mp3_lrc_txt"] ?? "").trim();
      const url = String(first["mp3_lrc_url"] ?? "").trim();
      if (txt || url) lrc[s["id"]] = { txt, url };
    }
  } catch (e) {
    warn(`single_song ${s["id"]}: ${e instanceof Error ? e.message : e}`);
  }
  const i = songs.indexOf(s) + 1;
  if (i % 50 === 0) console.log(`  single_song ${i}/${songs.length}（预计还需约 ${Math.round(((songs.length - i) * (SLEEP_MS + 350)) / 1000)}s）`);
  await sleep(SLEEP_MS);
}
summary.lrc = Object.keys(lrc).length;
dump("lrc.json", lrc);

// 7. settings 子集（app_details；onesignal/api_latest_limit/排序规则拿不到，导入后手工配）
dump("settings.json", await call("app_details"));

// 8. 热门榜初始顺序（prod 为近一月播放量算榜，取返回序作为人工固定榜初值）
const home = (await call<Record<string, Row[]>>("home_new")) ?? {};
const trending = (home["trending_songs"] ?? []).map((r) => r["id"]);
summary.trending = trending.length;
dump("trending.json", trending);

dump("summary.json", { base: BASE, fetchedAt: new Date().toISOString(), ...summary });
console.log(
  `\n导出完成：${songs.length} 歌 / ${categories.length} 分类 / ${artists.length} 艺术家 / ${albums.length} 专辑 / ${playlists.length} 歌单 / ${banners.length} 横幅 / ${summary.lrc} lrc / ${trending.length} 热门` +
    (warnings ? `，共 ${warnings} 条警告（见上）` : "，无警告"),
);
