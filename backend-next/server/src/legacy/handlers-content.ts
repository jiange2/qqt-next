// Legacy 门面 —— 内容类 method（Q21：与 api.php 逐一对齐，共 20 个）
// 每个函数与旧 api.php 对应分支语义等价；已知缺陷不复刻（ADR 0003 决定 1）
import { prisma } from "../prisma.js";
import type { Prisma, Setting } from "@prisma/client";
import { getSettings, parseOrderBy } from "../services/settings.js";
import {
  albumToLegacy,
  appendAlbumFields,
  artistToLegacy,
  bannerToLegacy,
  playlistToLegacy,
  songToLegacy,
  type FavouriteSet,
  type SongWithRelations,
} from "./view.js";
import { S } from "./shared.js";

export type LegacyCtx = {
  base: string;
  data: Record<string, string>;
  settings: Awaited<ReturnType<typeof getSettings>>;
  /** 客户端 IP（x-forwarded-for 首段 → x-real-ip → req.ip）与 User-Agent，访问事实落库用（仓库级 ADR 0008） */
  ip: string;
  userAgent: string;
};

const songInclude = {
  category: { select: { id: true, name: true, image: true, status: true, isPrivate: true } },
  album: true,
  artists: {
    orderBy: { sort: "asc" as const },
    select: { sort: true, artist: { select: { id: true, name: true } } },
  },
} satisfies Prisma.SongInclude;

export const songIncludeForQuery = songInclude;

function pageOf(data: Record<string, string>): number {
  const n = Number.parseInt(data["page"] ?? "1", 10);
  return Number.isFinite(n) && n > 0 ? n : 1;
}

function limitOffset(page: number, size: number) {
  return { take: size, skip: (page - 1) * size };
}

/** 与旧 is_favourite() 一致：不校验 type 之外的语义，默认 song */
export async function favouriteSetOf(userIdRaw?: string): Promise<FavouriteSet | undefined> {
  if (!userIdRaw || userIdRaw === "0") return undefined;
  const userId = Number(userIdRaw);
  if (!Number.isFinite(userId) || userId <= 0) return undefined;
  const favs = await prisma.favourite.findMany({
    where: { userId, type: "song" },
    select: { postId: true },
  });
  return new Set(favs.map((f) => f.postId));
}

// 归属链（backend-next ADR 0009）：歌曲对 App 可见要求自身、所属专辑、专辑所属分类均启用；
// 未归专辑歌曲与未分类专辑内歌曲经此过滤天然不可见。隐私模式下额外要求全链非隐私（ADR 0012）。
export function songAppVisibleFilter(settings: Setting): Prisma.SongWhereInput {
  if (settings.privacyMode === "true") {
    return {
      status: true,
      isPrivate: false,
      album: { status: true, isPrivate: false, category: { status: true, isPrivate: false } },
    };
  }
  return { status: true, album: { status: true, category: { status: true } } };
}

/** 专辑对 App 可见要求自身启用且已归入启用分类（未分类专辑仅存在于后台，ADR 0009） */
function albumAppVisibleFilter(settings: Setting): Prisma.AlbumWhereInput {
  if (settings.privacyMode === "true") {
    return { status: true, isPrivate: false, category: { status: true, isPrivate: false } };
  }
  return { status: true, category: { status: true } };
}

// ---------------------------------------------------------------- home / home_new

/** home 与 home_new 统一实现（旧实现的 banner 空 songs_list / 播放量热门均为缺陷，不复刻） */
export async function home(ctx: LegacyCtx): Promise<unknown> {
  const { base, settings } = ctx;
  const favourites = await favouriteSetOf(ctx.data["user_id"]);
  const limit = settings.apiLatestLimit;

  const banners = await prisma.banner.findMany({
    where: { status: true },
    orderBy: { id: "desc" },
    include: {
      songs: {
        where: { song: songAppVisibleFilter(settings) },
        orderBy: { sort: "asc" },
        include: { song: { include: songInclude } },
      },
    },
  });

  const albums = await prisma.album.findMany({
    where: albumAppVisibleFilter(settings),
    orderBy: { id: "desc" },
    take: limit,
  });

  const artists = await prisma.artist.findMany({
    orderBy: { id: "desc" },
    take: limit,
  });

  const trending = await prisma.trendingSong.findMany({
    // 1:1 嵌套 include 不支持 where（Prisma 6 类型限制），改在外层过滤下架歌曲
    where: { song: songAppVisibleFilter(settings) },
    orderBy: { sort: "asc" },
    take: 50,
    include: { song: { include: songInclude } },
  });

  return {
    home_banner: banners.map((b) => bannerToLegacy(b, base, true, favourites)),
    latest_album: albums.map((a) => albumToLegacy(a, base)),
    latest_artist: artists.map((a) => artistToLegacy(a, base)),
    trending_songs: trending.map((t) => songToLegacy(t.song, { base, favourites })),
  };
}

// ---------------------------------------------------------------- 列表类

export async function allSongs(ctx: LegacyCtx): Promise<unknown> {
  const { base, settings } = ctx;
  const favourites = await favouriteSetOf(ctx.data["user_id"]);
  const where = songAppVisibleFilter(settings);
  const total = await prisma.song.count({ where });
  const rows = await prisma.song.findMany({
    where,
    orderBy: { id: "desc" },
    ...limitOffset(pageOf(ctx.data), 10),
    include: songInclude,
  });
  return rows.map((s) => ({
    total_songs: S(total),
    ...songToLegacy(s, { base, favourites }),
  }));
}

export async function latest(ctx: LegacyCtx): Promise<unknown> {
  const { base, settings } = ctx;
  const favourites = await favouriteSetOf(ctx.data["user_id"]);
  const where = songAppVisibleFilter(settings);
  const total = await prisma.song.count({ where });
  const rows = await prisma.song.findMany({
    where,
    orderBy: { id: "desc" },
    ...limitOffset(pageOf(ctx.data), settings.apiLatestLimit),
    include: songInclude,
  });
  return rows.map((s) => ({
    total_records: S(total),
    ...songToLegacy(s, { base, favourites }),
  }));
}

// ---------------------------------------------------------------- banners

export async function banners(ctx: LegacyCtx): Promise<unknown> {
  const rows = await prisma.banner.findMany({
    where: { status: true },
    orderBy: { id: "desc" },
    include: { songs: { orderBy: { sort: "asc" } } },
  });
  return rows.map((b) => bannerToLegacy(b, ctx.base, false));
}

export async function bannerSongs(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const favourites = await favouriteSetOf(data["user_id"]);
  const bannerId = Number(data["banner_id"]);
  if (!Number.isFinite(bannerId)) return [];

  const banner = await prisma.banner.findFirst({
    where: { id: bannerId, status: true },
    include: {
      songs: {
        where: { song: songAppVisibleFilter(settings) },
        orderBy: { sort: "asc" },
        include: { song: { include: songInclude } },
      },
    },
  });
  if (!banner) return [];

  const postOrderBy = parseOrderBy(`id ${settings.apiCatPostOrderBy.includes("desc") ? "DESC" : "ASC"}`, { id: "id" });
  const dir = postOrderBy["id"] === "desc" ? -1 : 1;
  const songs = banner.songs
    .map((bs) => bs.song)
    .sort((a, b) => dir * (a.id - b.id));

  const total = songs.length;
  const { skip, take } = limitOffset(pageOf(data), 10);
  return songs.slice(skip, skip + take).map((s) => {
    const row: Record<string, unknown> = {
      total_records: S(total),
      ...songToLegacy(s, { base, favourites }),
    };
    // 旧实现的 banner_songs 分支会把分类 id 写入 link 字段（无害怪癖，逐字复刻）
    row.link = S(s.categoryId);
    return row;
  });
}

// ---------------------------------------------------------------- 分类

export async function catList(ctx: LegacyCtx): Promise<unknown> {
  const { base, settings } = ctx;
  const catWhere: Prisma.CategoryWhereInput = settings.privacyMode === "true"
    ? { status: true, isPrivate: false }
    : { status: true };
  const total = await prisma.category.count({ where: catWhere });
  const orderBy = parseOrderBy(settings.apiCatOrderBy, { id: "id", name: "name" });
  const rows = await prisma.category.findMany({
    where: catWhere,
    orderBy,
    ...limitOffset(pageOf(ctx.data), 10),
  });
  return rows.map((c) => ({
    total_records: S(total),
    cid: S(c.id),
    category_name: c.name,
    // 分类类型（书籍阅读域 ADR 0011）：1=音乐，2=书籍；App 据此分流点击去向
    category_type: S(c.type === "book" ? 2 : 1),
    category_image: `${base}images/${c.image}`,
    category_image_thumb: `${base}images/thumbs/${c.image}`,
  }));
}

// 分类专辑列表（backend-next ADR 0009，替代已删除的 cat_songs）：分类下放专辑而非歌曲，
// 行结构复用 album_list（Android 端 Album 模型零改动）；维度顺序范式与 album_songs 对称
export async function catAlbums(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const catId = Number(data["cat_id"]);
  if (!Number.isFinite(catId)) return [];
  const where: Prisma.AlbumWhereInput = { ...albumAppVisibleFilter(settings), categoryId: catId };
  const total = await prisma.album.count({ where });
  const rows = await prisma.album.findMany({
    where,
    orderBy: [{ categorySort: "asc" }, { id: "desc" }],
    ...limitOffset(pageOf(data), 10),
  });
  return rows.map((a) => ({
    total_records: S(total),
    aid: S(a.id),
    album_name: a.name,
    album_image: `${base}images/${a.image}`,
    album_image_thumb: `${base}images/thumbs/${a.image}`,
  }));
}

// ---------------------------------------------------------------- 书籍（书籍阅读域 ADR 0011）

/** 分类书籍列表：分页/形态完全对齐 cat_albums，行字段换书籍域命名；封面与其它业务图同库 images/（ADR 0011 修订：目录归一） */
export async function catBooks(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const catId = Number(data["cat_id"]);
  if (!Number.isFinite(catId)) return [];
  const catFilter: Prisma.CategoryWhereInput = settings.privacyMode === "true"
    ? { status: true, isPrivate: false }
    : { status: true };
  // 书籍可见性 = 书籍启用且所属分类启用（对齐 albumStatusFilter，ADR 0009/0011）
  const where: Prisma.BookWhereInput = { status: true, category: catFilter, categoryId: catId };
  const total = await prisma.book.count({ where });
  const rows = await prisma.book.findMany({
    where,
    // 维度顺序（ADR 0011）：分类内书籍排序，与 cat_albums 对称
    orderBy: [{ categorySort: "asc" }, { id: "desc" }],
    ...limitOffset(pageOf(data), 10),
  });
  return rows.map((b) => ({
    total_records: S(total),
    book_id: S(b.id),
    book_name: b.name,
    book_author: b.author,
    book_cover: `${base}images/${b.cover}`,
    book_cover_thumb: `${base}images/thumbs/${b.cover}`,
  }));
}

/** 全书章节目录：按章节顺序一次下发（chapterSort ASC + id ASC 兜底，ADR 0011 修订）；书籍或分类不可见返回空 */
export async function bookChapters(ctx: LegacyCtx): Promise<unknown> {
  const { settings } = ctx;
  const bookId = Number(ctx.data["book_id"]);
  if (!Number.isFinite(bookId)) return [];
  const catFilter: Prisma.CategoryWhereInput = settings.privacyMode === "true"
    ? { status: true, isPrivate: false }
    : { status: true };
  const book = await prisma.book.findFirst({
    where: { id: bookId, status: true, category: catFilter },
  });
  if (!book) return [];
  const rows = await prisma.chapter.findMany({
    where: { bookId },
    orderBy: [{ chapterSort: "asc" }, { id: "asc" }],
    select: { id: true, title: true },
  });
  return rows.map((ch) => ({
    chapter_id: S(ch.id),
    chapter_title: ch.title,
  }));
}

/** 单章正文：handler 返回单对象，routes 层自动外包数组（同 song_info 范式） */
export async function bookChapter(ctx: LegacyCtx): Promise<unknown> {
  const { settings } = ctx;
  const chapterId = Number(ctx.data["chapter_id"]);
  if (!Number.isFinite(chapterId)) return {};
  const catFilter: Prisma.CategoryWhereInput = settings.privacyMode === "true"
    ? { status: true, isPrivate: false }
    : { status: true };
  // 经章节反查归属链校验可见性：下架书/停用分类的章节不可读
  const ch = await prisma.chapter.findFirst({
    where: { id: chapterId, book: { status: true, category: catFilter } },
    select: { id: true, title: true, content: true },
  });
  if (!ch) return {};
  return {
    chapter_id: S(ch.id),
    chapter_title: ch.title,
    content: ch.content,
  };
}

// ---------------------------------------------------------------- 艺术家

export async function recentArtistList(ctx: LegacyCtx): Promise<unknown> {
  const rows = await prisma.artist.findMany({ orderBy: { id: "desc" }, take: 10 });
  return rows.map((a) => artistToLegacy(a, ctx.base));
}

export async function artistList(ctx: LegacyCtx): Promise<unknown> {
  const total = await prisma.artist.count();
  // 注意：旧实现 artist_list 为 id 升序（与其余列表相反），复刻
  const rows = await prisma.artist.findMany({
    orderBy: { id: "asc" },
    ...limitOffset(pageOf(ctx.data), 10),
  });
  return rows.map((a) => ({
    total_records: S(total),
    ...artistToLegacy(a, ctx.base),
  }));
}

export async function artistAlbumList(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const artistId = Number(data["artist_id"]);
  if (!Number.isFinite(artistId)) return [];
  const where: Prisma.AlbumWhereInput = {
    ...albumAppVisibleFilter(settings),
    artists: { some: { artistId } },
  };
  const total = await prisma.album.count({ where });
  const rows = await prisma.album.findMany({
    where,
    orderBy: { id: "desc" },
    ...limitOffset(pageOf(data), 10),
    include: { artists: { orderBy: { sort: "asc" } } },
  });
  return rows.map((a) => ({
    total_records: S(total),
    aid: S(a.id),
    artist_ids: a.artists.map((aa) => aa.artistId).join(","),
    album_name: a.name,
    album_image: `${base}images/${a.image}`,
    album_image_thumb: `${base}images/thumbs/${a.image}`,
  }));
}

export async function artistNameSongs(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const favourites = await favouriteSetOf(data["user_id"]);
  const artistName = data["artist_name"] ?? "";
  const where: Prisma.SongWhereInput = {
    ...songAppVisibleFilter(settings),
    artists: { some: { artist: { name: artistName } } },
  };
  const total = await prisma.song.count({ where });
  const rows = await prisma.song.findMany({
    where,
    orderBy: { id: "desc" },
    ...limitOffset(pageOf(data), 10),
    include: songInclude,
  });
  return rows.map((s) => ({
    total_records: S(total),
    ...songToLegacy(s, { base, favourites }),
  }));
}

// ---------------------------------------------------------------- 专辑

/** 专辑歌曲页整单加载上限（仓库级 ADR 0007）：App 一次性拉全量，超限静默截断防失控 */
const ALBUM_SONGS_MAX = 2000;

export async function albumList(ctx: LegacyCtx): Promise<unknown> {
  const { settings } = ctx;
  const where = albumAppVisibleFilter(settings);
  const total = await prisma.album.count({ where });
  const rows = await prisma.album.findMany({
    where,
    orderBy: { id: "desc" },
    ...limitOffset(pageOf(ctx.data), 10),
  });
  return rows.map((a) => ({
    total_records: S(total),
    aid: S(a.id),
    album_name: a.name,
    album_image: `${ctx.base}images/${a.image}`,
    album_image_thumb: `${ctx.base}images/thumbs/${a.image}`,
  }));
}

export async function albumSongs(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const favourites = await favouriteSetOf(data["user_id"]);
  const albumId = Number(data["album_id"]);
  if (!Number.isFinite(albumId)) return [];
  const where: Prisma.SongWhereInput = {
    ...songAppVisibleFilter(settings),
    albumId,
  };
  const total = await prisma.song.count({ where });
  // 维度顺序优先（backend-next ADR 0007）：管理员手动排序生效；id DESC 兜底（未排序存量新歌在前）；旧实现按歌名排序已废弃
  // 整单加载（仓库级 ADR 0007）：忽略 page，单次返回全量，仅以 ALBUM_SONGS_MAX 兜底截断；total_records 仍为真实总数
  const rows = await prisma.song.findMany({
    where,
    orderBy: [{ albumSort: "asc" }, { id: "desc" }],
    take: ALBUM_SONGS_MAX,
    include: songInclude,
  });
  return rows.map((s) => {
    const row = { total_records: S(total), ...songToLegacy(s, { base, favourites }) };
    appendAlbumFields(row, s.album, base);
    return row;
  });
}

// ---------------------------------------------------------------- 播放列表

export async function playlist(ctx: LegacyCtx): Promise<unknown> {
  const total = await prisma.playlist.count({ where: { status: true } });
  const rows = await prisma.playlist.findMany({
    where: { status: true },
    orderBy: { id: "desc" },
    ...limitOffset(pageOf(ctx.data), 10),
  });
  return rows.map((p) => ({
    total_records: S(total),
    ...playlistToLegacy(p, ctx.base),
  }));
}

export async function playlistSongs(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const favourites = await favouriteSetOf(data["user_id"]);
  const playlistId = Number(data["playlist_id"]);
  if (!Number.isFinite(playlistId)) return [];
  const playlist = await prisma.playlist.findFirst({
    where: { id: playlistId, status: true },
    include: {
      songs: {
        where: { song: songAppVisibleFilter(settings) },
        orderBy: { sort: "asc" },
        include: { song: { include: songInclude } },
      },
    },
  });
  if (!playlist) return [];
  const all = playlist.songs.map((ps) => ps.song);
  const total = all.length;
  const { skip, take } = limitOffset(pageOf(data), 10);
  return [
    {
      ...playlistToLegacy(playlist, base),
      songs_list: all.slice(skip, skip + take).map((s) => ({
        total_records: S(total),
        ...songToLegacy(s, { base, favourites }),
      })),
    },
  ];
}

// ---------------------------------------------------------------- 歌曲详情

export async function songDetail(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const songId = Number(data["song_id"]);
  if (!Number.isFinite(songId)) return [];
  const song = await prisma.song.findFirst({
    where: { id: songId, ...songAppVisibleFilter(settings) },
    include: songInclude,
  });
  if (!song) return [];

  // 详情请求计一次播放（旧实现同时维护 tbl_mp3_views，按 ADR 0003 决定 4 移除）
  await prisma.song.update({ where: { id: songId }, data: { totalViews: { increment: 1 } } });

  let userRate: number | undefined;
  const userIdRaw = data["user_id"];
  if (userIdRaw) {
    const rating = await prisma.rating.findUnique({
      where: { postId_userId: { postId: songId, userId: Number(userIdRaw) } },
      select: { rate: true },
    });
    userRate = rating?.rate ?? 0;
  }

  return [songToLegacy(song, { base, detail: true, userRate })];
}

export async function songDownload(ctx: LegacyCtx): Promise<unknown> {
  const { settings } = ctx;
  const songId = Number(ctx.data["song_id"]);
  if (!Number.isFinite(songId)) return [];
  // 隐私模式下先校验歌曲可见性（ADR 0012），不可见则拒返下载计数
  if (settings.privacyMode === "true") {
    const visible = await prisma.song.findFirst({
      where: { id: songId, status: true, isPrivate: false, album: { status: true, isPrivate: false, category: { status: true, isPrivate: false } } },
      select: { id: true },
    });
    if (!visible) return [];
  }
  const song = await prisma.song.update({
    where: { id: songId },
    data: { totalDownload: { increment: 1 } },
    select: { totalDownload: true },
  });
  return [{ total_download: S(song.totalDownload) }];
}

// ---------------------------------------------------------------- 搜索

export async function songSearch(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const favourites = await favouriteSetOf(data["user_id"]);
  const text = data["search_text"] ?? "";
  const type = data["search_type"] ?? "";
  const page = pageOf(data);

  if (type === "songs") {
    const where: Prisma.SongWhereInput = { ...songAppVisibleFilter(settings), title: { contains: text } };
    const total = await prisma.song.count({ where });
    const rows = await prisma.song.findMany({
      where,
      orderBy: { title: "asc" },
      ...limitOffset(page, 10),
      include: songInclude,
    });
    return rows.map((s) => ({ total_data: S(total), ...songToLegacy(s, { base, favourites }) }));
  }

  if (type === "artist") {
    const where: Prisma.ArtistWhereInput = { name: { contains: text } };
    const total = await prisma.artist.count({ where });
    const rows = await prisma.artist.findMany({
      where,
      orderBy: { name: "asc" },
      ...limitOffset(page, 10),
    });
    return rows.map((a) => ({ total_data: S(total), ...artistToLegacy(a, base) }));
  }

  if (type === "album") {
    const where: Prisma.AlbumWhereInput = {
      ...albumAppVisibleFilter(settings),
      name: { contains: text },
    };
    const total = await prisma.album.count({ where });
    const rows = await prisma.album.findMany({
      where,
      orderBy: { name: "asc" },
      ...limitOffset(page, 10),
      include: { artists: { orderBy: { sort: "asc" } } },
    });
    return rows.map((a) => ({
      total_data: S(total),
      aid: S(a.id),
      artist_ids: a.artists.map((aa) => aa.artistId).join(","),
      album_name: a.name,
      album_image: `${base}images/${a.image}`,
      album_image_thumb: `${base}images/thumbs/${a.image}`,
    }));
  }

  // 组合搜索（旧实现的 else 分支）：歌曲按关键词过滤——旧后端本就有 LIKE 过滤，复刻时漏掉，
  // 此处补齐以对齐语义（复刻初期缺陷：组合搜索歌曲不按关键词过滤）
  const [songs, albums, artists] = await Promise.all([
    prisma.song.findMany({
      where: { ...songAppVisibleFilter(settings), title: { contains: text } },
      orderBy: { title: "asc" },
      ...limitOffset(page, 10),
      include: songInclude,
    }),
    prisma.album.findMany({
      where: { ...albumAppVisibleFilter(settings), name: { contains: text } },
      orderBy: { name: "asc" },
      take: 20,
      include: { artists: { orderBy: { sort: "asc" } } },
    }),
    prisma.artist.findMany({
      where: { name: { contains: text } },
      orderBy: { name: "asc" },
      take: 20,
    }),
  ]);
  return {
    search_songs: songs.map((s) => songToLegacy(s, { base, favourites })),
    search_album: albums.map((a) => ({
      aid: S(a.id),
      artist_ids: a.artists.map((aa) => aa.artistId).join(","),
      album_name: a.name,
      album_image: `${base}images/${a.image}`,
      album_image_thumb: `${base}images/thumbs/${a.image}`,
    })),
    search_artist: artists.map((a) => artistToLegacy(a, base)),
  };
}