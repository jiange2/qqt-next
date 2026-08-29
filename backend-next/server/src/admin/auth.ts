// Admin API 鉴权（Q23）：单 JWT，滑动续期 7 天，Authorization: Bearer <token>
import jwt from "jsonwebtoken";
import type { FastifyReply, FastifyRequest } from "fastify";
import { config } from "../config.js";

export type AdminPayload = { sub: number; username: string };

export function signAdminToken(payload: AdminPayload): string {
  return jwt.sign(payload, config.jwtSecret, { expiresIn: config.jwtExpiresIn });
}

/** fastify preHandler：校验通过后 request.admin 携带管理员信息 */
export async function requireAdmin(req: FastifyRequest, reply: FastifyReply): Promise<void> {
  const header = req.headers.authorization ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!token) {
    await reply.code(401).send({ error: "Unauthorized" });
    return;
  }
  try {
    // verify 顺带处理续期：面板每次请求都换发新 token 由前端存储
    const payload = jwt.verify(token, config.jwtSecret) as unknown as AdminPayload;
    (req as FastifyRequest & { admin: AdminPayload }).admin = payload;
  } catch {
    await reply.code(401).send({ error: "Token expired or invalid" });
  }
}
