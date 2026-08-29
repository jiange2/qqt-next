// 旧协议编解码 + 验签（逐字复刻 includes/function.php 的 checkSignSalt）
// 协议：POST body 形如 data=base64(urlencode(json))，sign = md5("viaviweb" + salt)
import crypto from "node:crypto";
import type { FastifyReply } from "fastify";
import { prisma } from "../prisma.js";

const SIGN_KEY = "viaviweb";

export type LegacyData = Record<string, string>;

/** base64(urlencode(json)) → 对象 */
export function decodeData(raw: string): LegacyData | null {
  try {
    const json = decodeURIComponent(Buffer.from(raw, "base64").toString("utf8"));
    const parsed = JSON.parse(json);
    return typeof parsed === "object" && parsed !== null ? parsed : null;
  } catch {
    return null;
  }
}

export function signOf(salt: string): string {
  return crypto.createHash("md5").update(SIGN_KEY + salt).digest("hex");
}

/** 验签成功返回数据对象；失败时直接写出错误响应并返回 null */
export async function checkSignSalt(
  raw: string | undefined,
  reply: FastifyReply,
): Promise<LegacyData | null> {
  const fail = (success: number, msg: string) => {
    reply.send({ ONLINE_MP3: [{ success, msg }] });
    return null;
  };

  const data = raw ? decodeData(raw) : null;
  if (!data) return fail(-1, "Invalid sign salt.");

  const setting = await getPackageName();
  if (data["package_name"] !== setting) {
    return fail(-1, "Invalid package name");
  }

  const sign = data["sign"] ?? "";
  const salt = data["salt"] ?? "";
  if (sign === "" && salt === "") return fail(-1, "Invalid sign salt.");
  if (sign !== signOf(salt)) return fail(-1, "Invalid sign salt.");

  // 旧实现：携带 user_id 时校验账号有效性，并刷新活跃日志
  const userIdRaw = data["user_id"];
  if (userIdRaw !== undefined && userIdRaw !== "" && userIdRaw !== "0") {
    const userId = Number(userIdRaw);
    if (Number.isFinite(userId) && userId > 0) {
      const user = await prisma.appUser.findFirst({
        where: { id: userId, status: true },
        select: { id: true },
      });
      if (!user) {
        // 注意：旧响应文案即为此拼写（deactived），逐字保留
        return fail(-2, "Your account is deactived or deleted by admin !");
      }
      await prisma.userActiveLog.upsert({
        where: { userId },
        update: { lastActiveAt: new Date() },
        create: { userId, lastActiveAt: new Date() },
      });
    }
  }

  return data;
}

let cachedPackageName: string | null = null;
export function invalidatePackageNameCache(): void {
  cachedPackageName = null;
}
async function getPackageName(): Promise<string> {
  cachedPackageName ??= (
    await prisma.setting.findUnique({ where: { id: 1 }, select: { packageName: true } })
  )?.packageName ?? "";
  return cachedPackageName;
}
