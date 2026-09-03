// 把 fetch-prod.ts 导出的 JSON dump 导入空的新库（ADR 0006）
// 前置：pnpm prisma:deploy（按 schema 建表）；目标库应为空（Q6）
// 用法：pnpm import:prod
//
// 边界（Q2 拷问结论，ADR 0006）：
//   - 歌曲一律 type=external + prod 完整 URL（阶段一）；图片/lrcUrl 存 key（basename），OSS 待 fill-media.ts 补齐
//   - 专辑→艺术家关联 API 不暴露，留空（割接后后台手工补）
//   - 横幅挂歌顺序取 banner_songs 接口返回序（近似）
//   - 下线内容（status=0）API 不返回，不迁；用户/举报/建议不迁
//   - settings 仅 app_details 子集，onesignal/api_latest_limit/排序规则导入后手工配
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();
const DUMP_DIR = path.resolve(import.meta.dirname, "dump");

type Row = Record<string, string> & { song_ids?: string[] };
type LrcMap = Record<string, { txt: string; url: string }>;

function load<T>(name: string): T {
  const file = path.join(DUMP_DIR, name);
  if (!fs.existsSync(file)) throw new Error(`缺少 dump 文件 ${name}，请先运行 pnpm fetch:prod`);
  return JSON.parse(fs.readFileSync(file, "utf8")) as T;
}

const str = (v: unknown) => (v == null ? "" : String(v)).trim();
const int = (v: unknown) => {
  const n = Number.parseInt(str(v), 10);
  return Number.isFinite(n) ? n : 0;
};
/** 响应里的图片/歌词只有完整 URL，落库只存 key（basename） */
const basename = (v: unknown) => str(v).split(/[\\/]/).pop() ?? "";

let warnings = 0;
const warn = (msg: string) => {
  warnings++;
  console.warn("  [warn] " + msg);
};

// ---------------------------------------------------------------- 1. 设置（app_details 子集 + 缺省值）

async function importSettings(): Promise<void> {
  // app_details 的 ONLINE_MP3 是数组（api.php while 推入），取首元素
  const s = load<Row[] | Row>("settings.json");
  const row = Array.isArray(s) ? s[0] : s;
  if (!row) throw new Error("settings.json 为空，请重新 pnpm fetch:prod");
  const data = {
    packageName: str(row["package_name"]),
    onesignalAppId: "", // API 不暴露，后台手工配
    onesignalRestKey: "",
    appName: str(row["app_name"]),
    appLogo: basename(row["app_logo"]),
    appEmail: str(row["app_email"]),
    appVersion: str(row["app_version"]),
    appAuthor: str(row["app_author"]),
    appContact: str(row["app_contact"]),
    appWebsite: str(row["app_website"]),
    appDescription: str(row["app_description"]),
    appDevelopedBy: str(row["app_developed_by"]),
    appPrivacyPolicy: str(row["app_privacy_policy"]),
    apiLatestLimit: 10, // API 不暴露，取旧端点默认页大小
    apiCatOrderBy: "id ASC", // API 不暴露，取常见缺省
    apiCatPostOrderBy: "id ASC",
    publisherId: str(row["publisher_id"]),
    interstitalAd: str(row["interstital_ad"]),
    interstitalAdId: str(row["interstital_ad_id"]),
    interstitalAdClick: str(row["interstital_ad_click"]),
    bannerAd: str(row["banner_ad"]),
    bannerAdId: str(row["banner_ad_id"]),
    bannerAdType: str(row["banner_ad_type"]) || "admob",
    bannerFacebookId: "",
    interstitalAdType: str(row["interstital_ad_type"]) || "admob",
    interstitalFacebookId: "",
    nativeAd: str(row["native_ad"]) || "false",
    nativeAdType: str(row["native_ad_type"]) || "admob",
    nativeAdId: str(row["native_ad_id"]),
    nativeFacebookId: "",
    nativePosition: int(row["native_position"]) || 5,
    appUpdateStatus: str(row["app_update_status"]) || "false",
    appNewVersion: Number(str(row["app_new_version"])) || 1,
    appUpdateDesc: str(row["app_update_desc"]),
    appRedirectUrl: str(row["app_redirect_url"]),
    cancelUpdateStatus: str(row["cancel_update_status"]) || "false",
    songDownload: str(row["song_download"]) || "true",
  };
  await prisma.setting.upsert({ where: { id: 1 }, update: data, create: { id: 1, ...data } });
  console.log("settings: 导入完成（onesignal / api_latest_limit / 排序规则请后台手工补配）");
}

// ---------------------------------------------------------------- 2. 内容实体

async function importCategories(): Promise<void> {
  const list = load<Row[]>("categories.json");
  for (const c of list) {
    await prisma.category.upsert({
      where: { id: int(c["cid"]) },
      update: { name: str(c["category_name"]), image: basename(c["category_image"]), status: true },
      create: { id: int(c["cid"]), name: str(c["category_name"]), image: basename(c["category_image"]), status: true },
    });
  }
  console.log(`categories: ${list.length} 条`);
}

async function importArtists(): Promise<Map<string, number>> {
  const list = load<Row[]>("artists.json");
  for (const a of list) {
    await prisma.artist.upsert({
      where: { id: int(a["id"]) },
      update: { name: str(a["artist_name"]), image: basename(a["artist_image"]) },
      create: { id: int(a["id"]), name: str(a["artist_name"]), image: basename(a["artist_image"]) },
    });
  }
  console.log(`artists: ${list.length} 条`);
  return new Map(list.map((a) => [str(a["artist_name"]), int(a["id"])]));
}

/** 专辑→艺术家关联 API 不暴露，留空（ADR 0006，割接后手工补） */
async function importAlbums(): Promise<void> {
  const list = load<Row[]>("albums.json");
  for (const a of list) {
    await prisma.album.upsert({
      where: { id: int(a["aid"]) },
      update: { name: str(a["album_name"]), image: basename(a["album_image"]), status: true },
      create: { id: int(a["aid"]), name: str(a["album_name"]), image: basename(a["album_image"]), status: true },
    });
  }
  console.log(`albums: ${list.length} 条（artist 关联留空待手工补）`);
}

async function importSongs(artistByName: Map<string, number>): Promise<void> {
  const list = load<Row[]>("songs.json");
  const songAlbum = load<Record<string, string>>("album-songs.json");
  const lrc = load<LrcMap>("lrc.json");
  for (const s of list) {
    const songId = int(s["id"]);
    const categoryId = int(s["cat_id"]);
    if (!(await prisma.category.findUnique({ where: { id: categoryId }, select: { id: true } }))) {
      warn(`song ${songId}: cat_id ${categoryId} 不在已导入分类中（prod 分类已下架？），已跳过`);
      continue;
    }
    const albumRaw = int(songAlbum[String(songId)] ?? "0");
    const albumId =
      albumRaw > 0 && (await prisma.album.findUnique({ where: { id: albumRaw }, select: { id: true } }))
        ? albumRaw
        : null;

    // 阶段一：一律 external + prod 完整 URL；「转入 OSS」面板功能负责收尾（ADR 0006）
    const lrcEntry = lrc[String(songId)];
    const lrcUrlKey = lrcEntry ? basename(lrcEntry.url) : "";
    await prisma.song.upsert({
      where: { id: songId },
      update: {
        categoryId,
        albumId,
        type: "external",
        title: str(s["mp3_title"]),
        audioUrl: str(s["mp3_url"]),
        thumbnail: basename(s["mp3_thumbnail_b"]),
        description: str(s["mp3_description"]),
        ...(lrcEntry ? { lrcText: lrcEntry.txt || null, ...(lrcUrlKey ? { lrcUrl: lrcUrlKey } : {}) } : {}),
        totalViews: int(s["total_views"]),
        totalDownload: int(s["total_download"]),
        totalRate: int(s["total_rate"]),
        rateAvg: int(s["rate_avg"]),
        status: true,
      },
      create: {
        id: songId,
        categoryId,
        albumId,
        type: "external",
        title: str(s["mp3_title"]),
        audioUrl: str(s["mp3_url"]),
        thumbnail: basename(s["mp3_thumbnail_b"]),
        description: str(s["mp3_description"]),
        ...(lrcEntry ? { lrcText: lrcEntry.txt || null, ...(lrcUrlKey ? { lrcUrl: lrcUrlKey } : {}) } : {}),
        totalViews: int(s["total_views"]),
        totalDownload: int(s["total_download"]),
        totalRate: int(s["total_rate"]),
        rateAvg: int(s["rate_avg"]),
        status: true,
      },
    });

    // mp3_artist 是艺术家名字逗号串 → song_artists；名字缺失时补建（同 migrate:legacy 行为）
    const names = str(s["mp3_artist"])
      .split(",")
      .map((n) => n.trim())
      .filter(Boolean);
    await prisma.songArtist.deleteMany({ where: { songId } });
    let sort = 0;
    for (const name of names) {
      let artistId = artistByName.get(name);
      if (artistId === undefined) {
        const created = await prisma.artist.create({ data: { name, image: "" } });
        artistByName.set(name, created.id);
        artistId = created.id;
        warn(`song ${songId}: 艺术家 "${name}" 不在 prod 艺术家列表，已补建（无图）`);
      }
      await prisma.songArtist.create({ data: { songId, artistId, sort: sort++ } });
    }
  }
  console.log(`songs: ${list.length} 条（全部 external，待面板转入 OSS）`);
}

async function importPlaylists(): Promise<void> {
  const list = load<Row[]>("playlists.json");
  for (const p of list) {
    const playlistId = int(p["pid"]);
    await prisma.playlist.upsert({
      where: { id: playlistId },
      update: { name: str(p["playlist_name"]), image: basename(p["playlist_image"]), status: true },
      create: { id: playlistId, name: str(p["playlist_name"]), image: basename(p["playlist_image"]), status: true },
    });
    const ids = p["song_ids"] ?? [];
    await prisma.playlistSong.deleteMany({ where: { playlistId } });
    let sort = 0;
    for (const raw of ids) {
      const songId = int(raw);
      if (!(await prisma.song.findUnique({ where: { id: songId }, select: { id: true } }))) {
        warn(`playlist ${playlistId}: 引用不存在的歌曲 ${songId}，已跳过`);
        continue;
      }
      await prisma.playlistSong.create({ data: { playlistId, songId, sort: sort++ } });
    }
  }
  console.log(`playlists: ${list.length} 条`);
}

async function importBanners(): Promise<void> {
  const list = load<Row[]>("banners.json");
  for (const b of list) {
    const bannerId = int(b["bid"]);
    await prisma.banner.upsert({
      where: { id: bannerId },
      update: {
        title: str(b["banner_title"]),
        sortInfo: str(b["banner_sort_info"]),
        link: str(b["banner_link"]) || null,
        image: basename(b["banner_image"]),
        status: true,
      },
      create: {
        id: bannerId,
        title: str(b["banner_title"]),
        sortInfo: str(b["banner_sort_info"]),
        link: str(b["banner_link"]) || null,
        image: basename(b["banner_image"]),
        status: true,
      },
    });
    // 顺序取 banner_songs 接口返回序（API_CAT_POST_ORDER_BY，可能与原挂接顺序有偏差，ADR 0006）
    const ids = b["song_ids"] ?? [];
    await prisma.bannerSong.deleteMany({ where: { bannerId } });
    let sort = 0;
    for (const raw of ids) {
      const songId = int(raw);
      if (!(await prisma.song.findUnique({ where: { id: songId }, select: { id: true } }))) {
        warn(`banner ${bannerId}: 引用不存在的歌曲 ${songId}，已跳过`);
        continue;
      }
      await prisma.bannerSong.create({ data: { bannerId, songId, sort: sort++ } });
    }
  }
  console.log(`banners: ${list.length} 条`);
}

/** 热门榜初值：prod home_new.trending_songs 返回序（近一月播放量算榜的快照，ADR 0006） */
async function importTrending(): Promise<void> {
  const ids = load<string[]>("trending.json");
  await prisma.trendingSong.deleteMany();
  let sort = 0;
  for (const raw of ids) {
    const songId = int(raw);
    if (!(await prisma.song.findUnique({ where: { id: songId }, select: { id: true } }))) {
      warn(`trending: 引用不存在的歌曲 ${songId}，已跳过`);
      continue;
    }
    await prisma.trendingSong.create({ data: { songId, sort: sort++ } });
  }
  console.log(`trending: ${sort} 条`);
}

// ---------------------------------------------------------------- 3. 初始管理员（空库必需）

async function seedAdmin(): Promise<void> {
  if ((await prisma.adminUser.count()) > 0) {
    console.log("admin: 已存在管理员，跳过");
    return;
  }
  const password = crypto.randomBytes(12).toString("base64url");
  await prisma.adminUser.create({
    data: { username: "admin", password: await bcrypt.hash(password, 10) },
  });
  console.log("admin: 初始管理员已创建 → 用户名 admin，密码（仅此一次打印）: " + password);
}

// ---------------------------------------------------------------- main

console.log(`import: dump@${DUMP_DIR}`);
await importSettings();
await importCategories();
const artistByName = await importArtists();
await importAlbums();
await importSongs(artistByName);
await importPlaylists();
await importBanners();
await importTrending();
await seedAdmin();
console.log(`\n导入完成${warnings ? `，共 ${warnings} 条警告（见上）` : "，无警告"}`);
console.log("下一步：pnpm fill:media 补齐 OSS 图片/歌词，再在后台对歌曲批量「转入 OSS」");
await prisma.$disconnect();
