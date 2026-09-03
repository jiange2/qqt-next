// 维度顺序功能冒烟（backend-next ADR 0007）：对运行中的服务验证分类/专辑维度端点与门面排序。
// 前置：server 已启动（pnpm dev）；运行：npx tsx scripts/smoke-dimension.ts [--base=http://127.0.0.1:8000]
import bcrypt from "bcryptjs";
import crypto from "node:crypto";
import "dotenv/config";
import { PrismaClient } from "@prisma/client";

const baseArg = process.argv.find((a) => a.startsWith("--base="));
const BASE = baseArg ? baseArg.split("=")[1] : "http://127.0.0.1:8000";
const prisma = new PrismaClient();

// 临时管理员（结束后删除），绕过未知 seed 密码
const uname = `smoke_${Date.now()}`;
await prisma.adminUser.create({ data: { username: uname, password: await bcrypt.hash("smoke-pass", 4) } });
try {
  const login = await fetch(`${BASE}/admin/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ username: uname, password: "smoke-pass" }),
  }).then((r) => r.json() as Promise<{ token?: string }>);
  if (!login.token) throw new Error("login failed");
  const headers = { authorization: `Bearer ${login.token}`, "content-type": "application/json" };

  const cat = await prisma.category.findFirst({ orderBy: { id: "asc" } });
  if (!cat) throw new Error("no category");
  const catSongs = await prisma.song.findMany({
    where: { categoryId: cat.id },
    orderBy: { id: "asc" },
    select: { id: true, title: true },
  });
  console.log(`[${cat.name}] 歌曲数: ${catSongs.length}`);

  // 1) 维度列表：按 category_sort, id
  const list1 = await fetch(`${BASE}/admin/categories/${cat.id}/songs`, { headers }).then((r) => r.json());
  console.log("[GET songs]", list1.items.map((s: { id: number }) => s.id).join(","));

  // 2) 倒序保存 → 列表应反转
  const rev = [...list1.items].reverse().map((s: { id: number }) => s.id);
  await fetch(`${BASE}/admin/categories/${cat.id}/songs/order`, {
    method: "PUT", headers, body: JSON.stringify({ ids: rev }),
  });
  const list2 = await fetch(`${BASE}/admin/categories/${cat.id}/songs`, { headers }).then((r) => r.json());
  const ok = JSON.stringify(list2.items.map((s: { id: number }) => s.id)) === JSON.stringify(rev);
  console.log("[PUT order + GET]", ok ? "顺序生效 ✓" : "顺序未生效 ✗");

  // 3) 未分类歌曲：拿一首歌置空分类 → available 可见 → 认领回原分类（插入最前）→ 验证 sort 为最小
  const victim = catSongs[0];
  await prisma.song.update({ where: { id: victim.id }, data: { categoryId: null, categorySort: 0 } });
  const avail = await fetch(`${BASE}/admin/categories/${cat.id}/songs/available?keyword=`, { headers }).then((r) => r.json());
  console.log("[available] 含被移除歌曲:", avail.items.some((s: { id: number }) => s.id === victim.id) ? "✓" : "✗");
  await fetch(`${BASE}/admin/categories/${cat.id}/songs`, {
    method: "POST", headers, body: JSON.stringify({ ids: [victim.id] }),
  });
  const back = await prisma.song.findUnique({ where: { id: victim.id } });
  const minSort = await prisma.song.aggregate({ where: { categoryId: cat.id }, _min: { categorySort: true } });
  console.log(
    "[claim] 插入最前:",
    back?.categoryId === cat.id && back.categorySort === (minSort._min?.categorySort ?? 0) ? "✓" : "✗",
  );

  // 4) 专辑须有分类校验：未分类歌 + album_id → 400
  await prisma.song.update({ where: { id: victim.id }, data: { categoryId: null } });
  const album = await prisma.album.findFirst();
  if (album) {
    const res = await fetch(`${BASE}/admin/songs/${victim.id}`, {
      method: "PUT",
      headers: { ...headers, "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ title: victim.title, type: "local", category_id: "0", album_id: String(album.id), status: "1" }),
    });
    console.log("[album requires category]", res.status === 400 ? "拦截 ✓" : `未拦截 ✗ (${res.status})`);
  }

  // 5) /admin/songs?category_id=0 只列未分类
  const uncat = await fetch(`${BASE}/admin/songs?category_id=0`, { headers }).then((r) => r.json());
  console.log("[admin songs uncategorized]", uncat.items.some((s: { id: number }) => s.id === victim.id) ? "可见 ✓" : "✗");

  // 6) 恢复 victim 原分类（插入最前），再验证门面 cat_songs 顺序 = 维度顺序
  const maxAgg = await prisma.song.aggregate({ where: { categoryId: cat.id }, _min: { categorySort: true } });
  await prisma.song.update({ where: { id: victim.id }, data: { categoryId: cat.id, categorySort: (maxAgg._min?.categorySort ?? 0) - 1 } });

  const settings = await prisma.setting.findUnique({ where: { id: 1 } });
  const salt = crypto.randomBytes(4).toString("hex");
  const sign = crypto.createHash("md5").update("viaviweb" + salt).digest("hex");
  const encode = (d: unknown) => Buffer.from(encodeURIComponent(JSON.stringify(d)), "utf8").toString("base64");
  const legacy = await fetch(`${BASE}/api.php`, {
    method: "POST",
    body: new URLSearchParams({
      data: encode({ method_name: "cat_songs", package_name: settings?.packageName ?? "", salt, sign, cat_id: String(cat.id) }),
    }),
  }).then((r) => r.json() as Promise<{ ONLINE_MP3?: { id: string }[] }>); // 旧协议根节点 ONLINE_MP3
  const legacyIds = (legacy.ONLINE_MP3 ?? []).map((s) => Number(s.id));
  const dimIds = await prisma.song.findMany({ where: { categoryId: cat.id, status: true }, orderBy: [{ categorySort: "asc" }, { id: "desc" }], select: { id: true } }).then((r) => r.map((s) => s.id));
  console.log("[legacy cat_songs 顺序 = 维度顺序]", JSON.stringify(legacyIds) === JSON.stringify(dimIds) ? "✓" : `✗ legacy=${legacyIds} dim=${dimIds}`);
} finally {
  await prisma.adminUser.delete({ where: { username: uname } }).catch(() => {});
  await prisma.$disconnect();
}
