// 一次性迁移脚本（ADR 0003 / Q15）：旧 PHP 库 → 新库
// 迁移范围：内容实体（保留原 ID）+ 全局设置 + 媒体文件
// 不迁移：用户 / 收藏 / 评分 / 举报 / 建议 / 活跃日志 / 播放统计（Q15 决定）
//
// 使用步骤：
//   1. 配置 server/.env 的 DATABASE_URL（新库）与 LEGACY_DB_URL（旧库）
//   2. pnpm prisma:deploy（按 schema 建新库表）
//   3. pnpm migrate:legacy [--media-src=旧backend目录]
import "dotenv/config";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import mysql from "mysql2/promise";
import bcrypt from "bcryptjs";
import { PrismaClient } from "@prisma/client";
import { putObject } from "../src/media/oss.js";

// ---------------------------------------------------------------- 参数与环境

const MEDIA_SRC = (() => {
  const arg = process.argv.find((a) => a.startsWith("--media-src="));
  // 默认相对本 scripts/ 目录向上三级：workspace 根的 backend/（PHP 站点目录）
    return arg ? path.resolve(arg.split("=")[1]) : path.resolve(import.meta.dirname, "../../../backend");
})();

const legacy = await mysql.createConnection({
  uri: process.env.LEGACY_DB_URL ?? "",
  multipleStatements: false,
});
const prisma = new PrismaClient();

async function rows<T = Record<string, unknown>>(sql: string): Promise<T[]> {
  const [rs] = await legacy.query(sql);
  return rs as T[];
}

const str = (v: unknown) => (v == null ? "" : String(v)).trim();
const bool = (v: unknown) => str(v) === "1" || /^true$/i.test(str(v));
const int = (v: unknown) => {
  const n = Number.parseInt(str(v), 10);
  return Number.isFinite(n) ? n : 0;
};
const basename = (v: string) => v.split(/[\\/]/).pop() ?? v;

let warnings = 0;
const warn = (msg: string) => {
  warnings++;
  console.warn("  [warn] " + msg);
};

// ---------------------------------------------------------------- 1. 设置

/** 旧排序值引用旧列名（cid / mp3_title / aid ...），映射为现代列名 */
const ORDER_FIELD: Record<string, string> = {
  cid: "id",
  id: "id",
  aid: "id",
  category_name: "name",
  name: "name",
  album_name: "name",
  artist_name: "name",
  mp3_title: "title",
  title: "title",
};

function mapOrderBy(raw: string, fallbackField: string): string {
  const [field = "", dir = ""] = raw.trim().split(/\s+/);
  return `${ORDER_FIELD[field] ?? fallbackField} ${/desc/i.test(dir) ? "DESC" : "ASC"}`;
}

async function migrateSettings(): Promise<void> {
  const [s] = await rows("SELECT * FROM tbl_settings WHERE id = 1");
  if (!s) {
    warn("旧库无 tbl_settings 记录，跳过设置迁移");
    return;
  }
  await prisma.setting.upsert({
    where: { id: 1 },
    update: {
      packageName: str(s.package_name),
      onesignalAppId: str(s.onesignal_app_id),
      onesignalRestKey: str(s.onesignal_rest_key),
      appName: str(s.app_name),
      appLogo: str(s.app_logo),
      appEmail: str(s.app_email),
      appVersion: str(s.app_version),
      appAuthor: str(s.app_author),
      appContact: str(s.app_contact),
      appWebsite: str(s.app_website),
      appDescription: str(s.app_description),
      appDevelopedBy: str(s.app_developed_by),
      appPrivacyPolicy: str(s.app_privacy_policy),
      apiLatestLimit: int(s.api_latest_limit) || 10,
      apiCatOrderBy: mapOrderBy(str(s.api_cat_order_by), "id"),
      apiCatPostOrderBy: mapOrderBy(str(s.api_cat_post_order_by), "id"),
      publisherId: str(s.publisher_id),
      interstitalAd: str(s.interstital_ad),
      interstitalAdId: str(s.interstital_ad_id),
      interstitalAdClick: str(s.interstital_ad_click),
      bannerAd: str(s.banner_ad),
      bannerAdId: str(s.banner_ad_id),
      bannerAdType: str(s.banner_ad_type) || "admob",
      bannerFacebookId: str(s.banner_facebook_id),
      interstitalAdType: str(s.interstital_ad_type) || "admob",
      interstitalFacebookId: str(s.interstital_facebook_id),
      nativeAd: str(s.native_ad) || "false",
      nativeAdType: str(s.native_ad_type) || "admob",
      nativeAdId: str(s.native_ad_id),
      nativeFacebookId: str(s.native_facebook_id),
      nativePosition: int(s.native_position) || 5,
      appUpdateStatus: str(s.app_update_status) || "false",
      appNewVersion: Number(str(s.app_new_version)) || 1,
      appUpdateDesc: str(s.app_update_desc),
      appRedirectUrl: str(s.app_redirect_url),
      cancelUpdateStatus: str(s.cancel_update_status) || "false",
      songDownload: str(s.song_download) || "true",
    },
    create: {
      id: 1,
      packageName: str(s.package_name),
      onesignalAppId: str(s.onesignal_app_id),
      onesignalRestKey: str(s.onesignal_rest_key),
      appName: str(s.app_name),
      appLogo: str(s.app_logo),
      appEmail: str(s.app_email),
      appVersion: str(s.app_version),
      appAuthor: str(s.app_author),
      appContact: str(s.app_contact),
      appWebsite: str(s.app_website),
      appDescription: str(s.app_description),
      appDevelopedBy: str(s.app_developed_by),
      appPrivacyPolicy: str(s.app_privacy_policy),
      apiLatestLimit: int(s.api_latest_limit) || 10,
      apiCatOrderBy: mapOrderBy(str(s.api_cat_order_by), "id"),
      apiCatPostOrderBy: mapOrderBy(str(s.api_cat_post_order_by), "id"),
      publisherId: str(s.publisher_id),
      interstitalAd: str(s.interstital_ad),
      interstitalAdId: str(s.interstital_ad_id),
      interstitalAdClick: str(s.interstital_ad_click),
      bannerAd: str(s.banner_ad),
      bannerAdId: str(s.banner_ad_id),
      bannerAdType: str(s.banner_ad_type) || "admob",
      bannerFacebookId: str(s.banner_facebook_id),
      interstitalAdType: str(s.interstital_ad_type) || "admob",
      interstitalFacebookId: str(s.interstital_facebook_id),
      nativeAd: str(s.native_ad) || "false",
      nativeAdType: str(s.native_ad_type) || "admob",
      nativeAdId: str(s.native_ad_id),
      nativeFacebookId: str(s.native_facebook_id),
      nativePosition: int(s.native_position) || 5,
      appUpdateStatus: str(s.app_update_status) || "false",
      appNewVersion: Number(str(s.app_new_version)) || 1,
      appUpdateDesc: str(s.app_update_desc),
      appRedirectUrl: str(s.app_redirect_url),
      cancelUpdateStatus: str(s.cancel_update_status) || "false",
      songDownload: str(s.song_download) || "true",
    },
  });
  console.log("settings: 迁移完成（envato / email_from 列已按决策丢弃）");
}

// ---------------------------------------------------------------- 2. 内容实体

async function migrateCategories(): Promise<void> {
  const list = await rows("SELECT * FROM tbl_category");
  for (const c of list) {
    await prisma.category.upsert({
      where: { id: int(c.cid) },
      update: {
        name: str(c.category_name),
        image: basename(str(c.category_image)),
        status: bool(c.status),
      },
      create: {
        id: int(c.cid),
        name: str(c.category_name),
        image: basename(str(c.category_image)),
        status: bool(c.status),
      },
    });
  }
  console.log(`categories: ${list.length} 条`);
}

async function migrateArtists(): Promise<Map<string, number>> {
  const list = await rows("SELECT * FROM tbl_artist");
  for (const a of list) {
    await prisma.artist.upsert({
      where: { id: int(a.id) },
      update: { name: str(a.artist_name), image: basename(str(a.artist_image)) },
      create: { id: int(a.id), name: str(a.artist_name), image: basename(str(a.artist_image)) },
    });
  }
  console.log(`artists: ${list.length} 条`);
  return new Map(list.map((a) => [str(a.artist_name), int(a.id)]));
}

async function migrateAlbums(artistByName: Map<string, number>): Promise<void> {
  const list = await rows("SELECT * FROM tbl_album");
  for (const a of list) {
    const albumId = int(a.aid);
    await prisma.album.upsert({
      where: { id: albumId },
      update: { name: str(a.album_name), image: basename(str(a.album_image)), status: bool(a.status) },
      create: {
        id: albumId,
        name: str(a.album_name),
        image: basename(str(a.album_image)),
        status: bool(a.status),
      },
    });
    // artist_ids 逗号串 → album_artists（缺失的 id 静默跳过）
    const ids = str(a.artist_ids)
      .split(",")
      .map((v) => Number.parseInt(v.trim(), 10))
      .filter((n) => Number.isFinite(n) && n > 0);
    await prisma.albumArtist.deleteMany({ where: { albumId } });
    let sort = 0;
    for (const artistId of ids) {
      const exists = await prisma.artist.findUnique({ where: { id: artistId }, select: { id: true } });
      if (!exists) {
        warn(`album ${albumId}: artist_ids 引用不存在的 artist ${artistId}，已跳过`);
        continue;
      }
      await prisma.albumArtist.create({ data: { albumId, artistId, sort: sort++ } });
    }
  }
  console.log(`albums: ${list.length} 条`);
}

async function migrateSongs(artistByName: Map<string, number>): Promise<void> {
  const list = await rows("SELECT * FROM tbl_mp3");
  for (const s of list) {
    const songId = int(s.id);
    const categoryId = int(s.cat_id);
    const categoryExists = await prisma.category.findUnique({ where: { id: categoryId }, select: { id: true } });
    if (!categoryExists) {
      warn(`song ${songId}: cat_id ${categoryId} 不存在，已跳过`);
      continue;
    }
    const albumRaw = int(s.album_id);
    const albumId = albumRaw > 0 && (await prisma.album.findUnique({ where: { id: albumRaw }, select: { id: true } }))
      ? albumRaw
      : null;

    const type = str(s.mp3_type) || "local";
    // local 类型存文件名（新 audioUrl() 自行拼 uploads/），其余存完整地址
    const audioUrl = type === "local" ? basename(str(s.mp3_url)) : str(s.mp3_url);

    await prisma.song.upsert({
      where: { id: songId },
      update: {
        categoryId,
        albumId,
        type,
        title: str(s.mp3_title),
        audioUrl,
        thumbnail: basename(str(s.mp3_thumbnail)),
        description: str(s.mp3_description),
        totalViews: int(s.total_views),
        totalDownload: int(s.total_download),
        totalRate: int(s.total_rate),
        rateAvg: int(s.rate_avg),
        status: bool(s.status),
      },
      create: {
        id: songId,
        categoryId,
        albumId,
        type,
        title: str(s.mp3_title),
        audioUrl,
        thumbnail: basename(str(s.mp3_thumbnail)),
        description: str(s.mp3_description),
        totalViews: int(s.total_views),
        totalDownload: int(s.total_download),
        totalRate: int(s.total_rate),
        rateAvg: int(s.rate_avg),
        status: bool(s.status),
      },
    });

    // mp3_artist 是艺术家"名字"逗号串 → song_artists；名字缺失时补建 artist
    const names = str(s.mp3_artist)
      .split(",")
      .map((n) => n.trim())
      .filter(Boolean);
    await prisma.songArtist.deleteMany({ where: { songId } });
    let sort = 0;
    for (const name of names) {
      let artistId: number | undefined = artistByName.get(name);
      if (artistId === undefined) {
        const created = await prisma.artist.create({ data: { name, image: "" } });
        artistByName.set(name, created.id);
        artistId = created.id;
        warn(`song ${songId}: 艺术家 "${name}" 不在 tbl_artist，已补建（无图）`);
      }
      await prisma.songArtist.create({ data: { songId, artistId, sort: sort++ } });
    }
  }
  console.log(`songs: ${list.length} 条`);
}

async function migrateBanners(): Promise<void> {
  const list = await rows("SELECT * FROM tbl_banner");
  for (const b of list) {
    const bannerId = int(b.bid);
    await prisma.banner.upsert({
      where: { id: bannerId },
      update: {
        title: str(b.banner_title),
        sortInfo: str(b.banner_sort_info),
        image: basename(str(b.banner_image)),
        status: bool(b.status),
      },
      create: {
        id: bannerId,
        title: str(b.banner_title),
        sortInfo: str(b.banner_sort_info),
        image: basename(str(b.banner_image)),
        status: bool(b.status),
      },
    });
    // banner_songs 逗号串 → banner_songs 关联（保留挂接顺序为 sort）
    const ids = str(b.banner_songs)
      .split(",")
      .map((v) => Number.parseInt(v.trim(), 10))
      .filter((n) => Number.isFinite(n) && n > 0);
    await prisma.bannerSong.deleteMany({ where: { bannerId } });
    let sort = 0;
    for (const songId of ids) {
      const exists = await prisma.song.findUnique({ where: { id: songId }, select: { id: true } });
      if (!exists) {
        warn(`banner ${bannerId}: banner_songs 引用不存在的歌曲 ${songId}，已跳过`);
        continue;
      }
      await prisma.bannerSong.create({ data: { bannerId, songId, sort: sort++ } });
    }
  }
  console.log(`banners: ${list.length} 条`);
}

async function migratePlaylists(): Promise<void> {
  const list = await rows("SELECT * FROM tbl_playlist");
  for (const p of list) {
    const playlistId = int(p.pid);
    await prisma.playlist.upsert({
      where: { id: playlistId },
      update: {
        name: str(p.playlist_name),
        image: basename(str(p.playlist_image)),
        status: bool(p.status),
      },
      create: {
        id: playlistId,
        name: str(p.playlist_name),
        image: basename(str(p.playlist_image)),
        status: bool(p.status),
      },
    });
    // 旧库实际存 tbl_playlist.playlist_songs 逗号字段 → playlist_songs 关联
    const ids = str(p.playlist_songs)
      .split(",")
      .map((v) => Number.parseInt(v.trim(), 10))
      .filter((n) => Number.isFinite(n) && n > 0);
    await prisma.playlistSong.deleteMany({ where: { playlistId } });
    let sort = 0;
    for (const songId of ids) {
      const exists = await prisma.song.findUnique({ where: { id: songId }, select: { id: true } });
      if (!exists) {
        warn(`playlist ${playlistId}: 引用不存在的歌曲 ${songId}，已跳过`);
        continue;
      }
      await prisma.playlistSong.create({ data: { playlistId, songId, sort: sort++ } });
    }
  }
  console.log(`playlists: ${list.length} 条`);
}

// ---------------------------------------------------------------- 3. 媒体文件与初始管理员

/** 旧站点媒体文件按原路径形态上传 OSS（ADR 0004：key 与新上传一致，含 thumbs） */
async function copyMedia(): Promise<void> {
  for (const dir of ["images", "images/thumbs", "uploads", "lrc"]) {
    const src = path.join(MEDIA_SRC, dir);
    if (!fs.existsSync(src)) {
      warn(`媒体目录不存在，跳过: ${src}`);
      continue;
    }
    const files = fs.readdirSync(src, { withFileTypes: true }).filter((e) => e.isFile());
    for (const f of files) {
      const body = await fs.promises.readFile(path.join(src, f.name));
      await putObject(`${dir}/${f.name}`, body);
    }
    console.log(`media: ${dir}/ 已上传 ${files.length} 个对象`);
  }
}

async function seedAdmin(): Promise<void> {
  const count = await prisma.adminUser.count();
  if (count > 0) {
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

try {
  console.log(`legacy db: ${legacy.config.host ?? "(uri)"}`);
  console.log(`media: ${MEDIA_SRC} → OSS`);
  await migrateSettings();
  await migrateCategories();
  const artistByName = await migrateArtists();
  await migrateAlbums(artistByName);
  await migrateSongs(artistByName);
  await migrateBanners();
  await migratePlaylists();
  await copyMedia();
  await seedAdmin();
  console.log(`\n迁移完成${warnings ? `，共 ${warnings} 条警告（见上）` : "，无警告"}`);
} finally {
  await legacy.end();
  await prisma.$disconnect();
}
