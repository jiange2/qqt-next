// Admin API —— REST 端点（Q6：内容 CRUD + 设置 + 热门榜 + 用户 + OneSignal 推送）
// 砍除：Envato 验证、RichFileManager（Q4）；邮件相关全部不设（Q27）
import crypto from "node:crypto";
import path from "node:path";
import bcrypt from "bcryptjs";
import {
  type FastifyInstance,
  type FastifyReply,
  type FastifyRequest,
} from "fastify";
import type { MultipartFile } from "@fastify/multipart";
import { z } from "zod";
import { prisma } from "../prisma.js";
import { invalidateSettingsCache } from "../services/settings.js";
import { saveImage } from "../media/save.js";
import { mediaBase } from "../media/urls.js";
import { putObject, putStream } from "../media/oss.js";
import { requireAdmin, signAdminToken } from "./auth.js";

// ---------------------------------------------------------------- 工具

const str = (v: unknown) => (v == null ? "" : String(v)).trim();
const intOr = (v: unknown, fallback: number) => {
  const n = Number.parseInt(str(v), 10);
  return Number.isFinite(n) ? n : fallback;
};
const parseIds = (v: string): number[] =>
  v
    .split(",")
    .map((s) => Number.parseInt(s.trim(), 10))
    .filter((n) => Number.isFinite(n) && n > 0);

function paging(req: FastifyRequest): { page: number; size: number; skip: number } {
  const q = req.query as Record<string, string | undefined>;
  const page = Math.max(1, intOr(q["page"], 1));
  const size = Math.min(200, Math.max(1, intOr(q["size"], 20)));
  return { page, size, skip: (page - 1) * size };
}

function bad(reply: FastifyReply, message: string): FastifyReply {
  return reply.code(400).send({ error: message });
}

type FormData = {
  fields: Record<string, string>;
  saved: Record<string, string>; // fieldname → 上传后的对象 key 文件名
};

/**
 * 解析 multipart 表单。文件按 fieldname 分派：
 * streamHandler 返回非空文件名表示"已流式落盘"（大文件），否则收集 buffer 由调用方处理
 */
async function parseForm(
  req: FastifyRequest,
  streamHandler?: (fieldname: string, part: MultipartFile) => Promise<string | null>,
): Promise<FormData> {
  const fields: Record<string, string> = {};
  const saved: Record<string, string> = {};
  const buffers: Record<string, { buffer: Buffer; name: string }> = {};

  for await (const part of req.parts()) {
    if (part.type === "file") {
      if (streamHandler) {
        const name = await streamHandler(part.fieldname, part);
        if (name) {
          saved[part.fieldname] = name;
          continue;
        }
      }
      buffers[part.fieldname] = { buffer: await part.toBuffer(), name: part.filename || "file" };
    } else {
      fields[part.fieldname] = String(part.value ?? "");
    }
  }
  // 图片字段走 sharp 压缩后直传 OSS
  for (const [fieldname, f] of Object.entries(buffers)) {
    if (f.buffer.length > 20 * 1024 * 1024) {
      throw new Error(`Image too large (max 20MB): ${fieldname}`);
    }
    saved[fieldname] = fieldname === "lrc_file" || fieldname === "lrc"
      ? await saveTextFile(f.buffer, f.name)
      : await saveImage(f.buffer, f.name);
  }
  return { fields, saved };
}

/** 小文本文件（lrc）上传到 OSS（key = lrc/<name>） */
async function saveTextFile(buffer: Buffer, originalName: string): Promise<string> {
  const ext = path.extname(originalName).toLowerCase() || ".lrc";
  const name = `${Date.now()}_${crypto.randomBytes(4).toString("hex")}${ext}`;
  await putObject(`lrc/${name}`, buffer);
  return name;
}

/** 音频流式直传 OSS（ADR 0004；Q19：≤500MB，limits 由 app 层 multipart 配置控制） */
async function saveAudioStream(part: MultipartFile): Promise<string> {
  const ext = path.extname(part.filename || "").toLowerCase() || ".mp3";
  const name = `${Date.now()}_${crypto.randomBytes(4).toString("hex")}${ext}`;
  await putStream(`uploads/${name}`, part.file);
  return name;
}

// ---------------------------------------------------------------- 登录 / 账户

const loginSchema = z.object({ username: z.string().min(1), password: z.string().min(1) });

async function login(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "Invalid body");
  const { username, password } = parsed.data;
  const admin = await prisma.adminUser.findUnique({ where: { username } });
  if (!admin || !(await bcrypt.compare(password, admin.password))) {
    await reply.code(401).send({ error: "Invalid credentials" });
    return;
  }
  reply.send({
    token: signAdminToken({ sub: admin.id, username: admin.username }),
    username: admin.username,
  });
}

const passwordSchema = z.object({
  old_password: z.string().min(1),
  new_password: z.string().min(8),
});

/** 媒体基地址下发：面板图片 URL 运行时拼接（换 CDN 只改服务端 OSS_PUBLIC_BASE，无需重建前端） */

async function changePassword(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const parsed = passwordSchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "New password must be at least 8 characters");
  const adminId = (req as FastifyRequest & { admin: { sub: number } }).admin.sub;
  const admin = await prisma.adminUser.findUnique({ where: { id: adminId } });
  if (!admin || !(await bcrypt.compare(parsed.data.old_password, admin.password))) {
    await reply.code(400).send({ error: "Old password incorrect" });
    return;
  }
  await prisma.adminUser.update({
    where: { id: adminId },
    data: { password: await bcrypt.hash(parsed.data.new_password, 10) },
  });
  reply.send({ ok: true });
}

// ---------------------------------------------------------------- 分类 / 艺术家

async function upsertCategory(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req);
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  const data = {
    name,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    ...(saved["image"] ? { image: saved["image"] } : {}),
  };
  const id = intOr(fields["id"], 0);
  reply.send(
    id > 0
      ? await prisma.category.update({ where: { id }, data })
      : await prisma.category.create({ data: { ...data, image: data.image ?? "" } }),
  );
}

async function upsertArtist(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req);
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  const data = { name, ...(saved["image"] ? { image: saved["image"] } : {}) };
  const id = intOr(fields["id"], 0);
  reply.send(
    id > 0
      ? await prisma.artist.update({ where: { id }, data })
      : await prisma.artist.create({ data: { ...data, image: data.image ?? "" } }),
  );
}

// ---------------------------------------------------------------- 专辑

async function upsertAlbum(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req);
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  const artistIds = parseIds(str(fields["artist_ids"]));
  for (const artistId of artistIds) {
    if (!(await prisma.artist.findUnique({ where: { id: artistId } }))) {
      return void bad(reply, `artist ${artistId} not found`);
    }
  }
  const data = {
    name,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    ...(saved["image"] ? { image: saved["image"] } : {}),
  };
  const id = intOr(fields["id"], 0);
  const album = id > 0
    ? await prisma.album.update({ where: { id }, data })
    : await prisma.album.create({ data: { ...data, image: data.image ?? "" } });

  if (fields["artist_ids"] !== undefined) {
    await prisma.albumArtist.deleteMany({ where: { albumId: album.id } });
    await prisma.albumArtist.createMany({
      data: artistIds.map((artistId, sort) => ({ albumId: album.id, artistId, sort })),
    });
  }
  reply.send(album);
}

// ---------------------------------------------------------------- 歌曲

const songTypes = new Set(["local", "youtube", "external"]);

async function upsertSong(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  let form: FormData;
  try {
    form = await parseForm(req, (fieldname, part) =>
      fieldname === "audio" ? saveAudioStream(part) : Promise.resolve(null),
    );
  } catch (e) {
    return void bad(reply, e instanceof Error ? e.message : "upload failed");
  }
  const { fields, saved } = form;

  const type = str(fields["type"]) || "local";
  if (!songTypes.has(type)) return void bad(reply, "type must be local | youtube | external");
  const categoryId = intOr(fields["category_id"], 0);
  if (!(await prisma.category.findUnique({ where: { id: categoryId } }))) {
    return void bad(reply, "category not found");
  }
  const title = str(fields["title"]);
  if (!title) return void bad(reply, "title required");

  const albumRaw = intOr(fields["album_id"], 0);
  const albumId =
    albumRaw > 0 && (await prisma.album.findUnique({ where: { id: albumRaw } })) ? albumRaw : null;

  const artistIds = parseIds(str(fields["artist_ids"]));
  for (const artistId of artistIds) {
    if (!(await prisma.artist.findUnique({ where: { id: artistId } }))) {
      return void bad(reply, `artist ${artistId} not found`);
    }
  }

  const data = {
    categoryId,
    albumId,
    type,
    title,
    // local 用上传文件名；youtube/external 用地址字段
    audioUrl: type === "local" ? saved["audio"] ?? str(fields["audio_url"]) : str(fields["audio_url"]),
    thumbnail: saved["thumbnail"] ?? str(fields["thumbnail"]),
    description: fields["description"] ?? "",
    lrcText: fields["lrc_text"] ? str(fields["lrc_text"]) : null,
    lrcUrl: saved["lrc"] ?? (fields["lrc_url"] ? str(fields["lrc_url"]) : null),
    status: fields["status"] !== "0" && fields["status"] !== "false",
  };
  if (type === "local" && !data.audioUrl) return void bad(reply, "audio file or audio_url required");

  const id = intOr(fields["id"], 0);
  const song = id > 0
    ? await prisma.song.update({ where: { id }, data })
    : await prisma.song.create({ data });

  if (fields["artist_ids"] !== undefined) {
    await prisma.songArtist.deleteMany({ where: { songId: song.id } });
    await prisma.songArtist.createMany({
      data: artistIds.map((artistId, sort) => ({ songId: song.id, artistId, sort })),
    });
  }
  reply.send(song);
}

// ---------------------------------------------------------------- 横幅 / 播放列表

async function upsertBanner(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req);
  const title = str(fields["title"]);
  if (!title) return void bad(reply, "title required");
  const songIds = parseIds(str(fields["song_ids"]));
  for (const songId of songIds) {
    if (!(await prisma.song.findUnique({ where: { id: songId } }))) {
      return void bad(reply, `song ${songId} not found`);
    }
  }
  const data = {
    title,
    sortInfo: fields["sort_info"] ?? "",
    link: fields["link"] ? str(fields["link"]) : null,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    ...(saved["image"] ? { image: saved["image"] } : {}),
  };
  const id = intOr(fields["id"], 0);
  const banner = id > 0
    ? await prisma.banner.update({ where: { id }, data })
    : await prisma.banner.create({ data: { ...data, image: data.image ?? "" } });

  if (fields["song_ids"] !== undefined) {
    await prisma.bannerSong.deleteMany({ where: { bannerId: banner.id } });
    await prisma.bannerSong.createMany({
      data: songIds.map((songId, sort) => ({ bannerId: banner.id, songId, sort })),
    });
  }
  reply.send(banner);
}

async function upsertPlaylist(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req);
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  const songIds = parseIds(str(fields["song_ids"]));
  const data = {
    name,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    ...(saved["image"] ? { image: saved["image"] } : {}),
  };
  const id = intOr(fields["id"], 0);
  const playlist = id > 0
    ? await prisma.playlist.update({ where: { id }, data })
    : await prisma.playlist.create({ data: { ...data, image: data.image ?? "" } });

  if (fields["song_ids"] !== undefined) {
    await prisma.playlistSong.deleteMany({ where: { playlistId: playlist.id } });
    await prisma.playlistSong.createMany({
      data: songIds.map((songId, sort) => ({ playlistId: playlist.id, songId, sort })),
    });
  }
  reply.send(playlist);
}

// ---------------------------------------------------------------- 热门榜（Q26：管理员固定榜）

const trendingSchema = z.object({ song_ids: z.array(z.number().int().positive()).max(50) });

async function putTrending(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const parsed = trendingSchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "song_ids required (max 50)");
  const songIds = [...new Set(parsed.data.song_ids)];
  for (const songId of songIds) {
    if (!(await prisma.song.findUnique({ where: { id: songId } }))) {
      return void bad(reply, `song ${songId} not found`);
    }
  }
  await prisma.$transaction([
    prisma.trendingSong.deleteMany(),
    prisma.trendingSong.createMany({
      data: songIds.map((songId, sort) => ({ songId, sort })),
    }),
  ]);
  reply.send({ ok: true, count: songIds.length });
}

// ---------------------------------------------------------------- 设置

async function putSettings(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const body = (req.body ?? {}) as Record<string, unknown>;
  // 白名单更新（id 不可改）
  const allow = [
    "packageName", "onesignalAppId", "onesignalRestKey", "appName", "appLogo", "appEmail",
    "appVersion", "appAuthor", "appContact", "appWebsite", "appDescription", "appDevelopedBy",
    "appPrivacyPolicy", "apiLatestLimit", "apiCatOrderBy", "apiCatPostOrderBy", "publisherId",
    "interstitalAd", "interstitalAdId", "interstitalAdClick", "bannerAd", "bannerAdId",
    "bannerAdType", "bannerFacebookId", "interstitalAdType", "interstitalFacebookId",
    "nativeAd", "nativeAdType", "nativeAdId", "nativeFacebookId", "nativePosition",
    "appUpdateStatus", "appNewVersion", "appUpdateDesc", "appRedirectUrl",
    "cancelUpdateStatus", "songDownload",
  ] as const;
  const data: Record<string, unknown> = {};
  for (const key of allow) {
    if (body[key] !== undefined) data[key] = body[key];
  }
  const settings = await prisma.setting.update({ where: { id: 1 }, data });
  invalidateSettingsCache();
  reply.send(settings);
}

// ---------------------------------------------------------------- 用户 / 举报 / 建议

async function listUsers(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { page, size, skip } = paging(req);
  const keyword = str((req.query as Record<string, string | undefined>)["keyword"] ?? "");
  const where = keyword
    ? { OR: [{ name: { contains: keyword } }, { email: { contains: keyword } }] }
    : {};
  const [total, items] = await Promise.all([
    prisma.appUser.count({ where }),
    prisma.appUser.findMany({
      where,
      orderBy: { id: "desc" },
      take: size,
      skip,
      select: {
        id: true, userType: true, name: true, email: true, phone: true,
        status: true, createdAt: true,
      },
    }),
  ]);
  reply.send({ items, total, page, size });
}

// ---------------------------------------------------------------- OneSignal 推送（Q4 保留）

const notifySchema = z.object({
  title: z.string().min(1),
  message: z.string().min(1),
  url: z.string().optional(),
});

async function sendNotification(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const parsed = notifySchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "title and message required");
  const s = await prisma.setting.findUnique({ where: { id: 1 } });
  if (!s?.onesignalAppId || !s?.onesignalRestKey) {
    return void bad(reply, "OneSignal not configured in settings");
  }
  const res = await fetch("https://onesignal.com/api/v1/notifications", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Basic ${s.onesignalRestKey}`,
    },
    body: JSON.stringify({
      app_id: s.onesignalAppId,
      included_segments: ["All"],
      headings: { en: parsed.data.title },
      contents: { en: parsed.data.message },
      ...(parsed.data.url ? { url: parsed.data.url } : {}),
    }),
  });
  const body = (await res.json()) as unknown;
  reply.code(res.ok ? 200 : 502).send(body);
}

// ---------------------------------------------------------------- 注册路由

export async function adminRoutes(app: FastifyInstance): Promise<void> {
  app.post("/admin/login", login);
  app.post("/admin/password", { preHandler: requireAdmin }, changePassword);
  app.get(
    "/admin/config",
    { preHandler: requireAdmin },
    async (_req, reply) => reply.send({ mediaBase: mediaBase() }),
  );

  app.get(
    "/admin/categories",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const where = {};
      const [total, items] = await Promise.all([
        prisma.category.count({ where }),
        prisma.category.findMany({ where, orderBy: { id: "desc" }, take: size, skip }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/categories", { preHandler: requireAdmin }, upsertCategory);
  app.put("/admin/categories/:id", { preHandler: requireAdmin }, upsertCategory);
  app.delete("/admin/categories/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      const idNum = Number.parseInt(id, 10);
      const songCount = await prisma.song.count({ where: { categoryId: idNum } });
      if (songCount > 0) return void bad(reply, `Category has ${songCount} songs`);
      await prisma.category.delete({ where: { id: idNum } });
      reply.send({ ok: true });
    },
  });

  app.get(
    "/admin/artists",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const keyword = str((req.query as Record<string, string | undefined>)["keyword"] ?? "");
      const where = keyword ? { name: { contains: keyword } } : {};
      const [total, items] = await Promise.all([
        prisma.artist.count({ where }),
        prisma.artist.findMany({ where, orderBy: { id: "desc" }, take: size, skip }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/artists", { preHandler: requireAdmin }, upsertArtist);
  app.put("/admin/artists/:id", { preHandler: requireAdmin }, upsertArtist);
  app.delete("/admin/artists/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.artist.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  app.get(
    "/admin/albums",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const where = {};
      const [total, items] = await Promise.all([
        prisma.album.count({ where }),
        prisma.album.findMany({
          where,
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: { artists: { orderBy: { sort: "asc" } } },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/albums", { preHandler: requireAdmin }, upsertAlbum);
  app.put("/admin/albums/:id", { preHandler: requireAdmin }, upsertAlbum);
  app.delete("/admin/albums/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.album.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  app.get(
    "/admin/songs",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const q = req.query as Record<string, string | undefined>;
      const keyword = str(q["keyword"] ?? "");
      const where = {
        ...(keyword ? { title: { contains: keyword } } : {}),
        ...(q["category_id"] ? { categoryId: Number.parseInt(q["category_id"]!, 10) } : {}),
      };
      const [total, items] = await Promise.all([
        prisma.song.count({ where }),
        prisma.song.findMany({
          where,
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: {
            category: { select: { id: true, name: true } },
            album: { select: { id: true, name: true } },
            artists: { orderBy: { sort: "asc" }, select: { artistId: true } },
          },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/songs", { preHandler: requireAdmin }, upsertSong);
  app.put("/admin/songs/:id", { preHandler: requireAdmin }, upsertSong);
  app.delete("/admin/songs/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.song.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  app.get(
    "/admin/banners",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const where = {};
      const [total, items] = await Promise.all([
        prisma.banner.count({ where }),
        prisma.banner.findMany({
          where,
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: { songs: { orderBy: { sort: "asc" }, select: { songId: true } } },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/banners", { preHandler: requireAdmin }, upsertBanner);
  app.put("/admin/banners/:id", { preHandler: requireAdmin }, upsertBanner);
  app.delete("/admin/banners/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.banner.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  app.get(
    "/admin/playlists",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const where = {};
      const [total, items] = await Promise.all([
        prisma.playlist.count({ where }),
        prisma.playlist.findMany({
          where,
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: { songs: { orderBy: { sort: "asc" }, select: { songId: true } } },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/playlists", { preHandler: requireAdmin }, upsertPlaylist);
  app.put("/admin/playlists/:id", { preHandler: requireAdmin }, upsertPlaylist);
  app.delete("/admin/playlists/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.playlist.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  // 热门榜
  app.get(
    "/admin/trending",
    { preHandler: requireAdmin },
    async (_req, reply) => {
      const items = await prisma.trendingSong.findMany({
        orderBy: { sort: "asc" },
        include: { song: { include: { category: { select: { id: true, name: true } } } } },
      });
      reply.send({ items });
    },
  );
  app.put("/admin/trending", { preHandler: requireAdmin }, putTrending);

  // 设置
  app.get(
    "/admin/settings",
    { preHandler: requireAdmin },
    async (_req, reply) => reply.send(await prisma.setting.findUnique({ where: { id: 1 } })),
  );
  app.put("/admin/settings", { preHandler: requireAdmin }, putSettings);

  // 用户管理
  app.get("/admin/users", { preHandler: requireAdmin }, listUsers);
  app.put("/admin/users/:id/status", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      const body = (req.body ?? {}) as { status?: boolean };
      await prisma.appUser.update({
        where: { id: Number.parseInt(id, 10) },
        data: { status: body.status === true },
      });
      reply.send({ ok: true });
    },
  });
  app.delete("/admin/users/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.appUser.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  // 举报 / 建议
  app.get(
    "/admin/reports",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const [total, items] = await Promise.all([
        prisma.report.count(),
        prisma.report.findMany({
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: { song: { select: { id: true, title: true } }, user: { select: { id: true, name: true, email: true } } },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.delete("/admin/reports/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.report.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });
  app.get(
    "/admin/suggestions",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const [total, items] = await Promise.all([
        prisma.songSuggest.count(),
        prisma.songSuggest.findMany({
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: { user: { select: { id: true, name: true, email: true } } },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.delete("/admin/suggestions/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.songSuggest.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  // OneSignal 推送
  app.post("/admin/notifications", { preHandler: requireAdmin }, sendNotification);
}
