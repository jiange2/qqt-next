// /legacy 门面路由：POST /api.php 与 /legacy/api.php 同一实现（Q9 双路径）
// 请求编码兼容 urlencoded（data=base64(urlencode(json))）与 multipart（song_suggest 附带图片）
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { checkSignSalt } from "./protocol.js";
import { mediaBase } from "../media/urls.js";
import { getSettings } from "../services/settings.js";
import {
  albumList,
  albumSongs,
  allSongs,
  artistAlbumList,
  artistList,
  artistNameSongs,
  bannerSongs,
  banners,
  bookChapter,
  bookChapters,
  catAlbums,
  catBooks,
  catList,
  home,
  latest,
  playlist,
  playlistSongs,
  recentArtistList,
  songDetail,
  songDownload,
  songSearch,
  type LegacyCtx,
} from "./handlers-content.js";
import {
  favouritePost,
  forgotPass,
  getFavouritePost,
  getRecentSongs,
  userLogin,
  userProfile,
  userProfileUpdate,
  userRegister,
} from "./handlers-user.js";
import { appDetails, songRating, songReport, songSuggest } from "./handlers-misc.js";
import { recordSongAccess, updateSongDuration } from "./handlers-stats.js";

export type UploadedImage = { buffer: Buffer; name: string };
type Handler = (ctx: LegacyCtx, image?: UploadedImage) => Promise<unknown>;

/** 旧实现仅这三个 method 的 ONLINE_MP3 直接是对象而非数组 */
const OBJECT_PAYLOAD = new Set(["home", "home_new", "song_search"]);

const methods: Record<string, Handler> = {
  home: (ctx) => home(ctx),
  home_new: (ctx) => home(ctx),
  all_songs: allSongs,
  latest,
  banners,
  banner_songs: bannerSongs,
  cat_list: catList,
  cat_albums: catAlbums, // 分类专辑列表（backend-next ADR 0009，替代已删除的 cat_songs）
  cat_books: catBooks, // 分类书籍列表（书籍阅读域 ADR 0011，形态对齐 cat_albums）
  book_chapters: bookChapters, // 全书章节目录（按章节顺序一次下发，ADR 0011 修订）
  book_chapter: bookChapter, // 单章正文（handler 返回单对象，外包数组同 song_info）
  recent_artist_list: recentArtistList,
  artist_list: artistList,
  artist_album_list: artistAlbumList,
  artist_name_songs: artistNameSongs,
  album_list: albumList,
  album_songs: albumSongs,
  playlist,
  playlist_songs: playlistSongs,
  song_info: songDetail,
  single_song: songDetail,
  song_download: songDownload,
  song_search: songSearch,
  song_rating: songRating,
  song_report: songReport,
  song_suggest: (ctx, image) => songSuggest(ctx, image),
  // 访问统计（仓库级 ADR 0008）：纯增量 method，旧客户端不感知
  record_song_access: recordSongAccess,
  update_song_duration: updateSongDuration,
  user_register: userRegister,
  user_login: userLogin,
  user_profile: userProfile,
  user_profile_update: userProfileUpdate,
  forgot_pass: forgotPass,
  favourite_post: favouritePost,
  get_favourite_post: getFavouritePost,
  get_recent_songs: getRecentSongs,
  app_details: appDetails,
};

async function handleLegacy(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  let raw: string | undefined;
  let image: UploadedImage | undefined;

  if (req.isMultipart()) {
    for await (const part of req.parts()) {
      if (part.type === "file") {
        if (!image) {
          image = { buffer: await part.toBuffer(), name: part.filename || "image.jpg" };
        }
      } else if (part.fieldname === "data") {
        raw = String(part.value ?? "");
      }
    }
  } else {
    const body = (req.body ?? {}) as Record<string, unknown>;
    raw = typeof body["data"] === "string" ? body["data"] : undefined;
  }

  // 验签失败时 checkSignSalt 已直接写出错误响应
  const data = await checkSignSalt(raw, reply);
  if (!data) return;

  const handler = methods[data["method_name"] ?? ""];
  if (!handler) {
    // 旧实现对未知 method 不输出任何内容
    reply.code(200).send("");
    return;
  }

  const ctx: LegacyCtx = {
    base: mediaBase(),
    data,
    settings: await getSettings(),
    // 取头顺序（可信度递减）：XFF 首段（多级代理 append 链上最接近真实客户端）→ X-Real-IP
    // （部署手册 nginx 反代 set 的 $remote_addr，单级反代下与首段等价）→ req.ip（socket 对端，
    // 反代场景是代理自身地址，即统计页看到反代 IP 的坑源）。头均可伪造，IP 仅作排障信号非审计凭据。
    ip:
      (req.headers["x-forwarded-for"] as string | undefined)?.split(",")[0]?.trim() ||
      (req.headers["x-real-ip"] as string | undefined)?.trim() ||
      req.ip ||
      "unknown",
    userAgent: typeof req.headers["user-agent"] === "string" ? req.headers["user-agent"] : "",
  };
  const result = await handler(ctx, image);
  reply.send({
    ONLINE_MP3: OBJECT_PAYLOAD.has(data["method_name"]!)
      ? result
      : Array.isArray(result)
        ? result
        : [result],
  });
}

export async function legacyRoutes(app: FastifyInstance): Promise<void> {
  app.post("/api.php", handleLegacy);
  app.post("/legacy/api.php", handleLegacy);
}
