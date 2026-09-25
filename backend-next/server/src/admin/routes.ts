// Admin API —— REST 端点（Q6：内容 CRUD + 设置 + 热门榜 + 用户 + OneSignal 推送）
// 砍除：Envato 验证、RichFileManager（Q4）；邮件相关全部不设（Q27）
import bcrypt from "bcryptjs";
import {
  type FastifyInstance,
  type FastifyReply,
  type FastifyRequest,
} from "fastify";
import type { MultipartFile } from "@fastify/multipart";
import { Prisma } from "@prisma/client";
import { z } from "zod";
import { prisma } from "../prisma.js";
import { config } from "../config.js";
import { newQrToken } from "../download/qr.js";
import { invalidateSettingsCache } from "../services/settings.js";
import { BOOK_THUMB_SIZE, resolveName, saveImage } from "../media/save.js";
import { decryptFilename, encryptLyrics, nameStem } from "../media/crypt.js";
import { mediaBase } from "../media/urls.js";
import {
  deleteObject,
  getCacheControl,
  getStream,
  isObfuscated,
  listObjects,
  putObject,
  putStream,
  setCacheControl,
} from "../media/oss.js";
import { transferExternalToOss } from "../media/external.js";
import { probeAudioDuration } from "../media/duration.js";
import { OBFUSCATED_META, obfuscateStream } from "../media/obfuscate.js";
import { requireAdmin, signAdminToken } from "./auth.js";
import { splitChapters, decodeTxt } from "../services/txtSplit.js";
import { computeOnlineCount } from "../services/stats.js";

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

// 北京日界（UTC+8 固定偏移，无夏令时）：平移到北京墙上时钟后按 UTC 取整日，再移回。
// 不用 setHours —— 进程本地时区不可依赖：生产容器无 TZ（本地 = UTC）会把日界落到北京 08:00
const BJ_OFFSET_MS = 8 * 60 * 60 * 1000;
const bjDayStart = (ms: number): Date => {
  const d = new Date(ms + BJ_OFFSET_MS);
  d.setUTCHours(0, 0, 0, 0);
  return new Date(d.getTime() - BJ_OFFSET_MS);
};

function paging(req: FastifyRequest): { page: number; size: number; skip: number } {
  const q = req.query as Record<string, string | undefined>;
  const page = Math.max(1, intOr(q["page"], 1));
  const size = Math.min(1000, Math.max(1, intOr(q["size"], 20)));
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
  opts: {
    streamHandler?: (fieldname: string, part: MultipartFile) => Promise<string | null>;
    /** 图片固定标签：传给 resolveName 生成 rand_<label><ext>；不传则保留原名 */
    imageLabel?: string;
    /** 缩略图边长（默认 300；书籍封面 720，ADR 0011 修订） */
    thumbSize?: number;
  } = {},
): Promise<FormData> {
  const fields: Record<string, string> = {};
  const saved: Record<string, string> = {};
  const buffers: Record<string, { buffer: Buffer; name: string }> = {};

  for await (const part of req.parts()) {
    if (part.type === "file") {
      if (opts.streamHandler) {
        const name = await opts.streamHandler(part.fieldname, part);
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
      : await saveImage(f.buffer, f.name, 80, opts.imageLabel, opts.thumbSize);
  }
  return { fields, saved };
}

/** LRC 文本上传 OSS（key = lrc/<rand>_lrc.<ext>；旧逻辑误用缩略图标签 _mp3_thumb，已修正）。
 *  内容先经 encryptLyrics 密文化（docs/adr/0010 歌词密文）：歌曲表单与 OSS 直传两路都汇于此 */
async function saveTextFile(buffer: Buffer, originalName: string): Promise<string> {
  const name = await resolveName("lrc", originalName, "lrc");
  await putObject(`lrc/${name}`, Buffer.from(encryptLyrics(buffer.toString("utf8")), "utf8"));
  return name;
}

/** 音频流式直传 OSS（ADR 0004；Q19：≤500MB；key = uploads/<rand>_原名）。
 *  写入前逐字节 +31 混淆（仓库级 ADR 0011），encrypted 元数据与内容同 put 原子写入 */
async function saveAudioStream(part: MultipartFile): Promise<string> {
  const name = await resolveName("uploads", part.filename || "");
  await putStream(`uploads/${name}`, part.file.pipe(obfuscateStream()), { meta: OBFUSCATED_META });
  return name;
}

/**
 * 图片类字段取值：新上传文件优先，其次接受绑定已有 OSS key 的文本字段 image。
 * 分类/艺术家/专辑/横幅/播放列表共用；歌曲的 thumbnail/lrc_url/audio_url 原生支持文本回填。
 */
function boundImage(saved: Record<string, string>, fields: Record<string, string>): string | undefined {
  return saved["image"] ?? (str(fields["image"]) || undefined);
}

/**
 * upsert 分流 id：PUT /:id 路由以 URL 参数为权威（编辑必须命中既有记录，杜绝"编辑变新增"），
 * POST 无路由参数则回落表单字段 id。URL 参数存在但非法时返回 null，由调用方拒绝。
 */
function upsertId(req: FastifyRequest, fields: Record<string, string>): number | null {
  const param = (req.params as { id?: string }).id;
  if (param === undefined) return intOr(fields["id"], 0);
  const n = intOr(param, 0);
  return n > 0 ? n : null;
}

/** 专辑在分类内的维度顺序插入最前（backend-next ADR 0009，与歌曲维度顺序范式对称） */
async function nextAlbumCategorySort(categoryId: number): Promise<number> {
  const agg = await prisma.album.aggregate({ where: { categoryId }, _min: { categorySort: true } });
  return (agg._min.categorySort ?? 0) - 1;
}

/** 维度顺序插入最前（backend-next ADR 0007，用户修订）：新归属写入 min(sort)-1，排在最前（新歌在前） */
async function nextDimensionSort(kind: "category" | "album", refId: number): Promise<number> {
  if (kind === "category") {
    const agg = await prisma.song.aggregate({ where: { categoryId: refId }, _min: { categorySort: true } });
    return (agg._min.categorySort ?? 0) - 1;
  }
  const agg = await prisma.song.aggregate({ where: { albumId: refId }, _min: { albumSort: true } });
  return (agg._min.albumSort ?? 0) - 1;
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
  const { fields, saved } = await parseForm(req, { imageLabel: "category" });
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  const data = {
    name,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    isPrivate: fields["is_private"] !== "0" && fields["is_private"] !== "false",
    ...(boundImage(saved, fields) ? { image: boundImage(saved, fields) } : {}),
  };
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
  if (id > 0) return void reply.send(await prisma.category.update({ where: { id }, data }));
  // 类型仅创建时可定（书籍阅读域 ADR 0011）：编辑路径不触碰 type，存量分类不受影响
  const type = fields["type"] === "book" ? "book" : "music";
  reply.send(await prisma.category.create({ data: { ...data, type, image: data.image ?? "" } }));
}

async function upsertArtist(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req, { imageLabel: "artist" });
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  const data = { name, ...(boundImage(saved, fields) ? { image: boundImage(saved, fields) } : {}) };
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
  reply.send(
    id > 0
      ? await prisma.artist.update({ where: { id }, data })
      : await prisma.artist.create({ data: { ...data, image: data.image ?? "" } }),
  );
}

// ---------------------------------------------------------------- 专辑

async function upsertAlbum(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req, { imageLabel: "album" });
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  // 分类可空（backend-next ADR 0009）：category_id 为 0/缺省表示未分类专辑
  const categoryRaw = intOr(fields["category_id"], 0);
  if (categoryRaw > 0 && !(await prisma.category.findUnique({ where: { id: categoryRaw } }))) {
    return void bad(reply, "category not found");
  }
  const categoryId = categoryRaw > 0 ? categoryRaw : null;
  const artistIds = parseIds(str(fields["artist_ids"]));
  for (const artistId of artistIds) {
    if (!(await prisma.artist.findUnique({ where: { id: artistId } }))) {
      return void bad(reply, `artist ${artistId} not found`);
    }
  }
  const data = {
    name,
    categoryId,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    isPrivate: fields["is_private"] !== "0" && fields["is_private"] !== "false",
    ...(boundImage(saved, fields) ? { image: boundImage(saved, fields) } : {}),
  };
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
  // 维度顺序（ADR 0009）：新建/换分类时插入最前（min(sort)-1），移入未分类归零；未变动保留原序
  const existing = id > 0 ? await prisma.album.findUnique({ where: { id } }) : null;
  const categoryChanged = !!existing && existing.categoryId !== categoryId;
  const categorySort = categoryId ? await nextAlbumCategorySort(categoryId) : 0;
  const album = id > 0
    ? await prisma.album.update({
        where: { id },
        data: { ...data, ...(categoryChanged ? { categorySort } : {}) },
      })
    : await prisma.album.create({
        data: { ...data, image: data.image ?? "", ...(categoryId ? { categorySort } : {}) },
      });

  if (fields["artist_ids"] !== undefined) {
    await prisma.albumArtist.deleteMany({ where: { albumId: album.id } });
    await prisma.albumArtist.createMany({
      data: artistIds.map((artistId, sort) => ({ albumId: album.id, artistId, sort })),
    });
  }
  reply.send(album);
}

// ---------------------------------------------------------------- 歌曲

const songTypes = new Set(["local", "external"]);

async function upsertSong(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  let form: FormData;
  try {
    form = await parseForm(req, {
      streamHandler: (fieldname, part) =>
        fieldname === "audio" ? saveAudioStream(part) : Promise.resolve(null),
      imageLabel: "mp3_thumb", // 歌曲缩略图沿用旧逻辑固定标签
    });
  } catch (e) {
    return void bad(reply, e instanceof Error ? e.message : "upload failed");
  }
  const { fields, saved } = form;

  const type = str(fields["type"]) || "local";
  if (!songTypes.has(type)) return void bad(reply, "type must be local | external");
  // 分类可空（backend-next ADR 0007）：category_id 为 0/缺省表示未分类； album 须先有分类
  const categoryRaw = intOr(fields["category_id"], 0);
  if (categoryRaw > 0 && !(await prisma.category.findUnique({ where: { id: categoryRaw } }))) {
    return void bad(reply, "category not found");
  }
  const categoryId = categoryRaw > 0 ? categoryRaw : null;
  const title = str(fields["title"]);
  if (!title) return void bad(reply, "title required");

  const albumRaw = intOr(fields["album_id"], 0);
  // ADR 0007「先有分类才能进专辑」约束已废弃（ADR 0009）：歌曲可见性由专辑归属链推导
  const albumId =
    albumRaw > 0 && (await prisma.album.findUnique({ where: { id: albumRaw } })) ? albumRaw : null;

  const artistIds = parseIds(str(fields["artist_ids"]));
  for (const artistId of artistIds) {
    if (!(await prisma.artist.findUnique({ where: { id: artistId } }))) {
      return void bad(reply, `artist ${artistId} not found`);
    }
  }

  // local 用上传文件名，external 用地址字段；编辑时留空则保留原值（不覆盖为空）
  const audioUrl =
    type === "local" ? saved["audio"] ?? str(fields["audio_url"]) : str(fields["audio_url"]);
  const thumbnail = saved["thumbnail"] ?? (str(fields["thumbnail"]) || undefined);
  const lrcUrl = saved["lrc"] ?? (fields["lrc_url"] ? str(fields["lrc_url"]) : undefined);
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
  if (!audioUrl && id <= 0) return void bad(reply, "audio file or audio_url required");

  // 维度顺序（ADR 0007）：新建/换归属时追加到该维度末尾，未变动则保留原序
  const existing = id > 0 ? await prisma.song.findUnique({ where: { id } }) : null;
  const categoryChanged = !!existing && existing.categoryId !== categoryId;
  const albumChanged = !!existing && existing.albumId !== albumId;
  const categorySort = categoryId ? await nextDimensionSort("category", categoryId) : 0;
  const albumSort = albumId ? await nextDimensionSort("album", albumId) : 0;

  const common = {
    categoryId,
    albumId,
    type,
    title,
    description: fields["description"] ?? "",
    lrcText: fields["lrc_text"] ? str(fields["lrc_text"]) : null,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    isPrivate: fields["is_private"] !== "0" && fields["is_private"] !== "false",
  };
  // 编辑：留空的文件字段不进 data（Prisma 忽略 undefined，保留原值）；新建：audioUrl 必填、thumbnail 落空串（列无默认值）
  const song = existing
    ? await prisma.song.update({
        where: { id },
        data: {
          ...common,
          ...(audioUrl ? { audioUrl } : {}),
          ...(thumbnail ? { thumbnail } : {}),
          ...(lrcUrl ? { lrcUrl } : {}),
          ...(categoryChanged ? { categorySort } : {}),
          ...(albumChanged ? { albumSort } : {}),
        },
      })
    : await prisma.song.create({
        data: { ...common, audioUrl: audioUrl || "", thumbnail: thumbnail ?? "", ...(lrcUrl ? { lrcUrl } : {}) },
      });

  if (fields["artist_ids"] !== undefined) {
    await prisma.songArtist.deleteMany({ where: { songId: song.id } });
    await prisma.songArtist.createMany({
      data: artistIds.map((artistId, sort) => ({ songId: song.id, artistId, sort })),
    });
  }
  reply.send(song);
}

// ---------------------------------------------------------------- 歌曲批量操作

// 传什么改什么；thumbnail 为共享引用语义（多首歌指向同一 media key，不复制文件）
const songBatchSchema = z.object({
  ids: z.array(z.number().int().positive()).min(1),
  thumbnail: z.string().min(1).optional(),
  categoryId: z.number().int().nullable().optional(),
  albumId: z.number().int().nullable().optional(),
  isPrivate: z.boolean().optional(),
});

async function batchSong(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const parsed = songBatchSchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "ids required; thumbnail/categoryId/albumId/isPrivate optional");
  const { ids, thumbnail, categoryId, albumId, isPrivate } = parsed.data;
  if (thumbnail === undefined && categoryId === undefined && albumId === undefined && isPrivate === undefined) {
    return void bad(reply, "nothing to update");
  }
  // ADR 0007「专辑歌曲须先有分类」约束已废弃（ADR 0009），仅保留存在性校验
  if (categoryId !== undefined && categoryId !== null && !(await prisma.category.findUnique({ where: { id: categoryId } }))) {
    return void bad(reply, "category not found");
  }
  if (albumId != null && !(await prisma.album.findUnique({ where: { id: albumId } }))) {
    return void bad(reply, "album not found");
  }
  const data: Prisma.SongUncheckedUpdateManyInput = {};
  if (thumbnail !== undefined) data.thumbnail = thumbnail;
  if (categoryId !== undefined) {
    data.categoryId = categoryId;
    // 换分类插入最前（与单首编辑一致，ADR 0007，用户修订：min(sort)-1）；移入未分类时归零。同一 sort 值由 id 兜底保持相对顺序
    data.categorySort = categoryId === null ? 0 : await nextDimensionSort("category", categoryId);
  }
  if (albumId !== undefined) {
    data.albumId = albumId;
    // 同分类：换专辑插入最前（min(sort)-1）；移出专辑时归零
    data.albumSort = albumId === null ? 0 : await nextDimensionSort("album", albumId);
  }
  if (isPrivate !== undefined) data.isPrivate = isPrivate;
  const r = await prisma.song.updateMany({ where: { id: { in: ids } }, data });
  reply.send({ count: r.count });
}

// ---------------------------------------------------------------- 维度顺序（backend-next ADR 0007）

/**
 * 分类/专辑两维度共用的歌曲管理端点：列表（按维度顺序）、拖拽排序、认领孤儿、移除。
 * 维度顺序存于 Song.category_sort / album_sort，展示按 sort ASC, id ASC 兜底。
 */
function registerDimensionSongs(app: FastifyInstance, key: "categories" | "albums"): void {
  const fk = key === "categories" ? ("categoryId" as const) : ("albumId" as const);
  const sortKey = key === "categories" ? ("categorySort" as const) : ("albumSort" as const);
  const label = key === "categories" ? "分类" : "专辑";
  const base = `/admin/${key}/:id/songs`;
  const songSelect = {
    id: true, title: true, thumbnail: true, type: true, status: true,
    totalViews: true, album: { select: { id: true, name: true } },
  } as const satisfies Prisma.SongSelect;

  app.get(base, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const items = await prisma.song.findMany({
      where: { [fk]: parentId } as Prisma.SongWhereInput,
      // sort ASC 展示；id DESC 兜底：未手动排序过的存量歌新歌在前
      orderBy: [{ [sortKey]: "asc" }, { id: "desc" }] as Prisma.SongOrderByWithRelationInput[],
      select: songSelect,
    });
    reply.send({ items });
  });

  // 候选列表：分类认领只列未分类歌曲；专辑认领只列无专辑且有分类的歌曲（先有分类才能进专辑）
  app.get(`${base}/available`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const { page, size, skip } = paging(req);
    const keyword = str((req.query as Record<string, string | undefined>)["keyword"] ?? "");
    const where: Prisma.SongWhereInput = {
      ...(keyword ? { title: { contains: keyword } } : {}),
      ...({ [fk]: null } as Prisma.SongWhereInput),
      ...(key === "albums" ? ({ categoryId: { not: null } } as Prisma.SongWhereInput) : {}),
    };
    const [total, items] = await Promise.all([
      prisma.song.count({ where }),
      prisma.song.findMany({ where, orderBy: { id: "desc" }, take: size, skip, select: songSelect }),
    ]);
    reply.send({ items, total, page, size });
  });

  app.put(`${base}/order`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const parsed = z.object({ ids: z.array(z.number().int().positive()) }).safeParse(req.body);
    if (!parsed.success) return void bad(reply, "Invalid body");
    const results = await prisma.$transaction(
      parsed.data.ids.map((songId, index) =>
        prisma.song.updateMany({
          where: { id: songId, [fk]: parentId } as Prisma.SongWhereInput,
          data: { [sortKey]: index },
        }),
      ),
    );
    if (results.some((r) => r.count === 0)) return void bad(reply, `存在不属于该${label}的歌曲`);
    reply.send({ ok: true });
  });

  // 认领：把孤儿歌曲批量加入当前维度，插入最前（新歌在前）
  app.post(base, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const parsed = z.object({ ids: z.array(z.number().int().positive()) }).safeParse(req.body);
    if (!parsed.success) return void bad(reply, "Invalid body");
    const orphans = await prisma.song.findMany({
      where: {
        id: { in: parsed.data.ids },
        ...({ [fk]: null } as Prisma.SongWhereInput),
        ...(key === "albums" ? ({ categoryId: { not: null } } as Prisma.SongWhereInput) : {}),
      } as Prisma.SongWhereInput,
      select: { id: true },
    });
    if (orphans.length !== parsed.data.ids.length) {
      return void bad(reply, `部分歌曲不满足认领条件（须未归属${label}${key === "albums" ? "且已有分类" : ""}）`);
    }
    const maxAgg = await prisma.song.aggregate({
      where: { [fk]: parentId } as Prisma.SongWhereInput,
      _min: { [sortKey]: true } as never,
    });
    const baseSort = ((maxAgg._min as Record<string, number | null>)[sortKey] ?? 0) - 1;
    await prisma.$transaction(
      orphans.map((s, index) =>
        prisma.song.update({
          where: { id: s.id },
          data: { [fk]: parentId, [sortKey]: baseSort + 1 + index } as Prisma.SongUpdateInput,
        }),
      ),
    );
    reply.send({ ok: true });
  });

  // 移除：置空归属（歌曲不删除），顺序值归零待下次认领重排
  app.delete(`${base}/:songId`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    const songId = intOr((req.params as { songId: string }).songId, 0);
    if (parentId <= 0 || songId <= 0) return void bad(reply, "invalid id");
    const r = await prisma.song.updateMany({
      where: { id: songId, [fk]: parentId } as Prisma.SongWhereInput,
      data: { [fk]: null, [sortKey]: 0 } as Prisma.SongUncheckedUpdateInput,
    });
    if (r.count === 0) return void bad(reply, `歌曲不属于该${label}`);
    reply.send({ ok: true });
  });
}

// ---------------------------------------------------------------- 分类内专辑（backend-next ADR 0009）

/** 分类维度专辑管理端点：列表（维度顺序）、未分类专辑认领、拖拽排序、移出分类。与歌曲维度端点范式对称 */
function registerCategoryAlbums(app: FastifyInstance): void {
  const base = "/admin/categories/:id/albums";
  const albumSelect = {
    id: true,
    name: true,
    image: true,
    status: true,
    _count: { select: { songs: true } },
  } as const satisfies Prisma.AlbumSelect;

  app.get(base, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const items = await prisma.album.findMany({
      where: { categoryId: parentId },
      // 维度顺序：sort ASC 展示；id DESC 兜底（未手动排序的存量专辑新专辑在前）
      orderBy: [{ categorySort: "asc" }, { id: "desc" }] as Prisma.AlbumOrderByWithRelationInput[],
      select: albumSelect,
    });
    reply.send({ items });
  });

  // 候选列表：认领只列未分类专辑
  app.get(`${base}/available`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const { page, size, skip } = paging(req);
    const keyword = str((req.query as Record<string, string | undefined>)["keyword"] ?? "");
    const where: Prisma.AlbumWhereInput = {
      ...(keyword ? { name: { contains: keyword } } : {}),
      categoryId: null,
    };
    const [total, items] = await Promise.all([
      prisma.album.count({ where }),
      prisma.album.findMany({ where, orderBy: { id: "desc" }, take: size, skip, select: albumSelect }),
    ]);
    reply.send({ items, total, page, size });
  });

  app.put(`${base}/order`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const parsed = z.object({ ids: z.array(z.number().int().positive()) }).safeParse(req.body);
    if (!parsed.success) return void bad(reply, "Invalid body");
    const results = await prisma.$transaction(
      parsed.data.ids.map((albumId, index) =>
        prisma.album.updateMany({
          where: { id: albumId, categoryId: parentId },
          data: { categorySort: index },
        }),
      ),
    );
    if (results.some((r) => r.count === 0)) return void bad(reply, "存在不属于该分类的专辑");
    reply.send({ ok: true });
  });

  // 认领：把未分类专辑批量加入当前分类，插入最前（新专辑在前）
  app.post(base, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const parsed = z.object({ ids: z.array(z.number().int().positive()) }).safeParse(req.body);
    if (!parsed.success) return void bad(reply, "Invalid body");
    const orphans = await prisma.album.findMany({
      where: { id: { in: parsed.data.ids }, categoryId: null },
      select: { id: true },
    });
    if (orphans.length !== parsed.data.ids.length) {
      return void bad(reply, "部分专辑不满足认领条件（须未分类）");
    }
    const minAgg = await prisma.album.aggregate({
      where: { categoryId: parentId },
      _min: { categorySort: true },
    });
    const baseSort = (minAgg._min?.categorySort ?? 0) - 1;
    await prisma.$transaction(
      orphans.map((a, index) =>
        prisma.album.update({
          where: { id: a.id },
          data: { categoryId: parentId, categorySort: baseSort + 1 + index },
        }),
      ),
    );
    reply.send({ ok: true });
  });

  // 移出分类：置空归属（专辑不删除），顺序值归零待下次认领重排
  app.delete(`${base}/:albumId`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    const albumId = intOr((req.params as { albumId: string }).albumId, 0);
    if (parentId <= 0 || albumId <= 0) return void bad(reply, "invalid id");
    const r = await prisma.album.updateMany({
      where: { id: albumId, categoryId: parentId },
      data: { categoryId: null, categorySort: 0 },
    });
    if (r.count === 0) return void bad(reply, "专辑不属于该分类");
    reply.send({ ok: true });
  });
}

// ---------------------------------------------------------------- 书籍（书籍阅读域 ADR 0011）

/** 书籍在分类内的维度顺序插入最前（对齐专辑范式，ADR 0011） */
async function nextBookCategorySort(categoryId: number): Promise<number> {
  const agg = await prisma.book.aggregate({ where: { categoryId }, _min: { categorySort: true } });
  return (agg._min.categorySort ?? 0) - 1;
}

async function upsertBook(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const { fields, saved } = await parseForm(req, { imageLabel: "book", thumbSize: BOOK_THUMB_SIZE });
  const name = str(fields["name"]);
  if (!name) return void bad(reply, "name required");
  // 书籍必有分类，且必须是书籍分类（type=book）；分类类型创建后不可改，无音乐分类挂书的路径
  const categoryId = intOr(fields["category_id"], 0);
  if (categoryId <= 0) return void bad(reply, "category required");
  const category = await prisma.category.findUnique({ where: { id: categoryId } });
  if (!category) return void bad(reply, "category not found");
  if (category.type !== "book") return void bad(reply, "category is not a book category");
  const data = {
    name,
    author: str(fields["author"]),
    categoryId,
    status: fields["status"] !== "0" && fields["status"] !== "false",
    ...(boundImage(saved, fields) ? { cover: boundImage(saved, fields) } : {}),
  };
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
  // 维度顺序（ADR 0011）：新建/换分类时插入最前（min(sort)-1），未变动保留原序
  const existing = id > 0 ? await prisma.book.findUnique({ where: { id } }) : null;
  const categoryChanged = !!existing && existing.categoryId !== categoryId;
  const categorySort = await nextBookCategorySort(categoryId);
  const book = id > 0
    ? await prisma.book.update({
        where: { id },
        data: { ...data, ...(categoryChanged ? { categorySort } : {}) },
      })
    : await prisma.book.create({ data: { ...data, categorySort } });
  reply.send(book);
}

const chapterSchema = z.object({
  title: z.string().min(1).max(255),
  content: z.string().max(8 * 1024 * 1024),
});

async function createChapter(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const bookId = intOr((req.params as { id: string }).id, 0);
  if (bookId <= 0) return void bad(reply, "invalid id");
  if (!(await prisma.book.findUnique({ where: { id: bookId } }))) return void bad(reply, "book not found");
  const parsed = chapterSchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "title required");
  // 章节顺序（ADR 0011 修订）：逐章录入追加书末（max+1），与 id 序初值语义一致
  const agg = await prisma.chapter.aggregate({ where: { bookId }, _max: { chapterSort: true } });
  const chapter = await prisma.chapter.create({
    data: {
      bookId,
      title: parsed.data.title,
      content: parsed.data.content,
      chapterSort: (agg._max.chapterSort ?? -1) + 1,
    },
  });
  reply.send({ id: chapter.id, title: chapter.title });
}

// 传什么改什么（逐章编辑抽屉：标题/正文可分别保存）
async function updateChapter(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const id = intOr((req.params as { id: string }).id, 0);
  if (id <= 0) return void bad(reply, "invalid id");
  const parsed = chapterSchema.partial().safeParse(req.body);
  if (!parsed.success) return void bad(reply, "invalid body");
  if (parsed.data.title === undefined && parsed.data.content === undefined) {
    return void bad(reply, "nothing to update");
  }
  const chapter = await prisma.chapter.update({ where: { id }, data: parsed.data });
  reply.send({ id: chapter.id, title: chapter.title });
}

// 智能分章节：TXT 上限 50MB；preview 只切分不入库（parseForm 的 20MB 图片上限不适用，手动收流）
const TXT_MAX = 50 * 1024 * 1024;

async function txtPreview(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const bookId = intOr((req.params as { id: string }).id, 0);
  if (bookId <= 0) return void bad(reply, "invalid id");
  let buffer: Buffer | null = null;
  for await (const part of req.parts()) {
    if (part.type === "file") {
      buffer = await part.toBuffer();
      break;
    }
  }
  if (!buffer) return void bad(reply, "txt file required");
  if (buffer.length > TXT_MAX) return void bad(reply, "TXT too large (max 50MB)");
  const chapters = splitChapters(decodeTxt(buffer));
  reply.send({ total: chapters.length, chapters });
}

// 覆盖替换：删旧写新单事务（强确认弹窗在面板列明删 N 写 M）
const importSchema = z.object({
  chapters: z
    .array(z.object({ title: z.string().min(1).max(255), content: z.string().max(8 * 1024 * 1024) }))
    .min(1)
    .max(5000),
});

async function txtImport(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const bookId = intOr((req.params as { id: string }).id, 0);
  if (bookId <= 0) return void bad(reply, "invalid id");
  if (!(await prisma.book.findUnique({ where: { id: bookId } }))) return void bad(reply, "book not found");
  const parsed = importSchema.safeParse(req.body);
  if (!parsed.success) return void bad(reply, "chapters required");
  await prisma.$transaction([
    prisma.chapter.deleteMany({ where: { bookId } }),
    prisma.chapter.createMany({
      // 章节顺序（ADR 0011 修订）：按切分/预览顺序赋初值 0..N-1
      data: parsed.data.chapters.map((c, index) => ({ bookId, title: c.title, content: c.content, chapterSort: index })),
    }),
  ]);
  reply.send({ ok: true, count: parsed.data.chapters.length });
}

/** 分类内书籍维度端点（维度顺序第三维度，ADR 0011）：列表 + 拖拽排序。书籍必有分类，无认领/移除 */
function registerCategoryBooks(app: FastifyInstance): void {
  const base = "/admin/categories/:id/books";

  app.get(base, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const rows = await prisma.book.findMany({
      where: { categoryId: parentId },
      // 维度顺序：sort ASC 展示；id DESC 兜底（未手动排序的存量书籍新书在前）
      orderBy: [{ categorySort: "asc" }, { id: "desc" }] as Prisma.BookOrderByWithRelationInput[],
      select: {
        id: true, name: true, cover: true, status: true,
        _count: { select: { chapters: true } },
      },
    });
    // 行结构对齐 DimensionDrawer 的专辑形态（image 字段名 + _count 计数），前端零特判
    reply.send({
      items: rows.map((r) => ({
        id: r.id, name: r.name, image: r.cover, status: r.status,
        _count: { chapters: r._count.chapters },
      })),
    });
  });

  app.put(`${base}/order`, { preHandler: requireAdmin }, async (req, reply) => {
    const parentId = intOr((req.params as { id: string }).id, 0);
    if (parentId <= 0) return void bad(reply, "invalid id");
    const parsed = z.object({ ids: z.array(z.number().int().positive()) }).safeParse(req.body);
    if (!parsed.success) return void bad(reply, "Invalid body");
    const results = await prisma.$transaction(
      parsed.data.ids.map((bookId, index) =>
        prisma.book.updateMany({
          where: { id: bookId, categoryId: parentId },
          data: { categorySort: index },
        }),
      ),
    );
    if (results.some((r) => r.count === 0)) return void bad(reply, "存在不属于该分类的书籍");
    reply.send({ ok: true });
  });
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
    ...(boundImage(saved, fields) ? { image: boundImage(saved, fields) } : {}),
  };
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
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
    ...(boundImage(saved, fields) ? { image: boundImage(saved, fields) } : {}),
  };
  const id = upsertId(req, fields);
  if (id === null) return void bad(reply, "invalid id");
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
    "privacyMode",
  ] as const;
  const data: Record<string, unknown> = {};
  for (const key of allow) {
    if (body[key] !== undefined) data[key] = body[key];
  }
  const settings = await prisma.setting.update({ where: { id: 1 }, data });
  invalidateSettingsCache();
  reply.send(settings);
}

// ---------------------------------------------------------------- 下载二维码（仓库级 ADR 0013）

const qrSelect = { downloadQrToken: true, downloadQrExpiresAt: true } as const;

async function downloadQrState(reply: FastifyReply): Promise<void> {
  const s = await prisma.setting.findUnique({ where: { id: 1 }, select: qrSelect });
  const token = s?.downloadQrToken ?? "";
  const expiresAt = s?.downloadQrExpiresAt ?? null;
  const expired = !token || !expiresAt || expiresAt.getTime() <= Date.now();
  reply.send({
    token,
    url: token ? `${config.downloadBase}/download/q/${token}` : "",
    expiresAt,
    expired,
  });
}

/** 生成/重掷令牌：旧二维码即时失效（令牌唯一，请求时校验） */
async function putDownloadQr(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const body = (req.body ?? {}) as { expiresAt?: unknown };
  const ms = Number(body.expiresAt);
  if (!Number.isFinite(ms) || ms <= Date.now()) {
    return void bad(reply, "到期时间必须是未来时间");
  }
  const token = newQrToken();
  const expiresAt = new Date(ms);
  await prisma.setting.update({ where: { id: 1 }, data: { downloadQrToken: token, downloadQrExpiresAt: expiresAt } });
  invalidateSettingsCache();
  reply.send({
    token,
    url: `${config.downloadBase}/download/q/${token}`,
    expiresAt,
    expired: false,
  });
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
      // 类型筛选（书籍阅读域 ADR 0011）：管理面板按音乐/书籍分类分流管理
      const type = (req.query as Record<string, string | undefined>)["type"];
      const where = type === "music" || type === "book" ? { type } : {};
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
      // ADR 0009：分类下仍有专辑即阻止删除（原为“仍有歌曲”）
      const albumCount = await prisma.album.count({ where: { categoryId: idNum } });
      if (albumCount > 0) return void bad(reply, `Category has ${albumCount} albums`);
      // ADR 0011：分类下仍有书籍即阻止删除（对齐专辑规则）
      const bookCount = await prisma.book.count({ where: { categoryId: idNum } });
      if (bookCount > 0) return void bad(reply, `Category has ${bookCount} books`);
      // 过渡期保留（二期随 Song.categoryId 拆除）：存量歌曲仍挂分类且 FK 为 RESTRICT，避免直接 500
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
      // category_id=0 表示未分类专辑（backend-next ADR 0009，与歌曲过滤哨兵对称）
      const q = req.query as Record<string, string | undefined>;
      const catRaw = q["category_id"];
      const catFilter =
        catRaw == null || catRaw === ""
          ? {}
          : catRaw === "0"
            ? { categoryId: null }
            : { categoryId: intOr(catRaw, 0) };
      const [total, items] = await Promise.all([
        prisma.album.count({ where: catFilter }),
        prisma.album.findMany({
          where: catFilter,
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: {
            artists: { orderBy: { sort: "asc" } },
            category: { select: { id: true, name: true } },
            _count: { select: { songs: true } },
          },
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
      // category_id=0 表示未分类（backend-next ADR 0007）
      const catRaw = q["category_id"];
      const catFilter =
        catRaw == null || catRaw === ""
          ? {}
          : catRaw === "0"
            ? { categoryId: null }
            : { categoryId: intOr(catRaw, 0) };
      // album_id=0 表示未归专辑（与 category_id=0 对称）
      const albRaw = q["album_id"];
      const albFilter =
        albRaw == null || albRaw === ""
          ? {}
          : albRaw === "0"
            ? { albumId: null }
            : { albumId: intOr(albRaw, 0) };
      // duration_missing=1 仅看时长缺失（0）：时长探测修复的定位入口
      const durMissing = q["duration_missing"];
      const where = {
        ...(keyword ? { title: { contains: keyword } } : {}),
        ...catFilter,
        ...albFilter,
        ...(durMissing === "1" ? { duration: 0 } : {}),
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
  // 批量操作：多选歌曲统一绑定封面（共享 media key）或修改分类
  app.patch("/admin/songs/batch", { preHandler: requireAdmin }, batchSong);
  // 外链转入 OSS（ADR 0006）：单首端点；列表多选批量由前端逐首调用驱动（进度可见、失败隔离）
  app.post(
    "/admin/songs/:id/to-oss",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { id } = req.params as { id: string };
      const song = await prisma.song.findUnique({ where: { id: Number.parseInt(id, 10) } });
      if (!song) return void reply.code(404).send({ error: "song not found" });
      if (song.type !== "external") return void bad(reply, "仅 external 歌曲可转入 OSS");
      const r = await transferExternalToOss(song.audioUrl, song.title);
      if (!r.ok) return void reply.code(502).send({ error: r.error });
      reply.send(await prisma.song.update({ where: { id: song.id }, data: { type: "local", audioUrl: r.name } }));
    },
  );
  // 时长探测（仓库级 ADR 0008 修订）：管理端「修复时长」逐曲调用，仅补 0、不覆盖已有值。
  // 跳过/外链/对象缺失/解析失败均为业务结果，以 200 + status 返回，供前端批量循环记录失败原因而不逐条弹错
  app.post(
    "/admin/songs/:id/probe-duration",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { id } = req.params as { id: string };
      const song = await prisma.song.findUnique({ where: { id: Number.parseInt(id, 10) } });
      if (!song) return void reply.code(404).send({ error: "song not found" });
      if (song.duration > 0) return void reply.send({ status: "skipped", duration: song.duration });
      if (song.type !== "local") return void reply.send({ status: "failed", reason: "外链歌曲（请先转入 OSS）" });
      try {
        const duration = await probeAudioDuration(`uploads/${song.audioUrl}`);
        await prisma.song.update({ where: { id: song.id }, data: { duration } });
        reply.send({ status: "repaired", duration });
      } catch (err) {
        const missing = (err as { status?: number }).status === 404;
        reply.send({ status: "failed", reason: missing ? "音频对象不存在" : "音频解析失败" });
      }
    },
  );
  app.delete("/admin/songs/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.song.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });
  // 专辑维度歌曲端点（backend-next ADR 0007）；分类维度已改专辑（ADR 0009）
  registerDimensionSongs(app, "albums");
  registerCategoryAlbums(app);

  // ============ 书籍（书籍阅读域 ADR 0011）============

  app.get(
    "/admin/books",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const { page, size, skip } = paging(req);
      const q = req.query as Record<string, string | undefined>;
      const keyword = str(q["keyword"] ?? "");
      const catRaw = q["category_id"];
      const where = {
        ...(keyword ? { name: { contains: keyword } } : {}),
        ...(catRaw ? { categoryId: intOr(catRaw, 0) } : {}),
      };
      const [total, items] = await Promise.all([
        prisma.book.count({ where }),
        prisma.book.findMany({
          where,
          orderBy: { id: "desc" },
          take: size,
          skip,
          include: {
            category: { select: { id: true, name: true } },
            _count: { select: { chapters: true } },
          },
        }),
      ]);
      reply.send({ items, total, page, size });
    },
  );
  app.post("/admin/books", { preHandler: requireAdmin }, upsertBook);
  app.put("/admin/books/:id", { preHandler: requireAdmin }, upsertBook);
  app.delete("/admin/books/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      // 删书级联删章节（schema onDelete: Cascade）；章节是书的组成部分，无独立存活出路
      await prisma.book.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  app.get("/admin/books/:id", { preHandler: requireAdmin }, async (req, reply) => {
    const id = intOr((req.params as { id: string }).id, 0);
    if (id <= 0) return void bad(reply, "invalid id");
    const book = await prisma.book.findUnique({
      where: { id },
      include: {
        category: { select: { id: true, name: true } },
        _count: { select: { chapters: true } },
      },
    });
    if (!book) return void reply.code(404).send({ error: "book not found" });
    reply.send(book);
  });

  // 章节：列表（id/标题/字数，CHAR_LENGTH 免拉正文，按章节顺序展示）、逐章增删改（逐章录入通道）
  app.get("/admin/books/:id/chapters", { preHandler: requireAdmin }, async (req, reply) => {
    const bookId = intOr((req.params as { id: string }).id, 0);
    if (bookId <= 0) return void bad(reply, "invalid id");
    const rows = await prisma.$queryRaw<{ id: number; title: string; contentLength: number | bigint }[]>`
      SELECT id, title, CHAR_LENGTH(content) AS contentLength
      FROM chapters WHERE book_id = ${bookId} ORDER BY chapter_sort ASC, id ASC
    `;
    // CHAR_LENGTH 返回 BIGINT，$queryRaw 映射为 BigInt，JSON.stringify 无法序列化 → 统一转 number
    reply.send({ items: rows.map((r) => ({ ...r, contentLength: Number(r.contentLength) })) });
  });
  app.post("/admin/books/:id/chapters", { preHandler: requireAdmin }, createChapter);
  // 章节顺序保存（ADR 0011 修订）：全量归位 0..N-1；ids 恰为该书全部章节（数量+无重复+归属三验），
  // 部分提交整单拒——宽松现状不复刻，并发增删得到错误提示而非静默混序
  app.put("/admin/books/:id/chapters/order", { preHandler: requireAdmin }, async (req, reply) => {
    const bookId = intOr((req.params as { id: string }).id, 0);
    if (bookId <= 0) return void bad(reply, "invalid id");
    const parsed = z.object({ ids: z.array(z.number().int().positive()) }).safeParse(req.body);
    if (!parsed.success) return void bad(reply, "Invalid body");
    const ids = parsed.data.ids;
    const rows = await prisma.chapter.findMany({ where: { bookId }, select: { id: true } });
    const owned = new Set(rows.map((r) => r.id));
    if (rows.length !== ids.length || !ids.every((id) => owned.has(id)) || new Set(ids).size !== ids.length) {
      return void bad(reply, "章节集合已变化，请刷新后重试");
    }
    await prisma.$transaction(
      ids.map((id, index) => prisma.chapter.updateMany({ where: { id, bookId }, data: { chapterSort: index } })),
    );
    reply.send({ ok: true });
  });
  // 单章回读：逐章编辑抽屉回填正文用（列表出于流量考虑不带正文）
  app.get("/admin/chapters/:id", { preHandler: requireAdmin }, async (req, reply) => {
    const id = intOr((req.params as { id: string }).id, 0);
    if (id <= 0) return void bad(reply, "invalid id");
    const chapter = await prisma.chapter.findUnique({ where: { id } });
    if (!chapter) return void reply.code(404).send({ error: "chapter not found" });
    reply.send(chapter);
  });
  app.put("/admin/chapters/:id", { preHandler: requireAdmin }, updateChapter);
  app.delete("/admin/chapters/:id", {
    preHandler: requireAdmin,
    async handler(req, reply) {
      const { id } = req.params as { id: string };
      await prisma.chapter.delete({ where: { id: Number.parseInt(id, 10) } });
      reply.send({ ok: true });
    },
  });

  // 智能分章节：preview 只切分不入库；import 单事务删旧写新（覆盖替换，强确认在面板）
  app.post("/admin/books/:id/txt-preview", { preHandler: requireAdmin, bodyLimit: 60 * 1024 * 1024 }, txtPreview);
  app.post("/admin/books/:id/txt-import", { preHandler: requireAdmin, bodyLimit: 120 * 1024 * 1024 }, txtImport);

  // 分类内书籍维度端点（第三维度，ADR 0011）
  registerCategoryBooks(app);

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

  // 下载二维码（仓库级 ADR 0013）：令牌与到期独立于设置表单，不参与 putSettings 白名单
  app.get("/admin/download-qr", { preHandler: requireAdmin }, async (_req, reply) => downloadQrState(reply));
  app.put("/admin/download-qr", { preHandler: requireAdmin }, putDownloadQr);

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

  // OSS 管理（面板直管对象）；目录白名单与媒体目录约定一致（uploads/ images/ lrc/，ADR 0004）。
  // 缩略图可浏览但不可直传：派生物不设上传位，上传白名单 ossDirs 不含它（ADR 0011 修订）
  const ossDirs = new Set(["uploads", "images", "lrc"]);
  const ossPrefixes = new Set(["", "uploads", "images", "images/thumbs", "lrc"]);

  // 缓存头批量操作入参校验：1~200 个 key，且首段目录落在媒体目录白名单内
  const parseCacheKeys = (raw: unknown): string[] | null => {
    if (!Array.isArray(raw) || raw.length === 0 || raw.length > 200) return null;
    const keys = raw.map(String);
    return keys.every((key) => ossDirs.has(key.split("/")[0] ?? "")) ? keys : null;
  };

  // 全量列举 + 服务端解密原名（ADR 0008）；keyword 过滤与分页均在前端做
  app.get(
    "/admin/oss/objects",
    { preHandler: requireAdmin },
    async (req, reply) => {
      const q = req.query as Record<string, string | undefined>;
      const prefix = str(q["prefix"] ?? "uploads").replace(/\/+$/, "");
      if (!ossPrefixes.has(prefix)) return void bad(reply, "invalid prefix");
      const { items, truncated } = await listObjects({
        prefix: prefix ? `${prefix}/` : undefined,
      });
      // 目录视图只看本层：前缀列举会把子目录对象（thumbs/ 等）混进来，按 key 段数滤掉
      const depth = prefix ? prefix.split("/").length + 1 : 0;
      const visible = depth ? items.filter((i) => i.key.split("/").length === depth) : items;
      // 最新优先（管理页与选择器共用本响应）；ali-oss 运行时返回 Date 对象，须转时间戳比较，不可字符串直比
      const ts = (v: string) => new Date(v).getTime();
      visible.sort((a, b) => ts(b.lastModified) - ts(a.lastModified));
      reply.send({ items: visible, truncated });
    },
  );

  // 单条解密：编辑表单绑定值显示原名用（ADR 0008）；name 为文件名尾段，解不开返回空对象由前端回退
  app.get("/admin/oss/decode", { preHandler: requireAdmin }, async (req, reply) => {
    const q = req.query as Record<string, string | undefined>;
    const filename = str(q["name"] ?? "");
    const original = filename ? decryptFilename(nameStem(filename)) : null;
    reply.send(original ? { originalName: original } : {});
  });

  // 直传到指定目录：uploads 走流式（大文件），images 复用 saveImage（同时出缩略图），lrc 存文本
  app.post("/admin/oss/upload", { preHandler: requireAdmin }, async (req, reply) => {
    const dir = str((req.query as Record<string, string | undefined>)["dir"] ?? "");
    if (!ossDirs.has(dir)) return void bad(reply, "dir must be uploads | images | lrc");
    let name: string | undefined;
    try {
      for await (const part of req.parts()) {
        if (part.type !== "file") continue;
        if (dir === "uploads") {
          name = await saveAudioStream(part);
        } else {
          const buffer = await part.toBuffer();
          if (buffer.length > 20 * 1024 * 1024) throw new Error("File too large (max 20MB)");
          name = dir === "lrc"
            ? await saveTextFile(buffer, part.filename || "file")
            : await saveImage(buffer, part.filename || "file");
        }
        break;
      }
    } catch (e) {
      return void bad(reply, e instanceof Error ? e.message : "upload failed");
    }
    if (!name) return void bad(reply, "file required");
    reply.send({ name });
  });

  // 删除对象：默认拒绝被业务数据引用的 key（列明引用处数），force=1 强制删除
  app.delete("/admin/oss/objects", { preHandler: requireAdmin }, async (req, reply) => {
    const q = req.query as Record<string, string | undefined>;
    const key = str(q["key"] ?? "");
    const dir = key.split("/")[0] ?? "";
    if (!key || !ossDirs.has(dir)) return void bad(reply, "key 必须位于 uploads/ images/ lrc/ 下");
    const name = key.split("/").pop()!;

    const usage: string[] = [];
    if (dir === "uploads") {
      const n = await prisma.song.count({ where: { type: "local", audioUrl: name } });
      if (n) usage.push(`歌曲音频 ${n} 处`);
    } else if (dir === "lrc") {
      const n = await prisma.song.count({ where: { lrcUrl: name } });
      if (n) usage.push(`歌曲歌词 ${n} 处`);
    } else {
      // images/ 与 images/thumbs/ 同名同源（DB 只存文件名，缩略图由原图同名派生），按名查引用
      const counts: number[] = await Promise.all([
        prisma.song.count({ where: { thumbnail: name } }),
        prisma.category.count({ where: { image: name } }),
        prisma.artist.count({ where: { image: name } }),
        prisma.album.count({ where: { image: name } }),
        prisma.banner.count({ where: { image: name } }),
        prisma.playlist.count({ where: { image: name } }),
        prisma.book.count({ where: { cover: name } }),
      ]);
      const labels = ["歌曲缩略图", "分类", "艺术家", "专辑", "横幅", "播放列表", "书籍封面"];
      counts.forEach((n, i) => {
        if (n) usage.push(`${labels[i]} ${n} 处`);
      });
    }
    if (usage.length && q["force"] !== "1") {
      return void bad(reply, `对象被引用：${usage.join("、")}；确认请加 force=1`);
    }
    await deleteObject(key);
    reply.send({ ok: true });
  });

  // 批量设置缓存头（CONTEXT「缓存头」）：前端按 ~50 keys/片分片提交，逐 key CopyObject 改元数据，
  // 失败逐条返回不中断整片，失败片由前端重试（断点续传）
  app.post("/admin/oss/cache-control", { preHandler: requireAdmin }, async (req, reply) => {
    const b = (req.body ?? {}) as { keys?: unknown; maxAge?: unknown };
    const keys = parseCacheKeys(b.keys);
    if (!keys) return void bad(reply, "keys 必须为 1~200 个、位于媒体目录内的 key 数组");
    const maxAge = Number(b.maxAge);
    if (!Number.isInteger(maxAge) || maxAge < 0 || maxAge > 10 * 365 * 24 * 3600) {
      return void bad(reply, "maxAge 必须为 0~3153600000 的整数秒");
    }
    const failed: { key: string; error: string }[] = [];
    for (const key of keys) {
      try {
        await setCacheControl(key, maxAge);
      } catch (e) {
        failed.push({ key, error: e instanceof Error ? e.message : "copy failed" });
      }
    }
    reply.send({ ok: failed.length === 0, failed });
  });

  // 批量查询缓存头（管理列惰性查看）：逐 key head，未设置/对象缺失返回 null
  app.post("/admin/oss/cache-control/query", { preHandler: requireAdmin }, async (req, reply) => {
    const b = (req.body ?? {}) as { keys?: unknown };
    const keys = parseCacheKeys(b.keys);
    if (!keys) return void bad(reply, "keys 必须为 1~200 个、位于媒体目录内的 key 数组");
    const items: { key: string; cacheControl: string | null }[] = [];
    const failed: { key: string; error: string }[] = [];
    for (const key of keys) {
      try {
        items.push({ key, cacheControl: await getCacheControl(key) });
      } catch (e) {
        failed.push({ key, error: e instanceof Error ? e.message : "head failed" });
      }
    }
    reply.send({ items, failed });
  });

  // 批量媒体混淆（仓库级 ADR 0011）：逐 key head 查 encrypted 元数据，已混淆跳过（幂等可反复执行）；
  // 否则 getStream → +31 流 → putStream 回写（encrypted 元数据与目录默认缓存头同 put 原子写入）。
  // 失败逐条返回不中断整片，与缓存头批量同款分片驱动；lrc/ 不参与（另有真加密，见「歌词密文」）
  const parseEncryptKeys = (raw: unknown): string[] | null => {
    if (!Array.isArray(raw) || raw.length === 0 || raw.length > 200) return null;
    const keys = raw.map(String);
    return keys.every((key) => {
      const dir = key.split("/")[0] ?? "";
      return dir === "uploads" || dir === "images";
    })
      ? keys
      : null;
  };
  app.post("/admin/oss/encrypt", { preHandler: requireAdmin }, async (req, reply) => {
    const b = (req.body ?? {}) as { keys?: unknown };
    const keys = parseEncryptKeys(b.keys);
    if (!keys) {
      return void bad(reply, "keys 必须为 1~200 个、位于 uploads/ 或 images/（含 thumbs）内的 key 数组");
    }
    const failed: { key: string; error: string }[] = [];
    let done = 0;
    let skipped = 0;
    for (const key of keys) {
      try {
        if (await isObfuscated(key)) {
          skipped++;
          continue;
        }
        const stream = await getStream(key);
        await putStream(key, stream.pipe(obfuscateStream()), { meta: OBFUSCATED_META });
        done++;
      } catch (e) {
        failed.push({ key, error: e instanceof Error ? e.message : "encrypt failed" });
      }
    }
    reply.send({ ok: failed.length === 0, done, skipped, failed });
  });

  // OneSignal 推送
  app.post("/admin/notifications", { preHandler: requireAdmin }, sendNotification);

  // ============ 访问统计（仓库级 ADR 0008，条数口径）============

  // 当日访问分解：装载次数 / 命中 / 未命中 / 活跃设备数 + 命中率 + 即时在线设备数。
  // online 与「活跃设备」口径不同：非当日窗口，为「在线状态」的即时推导（同采样器口径，见 services/stats.ts）
  // 日界按北京自然日（实现与理由见工具区 bjDayStart）
  app.get("/admin/stats/summary", { preHandler: requireAdmin }, async (_req, reply) => {
    const startOfDay = bjDayStart(Date.now());
    const where = { accessedAt: { gte: startOfDay } };
    const [total, hits, devices, online] = await Promise.all([
      prisma.accessFact.count({ where }),
      prisma.accessFact.count({ where: { ...where, cacheHit: true } }),
      prisma.accessFact.groupBy({ by: ["deviceId"], where, _count: { _all: true } }),
      computeOnlineCount(),
    ]);
    reply.send({
      total,
      hits,
      misses: total - hits,
      devices: devices.length,
      hitRate: total > 0 ? hits / total : 0,
      online,
    });
  });

  // 访问明细：分页 + left join 歌名（已删歌曲 title 为 null，前端显示「已删除歌曲」）；
  // 可选 deviceId 精确过滤（设备快照行下钻复用本列表）；无日期筛选，分页覆盖全表、不设查看上限
  // （无 housekeep，ADR 0008 2026-09-20 修订）
  app.get("/admin/stats/facts", { preHandler: requireAdmin }, async (req, reply) => {
    const { size, skip } = paging(req);
    const deviceId = str((req.query as Record<string, string | undefined>)["deviceId"]);
    const where = deviceId ? Prisma.sql`WHERE f.device_id = ${deviceId}` : Prisma.empty;
    const [count, rows] = await Promise.all([
      prisma.accessFact.count(deviceId ? { where: { deviceId } } : undefined),
      prisma.$queryRaw<FactRow[]>`
        SELECT f.id AS id, f.song_id AS songId, s.title AS songTitle,
               f.device_id AS deviceId, f.cache_hit AS cacheHit,
               f.app_version AS appVersion,
               f.ip_address AS ipAddress, f.accessed_at AS accessedAt
        FROM access_facts f
        LEFT JOIN songs s ON s.id = f.song_id
        ${where}
        ORDER BY f.id DESC
        LIMIT ${size} OFFSET ${skip}
      `,
    ]);
    reply.send({ total: count, items: rows });
  });

  // 设备快照：每设备最新一条事实的快照值（allocated/used、UA、最近上报 IP，快照长事实上，ADR 0008）。
  // 预期下线 = 最新事实时刻 + 曲时长 + 1 分钟冗余（口径同「在线状态」词条），歌删或时长缺失仅加冗余。
  // 分页在 SQL 层：「在线优先」是实时派生排序键，必须随 LIMIT 一起下推，组内保持最近访问倒序。
  // SQL 内时刻比较一律 UTC_TIMESTAMP()、不得用 NOW() —— Prisma 存 UTC 墙上时间但不钉会话时区，
  // 部署库 SYSTEM=CST 时 NOW() 会混入 8 小时偏差（computeOnlineCount / 趋势窗口同此约定）。
  // 展示的 isOnline 仍在 Node 侧比对（与写库同源的时钟基），与排序键仅可能在边界瞬间不同步：
  // 单行落进另一区块，亚秒级差异、刷新自愈。
  // 「最新一条」用每组 MAX(id) 回连：id 自增单调、插入序即时序，且无窗口函数（MySQL 5.7 不支持 OVER，部署库即 5.7）
  app.get("/admin/stats/devices", { preHandler: requireAdmin }, async (req, reply) => {
    const { size, skip } = paging(req);
    const [countRows, rows] = await Promise.all([
      // 设备数 = 有事实的设备数（本端点一行一设备）
      prisma.$queryRaw<{ total: bigint }[]>`SELECT COUNT(DISTINCT device_id) AS total FROM access_facts`,
      prisma.$queryRaw<DeviceRow[]>`
        SELECT f.device_id AS deviceId, f.allocated_storage + 0 AS allocatedStorage,
               f.used_storage + 0 AS usedStorage, f.user_agent AS userAgent,
               f.app_version AS appVersion, IFNULL(so.duration, 0) AS usedDuration,
               f.ip_address AS ipAddress, f.accessed_at AS lastSeen, s.facts AS facts,
               s.first_seen AS firstSeen,
               f.accessed_at + INTERVAL (IFNULL(so.duration, 0) + 60) SECOND AS expectedOfflineAt
        FROM access_facts f
        INNER JOIN (
          SELECT MAX(id) AS max_id, COUNT(*) AS facts, MIN(accessed_at) AS first_seen
          FROM access_facts
          GROUP BY device_id
        ) s ON s.max_id = f.id
        LEFT JOIN songs so ON so.id = f.song_id
        ORDER BY (f.accessed_at + INTERVAL (IFNULL(so.duration, 0) + 60) SECOND > UTC_TIMESTAMP()) DESC,
                 f.accessed_at DESC
        LIMIT ${size} OFFSET ${skip}
      `,
    ]);
    // BigInt 列（+0 后仍 BIGINT）、COUNT(*) 与 COUNT(DISTINCT) 聚合均以 BigInt 抵达，JSON.stringify 无法序列化 → 统一转 number
    const now = Date.now();
    const items = rows.map((r) => ({
      ...r,
      allocatedStorage: r.allocatedStorage == null ? null : Number(r.allocatedStorage),
      usedStorage: r.usedStorage == null ? null : Number(r.usedStorage),
      // IFNULL 表达式的协议类型为 LONGLONG → BigInt 抵达，与 facts/COUNT(*) 同坑
      usedDuration: Number(r.usedDuration),
      facts: Number(r.facts),
      isOnline: new Date(r.expectedOfflineAt).getTime() > now,
    }));
    reply.send({ total: Number(countRows[0]?.total ?? 0), items });
  });

  // 在线趋势：24h/7d 原始 5 分钟采样点；30d 小时平均（平均非峰值）
  app.get("/admin/stats/online-trend", { preHandler: requireAdmin }, async (req, reply) => {
    const range = str((req.query as Record<string, string | undefined>)["range"] ?? "24h");
    if (range === "30d") {
      const points = await prisma.$queryRaw<TrendPoint[]>`
        SELECT DATE_FORMAT(CONVERT_TZ(sampled_at, '+00:00', '+08:00'), '%Y-%m-%dT%H:00:00') AS bucket,
               ROUND(AVG(total_online)) AS totalOnline
        FROM online_samples
        WHERE sampled_at >= UTC_TIMESTAMP() - INTERVAL 30 DAY
        GROUP BY bucket
        ORDER BY bucket
      `;
      // ROUND(AVG()) 为 DECIMAL，$queryRaw 以 Decimal 对象抵达（toJSON 出字符串）→ 统一转 number
      return void reply.send({ points: points.map((p) => ({ ...p, totalOnline: Number(p.totalOnline) })) });
    }
    const hours = range === "7d" ? 168 : 24;
    const points = await prisma.onlineSample.findMany({
      where: { sampledAt: { gte: new Date(Date.now() - hours * 3_600_000) } },
      orderBy: { sampledAt: "asc" },
      select: { sampledAt: true, totalOnline: true },
    });
    reply.send({ points });
  });

  // 每日装载趋势：最近 7/30/90/365 个已结束的北京自然日（不含今天——今天由当日栏承担，
  // 半日数据上线会造成末点假跌），条数口径（仓库级 ADR 0008），即时聚合、不落日表：
  // 事实带服务端插入时刻且不可变，日终即终值，无需定时结算（见 ADR 0008 Considered Options）。
  // 日界与时刻比较同「当日」：CONVERT_TZ 显式把 UTC 墙上时间折到 +08:00，不依赖会话时区。
  // 无事实的自然日 SQL 不产生行，按窗口逐日补零（轴按日历日等距；断线会把「停用」读成连续）。
  app.get("/admin/stats/daily-trend", { preHandler: requireAdmin }, async (req, reply) => {
    const daysByRange: Record<string, number> = { "7d": 7, "30d": 30, "90d": 90, "1y": 365 };
    const days = daysByRange[str((req.query as Record<string, string | undefined>)["range"])] ?? 7;
    const todayStart = bjDayStart(Date.now());
    const from = new Date(todayStart.getTime() - days * 86_400_000);
    const rows = await prisma.$queryRaw<{ day: string; total: bigint; hits: bigint }[]>`
      SELECT DATE_FORMAT(CONVERT_TZ(accessed_at, '+00:00', '+08:00'), '%Y-%m-%d') AS day,
             COUNT(*) AS total, COUNT(IF(cache_hit = 1, 1, NULL)) AS hits
      FROM access_facts
      WHERE accessed_at >= ${from} AND accessed_at < ${todayStart}
      GROUP BY day
      ORDER BY day
    `;
    const byDay = new Map(rows.map((r) => [r.day, { total: Number(r.total), hits: Number(r.hits) }]));
    const points = Array.from({ length: days }, (_, i) => {
      // 北京日历日字符串：from 是北京零点的 UTC 表示，加偏移后取 ISO 日期即北京墙上日期
      const date = new Date(from.getTime() + i * 86_400_000 + BJ_OFFSET_MS).toISOString().slice(0, 10);
      const agg = byDay.get(date);
      const total = agg?.total ?? 0;
      const hits = agg?.hits ?? 0;
      return { date, total, hits, misses: total - hits };
    });
    reply.send({ points });
  });
}

// ---- 访问统计行类型（$queryRaw 原始返回；BIGINT/DECIMAL 以 BigInt/Decimal 对象抵达，出口统一转 number）

type FactRow = {
  id: number;
  songId: number;
  songTitle: string | null;
  deviceId: string;
  cacheHit: boolean | number;
  appVersion: string | null;
  ipAddress: string;
  accessedAt: Date;
};

type DeviceRow = {
  deviceId: string;
  allocatedStorage: number | string | bigint | null;
  usedStorage: number | string | bigint | null;
  userAgent: string | null;
  appVersion: string | null;
  usedDuration: number | string | bigint;
  ipAddress: string;
  lastSeen: Date;
  facts: number | string | bigint;
  firstSeen: Date;
  expectedOfflineAt: Date;
};

type TrendPoint = { bucket: string; totalOnline: number | string };
