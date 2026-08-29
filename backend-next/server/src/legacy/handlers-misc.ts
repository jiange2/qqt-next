// Legacy 门面 —— 杂项 method（song_rating / song_report / song_suggest / app_details）
import { prisma } from "../prisma.js";
import { appLang } from "./lang.js";
import { S } from "./shared.js";
import type { LegacyCtx } from "./handlers-content.js";
import { saveImage } from "../media/save.js";

// ---------------------------------------------------------------- song_rating

export async function songRating(ctx: LegacyCtx): Promise<unknown> {
  const postId = Number(ctx.data["post_id"]);
  const userId = Number(ctx.data["user_id"]);
  const song = await prisma.song.findUnique({ where: { id: postId }, select: { id: true } });
  if (!song) return { msg: appLang.no_data_msg, success: "0" };

  const existed = await prisma.rating.findUnique({
    where: { postId_userId: { postId, userId } },
    select: { id: true },
  });
  if (existed) return { msg: appLang.rate_already, success: "0" };

  // 旧实现直接写入原始 rate 值（MySQL int 列强转），保持一致
  const rate = Number(ctx.data["rate"] ?? 0) || 0;
  await prisma.rating.create({ data: { postId, userId, rate } });

  const agg = await prisma.rating.aggregate({ where: { postId }, _avg: { rate: true } });
  const rateAvg = Math.round(agg._avg.rate ?? 0);
  const updated = await prisma.song.update({
    where: { id: postId },
    data: { totalRate: { increment: 1 }, rateAvg },
    select: { totalRate: true, rateAvg: true },
  });
  return {
    total_rate: S(updated.totalRate),
    rate_avg: S(updated.rateAvg),
    msg: appLang.rate_success,
    success: "1",
  };
}

// ---------------------------------------------------------------- song_report

export async function songReport(ctx: LegacyCtx): Promise<unknown> {
  await prisma.report.create({
    data: {
      userId: Number(ctx.data["user_id"]),
      songId: Number(ctx.data["song_id"]),
      report: (ctx.data["report"] ?? "").trim(),
    },
  });
  return { msg: appLang.report_success, success: "1" };
}

// ---------------------------------------------------------------- song_suggest

/** image 由路由层从 multipart 解出（旧实现经 compress_image 质量 80 存 images/） */
export async function songSuggest(
  ctx: LegacyCtx,
  image?: { buffer: Buffer; name: string },
): Promise<unknown> {
  const songImage = image ? await saveImage(image.buffer, image.name, 80) : null;
  await prisma.songSuggest.create({
    data: {
      userId: Number(ctx.data["user_id"]),
      songTitle: (ctx.data["song_title"] ?? "").trim(),
      songImage,
      message: ctx.data["message"] ?? "",
    },
  });
  return { msg: appLang.suggest_success, success: "1" };
}

// ---------------------------------------------------------------- app_details

export async function appDetails(ctx: LegacyCtx): Promise<unknown> {
  const s = ctx.settings;
  const strip = (v: string) => v.replace(/\\(["'\\])/g, "$1"); // PHP stripslashes
  return {
    app_name: s.appName,
    app_logo: s.appLogo,
    app_version: s.appVersion,
    app_author: s.appAuthor,
    app_contact: s.appContact,
    app_email: s.appEmail,
    app_website: s.appWebsite,
    // 旧库无此列，PHP undefined index 输出 null，逐字复刻
    app_download_url: null,
    app_description: strip(s.appDescription),
    app_developed_by: s.appDevelopedBy,
    app_privacy_policy: strip(s.appPrivacyPolicy),
    package_name: s.packageName,
    publisher_id: s.publisherId,
    interstital_ad: s.interstitalAd,
    interstital_ad_type: s.interstitalAdType,
    interstital_ad_id:
      s.interstitalAdType === "facebook" ? s.interstitalFacebookId : s.interstitalAdId,
    interstital_ad_click: s.interstitalAdClick,
    banner_ad: s.bannerAd,
    banner_ad_type: s.bannerAdType,
    banner_ad_id: s.bannerAdType === "facebook" ? s.bannerFacebookId : s.bannerAdId,
    native_ad: s.nativeAd,
    native_ad_type: s.nativeAdType,
    native_ad_id: s.nativeAdType === "facebook" ? s.nativeFacebookId : s.nativeAdId,
    native_position: S(s.nativePosition),
    song_download: s.songDownload,
    app_update_status: s.appUpdateStatus,
    app_new_version: S(s.appNewVersion),
    app_update_desc: strip(s.appUpdateDesc),
    app_redirect_url: s.appRedirectUrl,
    cancel_update_status: s.cancelUpdateStatus,
  };
}
