// 空库引导脚本：仅创建初始管理员（Q4=b 决策：先空库上线验证部署链路，迁移另约窗口）
// 逻辑与 migrate.ts 的 seedAdmin() 完全一致（幂等：已存在管理员时跳过）
//
// 使用方式：
//   本地：pnpm seed:admin
//   容器：docker compose -f docker-compose.next.yml run --rm app \
//           sh -c "cd server && npx tsx scripts/seed-admin.ts"
import "dotenv/config";
import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import { PrismaClient } from "@prisma/client";

const prisma = new PrismaClient();

try {
  const count = await prisma.adminUser.count();
  if (count > 0) {
    console.log("admin: 已存在管理员，跳过");
  } else {
    const password = crypto.randomBytes(12).toString("base64url");
    await prisma.adminUser.create({
      data: { username: "admin", password: await bcrypt.hash(password, 10) },
    });
    console.log("admin: 初始管理员已创建 → 用户名 admin，密码（仅此一次打印）: " + password);
  }
} finally {
  await prisma.$disconnect();
}
