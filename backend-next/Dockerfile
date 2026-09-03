# backend-next 全栈镜像：server（Fastify + Prisma）+ admin-web 构建产物
# 构建上下文必须是 backend-next/ 目录：
#   docker build -t qqt-next -f backend-next/Dockerfile backend-next
FROM node:22-alpine AS build
WORKDIR /app
# corepack 下载 pnpm 本体默认走 registry.npmjs.org（国外），指向 npmmirror
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
RUN corepack enable

# 国内网络可在构建时注入镜像源：--build-arg NPM_REGISTRY=https://registry.npmmirror.com
ARG NPM_REGISTRY=https://registry.npmjs.org
RUN npm config set registry ${NPM_REGISTRY}
# Prisma 引擎二进制默认从 binaries.prisma.sh 下载（国外），换 npmmirror 免 VPN
ENV PRISMA_ENGINES_MIRROR=https://registry.npmmirror.com/-/binary/prisma

# 先拷贝清单文件，利用 Docker 层缓存
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json .npmrc ./
COPY server/package.json server/
COPY admin-web/package.json admin-web/
RUN pnpm install --frozen-lockfile

# 再拷贝源码并构建
COPY server/ server/
COPY admin-web/ admin-web/
RUN pnpm --filter admin-web build \
 && pnpm --filter server exec prisma generate \
 && pnpm --filter server build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
# apk 默认源在国外，换阿里镜像源（用 http 绕开容器内 https 握手问题，包本身有签名校验）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g; s/https/http/g' /etc/apk/repositories \
 && apk add --no-cache curl
COPY --from=build /app ./
ENV ADMIN_DIST=/app/admin-web/dist
EXPOSE 8000
# 启动前先确保数据库 schema 就绪（幂等）
CMD ["sh", "-c", "cd server && npx prisma migrate deploy && node dist/src/server.js"]
