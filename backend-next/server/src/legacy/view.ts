// Legacy 视图层：把新模型映射为旧协议的字段名与类型形态（Q17 语义兼容）
// 规则：数值字段一律字符串（旧 mysqli 返回字符串）；is_favourite 为布尔（PHP json_encode true/false）
import type { Album, Artist, Banner, Playlist, Song } from "@prisma/client";
import { audioUrl, imageUrl, lrcUrl, thumbUrl } from "../media/urls.js";

/** 带关联的歌曲模型（handlers 查询时 include）；category 可空（backend-next ADR 0007，App 端已被 songStatusFilter 过滤） */
export type SongWithRelations = Song & {
  category: { id: number; name: string; image: string } | null;
  album?: Album | null;
  artists: { sort: number; artist: Pick<Artist, "id" | "name"> }[];
};

export type FavouriteSet = Set<number>;

type SongCtx = {
  base: string;
  favourites?: FavouriteSet;
  /** song_info / single_song：附带歌词与用户评分 */
  detail?: boolean;
  userRate?: number;
};

const S = (v: number | string | null | undefined) => String(v ?? "");

export function songToLegacy(song: SongWithRelations, ctx: SongCtx): Record<string, unknown> {
  // 未分类歌曲不会进入任何 App 列表（songStatusFilter），此处空值回退仅为类型层防御
  const cat = song.category;
  const row: Record<string, unknown> = {
    id: S(song.id),
    cat_id: S(song.categoryId),
    mp3_type: song.type,
    mp3_title: song.title,
    mp3_url: audioUrl(ctx.base, song.type, song.audioUrl),
    mp3_thumbnail_b: imageUrl(ctx.base, song.thumbnail),
    mp3_thumbnail_s: thumbUrl(ctx.base, song.thumbnail),
    mp3_artist: song.artists
      .slice()
      .sort((a, b) => a.sort - b.sort)
      .map((sa) => sa.artist.name)
      .join(","),
    mp3_description: song.description,
    total_rate: S(song.totalRate),
    rate_avg: S(song.rateAvg),
    total_views: S(song.totalViews),
    total_download: S(song.totalDownload),
    is_favourite: ctx.favourites ? ctx.favourites.has(song.id) : false,
    cid: S(cat?.id),
    category_name: cat?.name ?? "",
    category_image: cat ? imageUrl(ctx.base, cat.image) : "",
    category_image_thumb: cat ? thumbUrl(ctx.base, cat.image) : "",
  };

  if (song.albumId != null) row.album_id = S(song.albumId);

  if (ctx.detail) {
    // 旧库无 lrc 列时 PHP undefined index 输出 null；mp3_lrc_url 恒为 base+'lrc/'+文件名
    row.mp3_lrc_txt = song.lrcText ?? null;
    row.mp3_lrc_url = lrcUrl(ctx.base, song.lrcUrl);
    if (ctx.userRate !== undefined) row.user_rate = ctx.userRate;
  }

  return row;
}

/** album_songs 响应附带的专辑字段 */
export function appendAlbumFields(
  row: Record<string, unknown>,
  album: Album | null | undefined,
  base: string,
): void {
  if (!album) return;
  row.aid = S(album.id);
  row.album_name = album.name;
  row.album_image = imageUrl(base, album.image);
  row.album_image_thumb = thumbUrl(base, album.image);
}

export type BannerWithSongs = Banner & {
  // banners() 列表分支不 include song，因此为可选
  songs: { sort: number; song?: SongWithRelations }[];
};

export function bannerToLegacy(
  banner: BannerWithSongs,
  base: string,
  withSongs: boolean,
  favourites?: FavouriteSet,
): Record<string, unknown> {
  const row: Record<string, unknown> = {
    bid: S(banner.id),
    banner_title: banner.title,
    banner_sort_info: banner.sortInfo,
    banner_link: banner.link ?? "",
    banner_image: imageUrl(base, banner.image),
    banner_image_thumb: thumbUrl(base, banner.image),
  };
  if (withSongs) {
    const songs = banner.songs
      .slice()
      .sort((a, b) => a.sort - b.sort)
      .map((bs) => bs.song)
      .filter((s): s is SongWithRelations => !!s);
    row.total_songs = songs.length;
    row.songs_list = songs.map((s) => songToLegacy(s, { base, favourites }));
  }
  return row;
}

export function albumToLegacy(album: Album, base: string): Record<string, unknown> {
  return {
    aid: S(album.id),
    album_name: album.name,
    album_image: imageUrl(base, album.image),
    album_image_thumb: thumbUrl(base, album.image),
  };
}

export function artistToLegacy(artist: Artist, base: string): Record<string, unknown> {
  return {
    id: S(artist.id),
    artist_name: artist.name,
    artist_image: imageUrl(base, artist.image),
    artist_image_thumb: thumbUrl(base, artist.image),
  };
}

export function playlistToLegacy(playlist: Playlist, base: string): Record<string, unknown> {
  return {
    pid: S(playlist.id),
    playlist_name: playlist.name,
    playlist_image: imageUrl(base, playlist.image),
    playlist_image_thumb: thumbUrl(base, playlist.image),
  };
}
