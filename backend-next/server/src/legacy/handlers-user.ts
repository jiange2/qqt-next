// Legacy 门面 —— 用户类 method（8 个，api.php 逐一对齐）
// 密码一律 bcrypt（ADR 0003 决定 3）；邮件功能全砍（决定 5），注册即生效
import bcrypt from "bcryptjs";
import { prisma } from "../prisma.js";
import { appLang } from "./lang.js";
import { S } from "./shared.js";
import { favouriteSetOf, songAppVisibleFilter, type LegacyCtx } from "./handlers-content.js";
import { songToLegacy } from "./view.js";
import { songIncludeForQuery as songInclude } from "./handlers-content.js";

/** 与 PHP FILTER_VALIDATE_EMAIL 近似的邮箱格式校验 */
function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

async function touchActiveLog(userId: number): Promise<void> {
  await prisma.userActiveLog.upsert({
    where: { userId },
    update: { lastActiveAt: new Date() },
    create: { userId, lastActiveAt: new Date() },
  });
}

// ---------------------------------------------------------------- user_register

export async function userRegister(ctx: LegacyCtx): Promise<unknown> {
  const data = ctx.data;
  const type = (data["type"] ?? "").trim();
  const email = (data["email"] ?? "").trim();
  const authId = (data["auth_id"] ?? "").trim();

  // Google / Facebook 社交注册：email 或 auth_id 命中同类型账户即视为已有账户
  if (/^google$/i.test(type) || /^facebook$/i.test(type)) {
    const userType = /^google$/i.test(type) ? "Google" : "Facebook";
    const existing = await prisma.appUser.findFirst({
      where: { userType, OR: [{ email }, { authId }] },
    });
    if (existing) {
      await prisma.appUser.update({ where: { id: existing.id }, data: { authId } });
      await touchActiveLog(existing.id);
      if (!existing.status) {
        return { msg: appLang.account_deactive, success: "0" };
      }
      return {
        user_id: S(existing.id),
        name: existing.name,
        email: existing.email,
        msg: appLang.login_success,
        auth_id: authId,
        success: "1",
      };
    }
    const password = (data["password"] ?? "").trim();
    const user = await prisma.appUser.create({
      data: {
        userType,
        name: (data["name"] ?? "").trim(),
        email,
        password: password ? await bcrypt.hash(password, 10) : null,
        phone: (data["phone"] ?? "").trim(),
      },
    });
    await touchActiveLog(user.id);
    return {
      user_id: S(user.id),
      name: data["name"] ?? "",
      email: data["email"] ?? "",
      success: "1",
      msg: "",
      auth_id: authId,
    };
  }

  // Normal 注册：邮箱格式 + 重复校验（旧 Normal 分支不维护活跃日志，保持一致）
  if (!isValidEmail(data["email"] ?? "")) {
    return { msg: appLang.invalid_email_format, success: "0" };
  }
  const dup = await prisma.appUser.findFirst({ where: { email }, select: { id: true } });
  if (dup) return { msg: appLang.email_exist, success: "0" };
  await prisma.appUser.create({
    data: {
      userType: "Normal",
      name: (data["name"] ?? "").trim(),
      email,
      password: await bcrypt.hash((data["password"] ?? "").trim(), 10),
      phone: (data["phone"] ?? "").trim(),
    },
  });
  return { msg: appLang.register_success, success: "1" };
}

// ---------------------------------------------------------------- user_login

export async function userLogin(ctx: LegacyCtx): Promise<unknown> {
  const data = ctx.data;
  const email = (data["email"] ?? "").trim();
  const type = (data["type"] ?? "").trim();

  if (/^normal$/i.test(type)) {
    const user = await prisma.appUser.findFirst({ where: { email, userType: "Normal" } });
    if (!user) return { msg: appLang.email_not_found, success: "0" };
    if (!user.status) return { msg: appLang.account_deactive, success: "0" };
    const ok = user.password
      ? await bcrypt.compare((data["password"] ?? "").trim(), user.password)
      : false;
    if (!ok) return { msg: appLang.invalid_password, success: "0" };
    await touchActiveLog(user.id);
    return {
      user_id: S(user.id),
      name: user.name,
      email: user.email,
      msg: appLang.login_success,
      auth_id: "",
      success: "1",
    };
  }

  if (/^google$/i.test(type) || /^facebook$/i.test(type)) {
    const userType = /^google$/i.test(type) ? "Google" : "Facebook";
    const authId = (data["auth_id"] ?? "").trim();
    const user = await prisma.appUser.findFirst({
      where: { userType, OR: [{ email }, { authId }] },
    });
    if (!user) return { msg: appLang.email_not_found, success: "0" };
    if (!user.status) return { msg: appLang.account_deactive, success: "0" };
    await prisma.appUser.update({ where: { id: user.id }, data: { authId } });
    await touchActiveLog(user.id);
    return {
      user_id: S(user.id),
      name: user.name,
      email: user.email,
      msg: appLang.login_success,
      auth_id: authId,
      success: "1",
    };
  }

  // 旧实现引用未定义文案，输出空字符串（见 lang.ts invalid_user_type 注释）
  return { success: "0", msg: appLang.invalid_user_type };
}

// ---------------------------------------------------------------- user_profile

export async function userProfile(ctx: LegacyCtx): Promise<unknown> {
  const userId = Number(ctx.data["user_id"]);
  const user = Number.isFinite(userId)
    ? await prisma.appUser.findUnique({ where: { id: userId } })
    : null;
  return {
    success: "1",
    user_id: S(user?.id),
    name: user?.name ?? "",
    email: user?.email || "",
    phone: user?.phone || "",
  };
}

// ---------------------------------------------------------------- user_profile_update

export async function userProfileUpdate(ctx: LegacyCtx): Promise<unknown> {
  const data = ctx.data;
  const userId = Number(data["user_id"]);
  const email = (data["email"] ?? "").trim();

  if (!isValidEmail(data["email"] ?? "")) {
    return { msg: appLang.invalid_email_format, success: "0" };
  }
  // 旧实现的重复邮箱分支因取行方式永远不触发（已实锤缺陷，按本意修复）
  const dup = await prisma.appUser.findFirst({
    where: { email, id: { not: userId } },
    select: { id: true },
  });
  if (dup) return { msg: appLang.email_exist, success: "0" };

  const password = (data["password"] ?? "").trim();
  const update: { name: string; email: string; phone: string; password?: string } = {
    name: (data["name"] ?? "").trim(),
    email,
    phone: (data["phone"] ?? "").trim(),
  };
  if (password) update.password = await bcrypt.hash(password, 10);
  await prisma.appUser.update({ where: { id: userId }, data: update });
  return { msg: appLang.update_success, success: "1" };
}

// ---------------------------------------------------------------- forgot_pass

/** 邮件功能移除（ADR 0003 决定 5 / Q27）：method 保留，只返回固定提示 */
export async function forgotPass(): Promise<unknown> {
  return { msg: appLang.forgot_pass_disabled, success: "0" };
}

// ---------------------------------------------------------------- favourite_post

/** 旧实现怪癖：添加 success:'1'，移除 success:'0'，移除失败反而是 '1' */
export async function favouritePost(ctx: LegacyCtx): Promise<unknown> {
  const data = ctx.data;
  const postId = Number(data["post_id"]);
  const userId = Number(data["user_id"]);
  const type = data["type"] || "song";

  const existing = await prisma.favourite.findFirst({ where: { postId, userId, type } });
  if (!existing) {
    await prisma.favourite.create({ data: { postId, userId, type } });
    return { msg: appLang.favourite_success, success: "1" };
  }
  try {
    await prisma.favourite.delete({ where: { id: existing.id } });
    return { msg: appLang.favourite_remove_success, success: "0" };
  } catch {
    return { msg: appLang.favourite_remove_error, success: "1" };
  }
}

// ---------------------------------------------------------------- get_favourite_post

export async function getFavouritePost(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const userId = Number(data["user_id"]);
  if (!Number.isFinite(userId)) return [];
  const type = data["type"] || "song";
  const page = Math.max(1, Number.parseInt(data["page"] ?? "1", 10) || 1);

  const favs = await prisma.favourite.findMany({
    where: { userId, type },
    orderBy: { id: "desc" },
    include: { song: { include: songInclude } },
  });
  // 归属链过滤（ADR 0009）：状态链 song + album + category 均启用；
  // 隐私模式额外要求全链非隐私（ADR 0012）；补齐旧实现遗漏的 album.status 检查
  const valid = favs.filter((f) => {
    if (!f.song) return false;
    const s = f.song;
    if (!s.status || !s.album?.status || !s.category?.status) return false;
    if (settings.privacyMode === "true") {
      if (s.isPrivate || s.album?.isPrivate || s.category?.isPrivate) return false;
    }
    return true;
  });
  const favourites = new Set(valid.map((f) => f.postId));
  return valid.slice((page - 1) * 10, page * 10).map((f) => ({
    total_songs: S(valid.length),
    ...songToLegacy(f.song, { base, favourites }),
  }));
}

// ---------------------------------------------------------------- get_recent_songs

export async function getRecentSongs(ctx: LegacyCtx): Promise<unknown> {
  const { base, data, settings } = ctx;
  const ids = (data["songs_ids"] ?? "")
    .split(",")
    .map((s) => Number.parseInt(s.trim(), 10))
    .filter((n) => Number.isFinite(n));
  if (ids.length === 0) return [];
  const page = Math.max(1, Number.parseInt(data["page"] ?? "1", 10) || 1);

  // 归属链过滤（ADR 0009/0012）：用 songAppVisibleFilter 统一处理状态链 + 隐私模式
  const where = { ...songAppVisibleFilter(settings), id: { in: ids } };
  const total = await prisma.song.count({ where });
  const rows = await prisma.song.findMany({
    where,
    orderBy: { id: "desc" },
    take: 10,
    skip: (page - 1) * 10,
    include: songInclude,
  });
  const favourites = await favouriteSetOf(data["user_id"]);
  return rows.map((s) => ({
    total_songs: S(total),
    ...songToLegacy(s, { base, favourites }),
  }));
}
