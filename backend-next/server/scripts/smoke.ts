// 一次性冒烟脚本：对运行中的新服务发起 /legacy 全 method 请求，输出结构摘要。
// 前置：新库已迁移（pnpm migrate:legacy）、server 已启动（pnpm dev）。
// 运行：pnpm smoke [--base=http://127.0.0.1:8000]
// 说明：sign 与 salt 与旧客户端一致，放在 data JSON 内；参数名严格对照旧 api.php
//      （song_id / cat_id / album_id / aid / bid / pid / type 等）。
import crypto from "node:crypto";
import "dotenv/config";
import { PrismaClient } from "@prisma/client";

const baseArg = process.argv.find((a) => a.startsWith("--base="));
const BASE = baseArg ? baseArg.split("=")[1] : "http://127.0.0.1:8000/api.php";

// 样本 id 取自新库（legacy 库已清空/不可依赖，见 ADR 0006 后的现状）
const prisma = new PrismaClient();
const packageName = (await prisma.setting.findUnique({ where: { id: 1 } }))?.packageName ?? "";
const cat = await prisma.category.findFirst({ orderBy: { id: "asc" }, select: { id: true } });
const song = await prisma.song.findFirst({ orderBy: { id: "asc" }, select: { id: true } });
const artist = await prisma.artist.findFirst({ orderBy: { id: "asc" }, select: { id: true, name: true } });
const album = await prisma.album.findFirst({ orderBy: { id: "asc" }, select: { id: true } });
const banner = await prisma.banner.findFirst({ orderBy: { id: "asc" }, select: { id: true } });
const pl = await prisma.playlist.findFirst({ orderBy: { id: "asc" }, select: { id: true } });
await prisma.$disconnect();

function encode(data: unknown): string {
  return Buffer.from(encodeURIComponent(JSON.stringify(data)), "utf8").toString("base64");
}

async function call(method: string, extra: Record<string, unknown> = {}) {
  const salt = crypto.randomBytes(4).toString("hex");
  const sign = crypto.createHash("md5").update("viaviweb" + salt).digest("hex");
  const body = new URLSearchParams();
  body.set("data", encode({ method_name: method, package_name: packageName, salt, sign, ...extra }));
  const res = await fetch(BASE, { method: "POST", body });
  const text = await res.text();
  try {
    return { http: res.status, json: JSON.parse(text) as unknown };
  } catch {
    return { http: res.status, json: undefined, raw: text.slice(0, 80) };
  }
}

/** 结构摘要：递归取键与类型（限深 3 层），用于人工比对响应形态 */
function summarize(v: unknown, depth = 0): string {
  if (depth > 3) return typeof v;
  if (v === null) return "null";
  if (Array.isArray(v)) return `[${summarize(v[0], depth + 1)}]×${v.length}`;
  if (typeof v === "object") {
    const keys = Object.keys(v as object);
    return `{${keys.slice(0, 14).map((k) => `${k}:${summarize((v as Record<string, unknown>)[k], depth + 1)}`).join(",")}${keys.length > 14 ? ",..." : ""}}`;
  }
  if (typeof v === "string") return `str"${v.slice(0, 20)}"`;
  return String(v);
}

// 1) 验签失败（sign/salt 均空）→ Invalid sign salt.
{
  const body = new URLSearchParams();
  body.set("data", encode({ method_name: "home", package_name: packageName, salt: "", sign: "" }));
  const res = await fetch(BASE, { method: "POST", body });
  console.log("[sign-fail]", res.status, (await res.text()).slice(0, 120));
}

// 2) 未知 method → 空响应体
{
  const r = await call("no_such_method");
  console.log("[unknown]", r.http, r.json === undefined ? "empty body" : "unexpected json");
}

// 3) 全部读类 method
const readCases: [string, Record<string, unknown>][] = [
  ["home", {}],
  ["home_new", {}],
  ["all_songs", { post_id: 1 }],
  ["latest", {}],
  ["banners", {}],
  ["banner_songs", { banner_id: String(banner?.id) }],
  ["cat_list", {}],
  ["cat_songs", { cat_id: String(cat?.id), order_by: "id", order: "ASC" }],
  ["recent_artist_list", {}],
  ["artist_list", { order_by: "id", search_value: "" }],
  ["artist_album_list", { artist_id: String(artist?.id) }],
  ["artist_name_songs", { artist_name: String(artist?.name) }],
  ["album_list", { order_by: "id" }],
  ["album_songs", { album_id: String(album?.id) }],
  ["playlist", {}],
  ["playlist_songs", { playlist_id: String(pl?.id) }],
  ["song_info", { song_id: String(song?.id) }],
  ["single_song", { song_id: String(song?.id) }],
  ["song_search", { search_value: "a" }],
  ["app_details", {}],
];

for (const [m, extra] of readCases) {
  const r = await call(m, extra);
  console.log(`[${m}]`, r.http, r.json === undefined ? r.raw : summarize(r.json));
}

// 4) 写路径：注册→登录（Normal 注册响应按旧契约不含 user_id，需登录取）→评分→收藏→下载
const email = `smoke-${Date.now()}@test.local`;
const reg = await call("user_register", { name: "smoke", email, password: "123456", auth_id: "", auth_type: "" });
console.log("[user_register]", summarize(reg.json));
const login = await call("user_login", { email, password: "123456", auth_id: "", type: "normal" });
console.log("[user_login]", summarize(login.json));
const uid = (login.json as { ONLINE_MP3: [{ user_id?: string }] }).ONLINE_MP3[0]?.user_id;
if (uid) {
  console.log("[user_profile]", summarize((await call("user_profile", { user_id: uid })).json));
  console.log("[song_rating]", summarize((await call("song_rating", { post_id: String(song?.id), rate: "5", user_id: uid, ip: "10.0.0.9" })).json));
  console.log("[song_rating-dup]", summarize((await call("song_rating", { post_id: String(song?.id), rate: "3", user_id: uid, ip: "10.0.0.9" })).json));
  console.log("[favourite_post]", summarize((await call("favourite_post", { user_id: uid, post_id: String(song?.id) })).json));
  console.log("[get_favourite_post]", summarize((await call("get_favourite_post", { user_id: uid })).json));
  console.log("[get_recent_songs]", summarize((await call("get_recent_songs", { user_id: uid, songs_ids: String(song?.id) })).json));
}
console.log("[song_download]", summarize((await call("song_download", { song_id: String(song?.id) })).json));

// 5) user_id 无效 → -2
console.log("[bad-user]", summarize((await call("favourite_post", { user_id: "999999", post_id: String(song?.id) })).json));
