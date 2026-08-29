import "dotenv/config";

function required(name: string, fallback?: string): string {
  const value = process.env[name] ?? fallback;
  if (value === undefined) {
    throw new Error(`Missing required env var: ${name}`);
  }
  return value;
}

export const config = {
  port: Number(process.env.PORT ?? 8000),
  databaseUrl: required("DATABASE_URL"),
  jwtSecret: required("JWT_SECRET", "change-me-to-a-long-random-string"),
  // 阿里云 OSS（ADR 0004）：媒体对象存储，DB 只存 key，URL 由 OSS_PUBLIC_BASE 运行时拼接
  oss: {
    region: required("OSS_REGION"),
    bucket: required("OSS_BUCKET"),
    accessKeyId: required("OSS_ACCESS_KEY_ID"),
    accessKeySecret: required("OSS_ACCESS_KEY_SECRET"),
    // 上传走内网 endpoint（同 region ECS 免流量费，SDK internal 参数自动切 -internal 域名）
    internal: process.env.OSS_INTERNAL === "true",
    // 对外媒体 URL 基地址（公网 endpoint / 自定义域名 / CDN），换 CDN 只改此值
    publicBase: required("OSS_PUBLIC_BASE"),
  },
  jwtExpiresIn: "7d",
  adminCookieName: "admin_token",
} as const;
