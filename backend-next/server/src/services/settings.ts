// 设置服务：tbl_settings 单行读取 + 进程内缓存（管理端修改后失效）
import { prisma } from "../prisma.js";
import type { Setting } from "@prisma/client";

let cache: Setting | null = null;

export async function getSettings(): Promise<Setting> {
  cache ??= await prisma.setting.findUnique({ where: { id: 1 } });
  if (!cache) {
    throw new Error("Settings row missing: run prisma migrate + seed first");
  }
  return cache;
}

export function invalidateSettingsCache(): void {
  cache = null;
}

/**
 * 解析设置里的排序配置（如 "id DESC" / "name ASC"）为 Prisma orderBy。
 * 迁移脚本负责把旧列名（cid / mp3_title 等）映射为现代列名。
 */
export function parseOrderBy(
  raw: string,
  allowedFields: Record<string, string>,
): Record<string, "asc" | "desc"> {
  const parts = (raw ?? "").trim().split(/\s+/);
  const field = parts[0] ?? "";
  const column = allowedFields[field] ?? allowedFields["id"];
  const dir: "asc" | "desc" = /desc/i.test(parts[1] ?? "") ? "desc" : "asc";
  return { [column]: dir };
}
