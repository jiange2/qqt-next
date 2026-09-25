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
# 分层拷贝替代整目录 COPY /app：每层独立按 digest 推送，内容未变的层 push 时 already exists 跳过。
# 1) 稳定大层：依赖。刻意保留 devDeps——CMD 的 npx prisma migrate deploy 需要 prisma CLI，
#    运维脚本（tsx scripts/*.ts）需要 tsx；lockfile 不变则本层内容不变，日常发版不重推。
#    admin-web/node_modules 不拷：运行期只服务其 dist，不执行前端工程。
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/server/node_modules ./server/node_modules

# 2) 小层：包清单 / prisma schema / 运维脚本与源码 / 下载页静态资源
#    （public/download 被 app.ts 运行时引用，随镜像发布，不可遗漏）
COPY --from=build /app/package.json /app/pnpm-workspace.yaml /app/.npmrc ./
COPY --from=build /app/server/package.json /app/server/tsconfig.json /app/server/query.sql /app/server/.env.example ./server/
COPY --from=build /app/admin-web/package.json ./admin-web/
COPY --from=build /app/server/prisma ./server/prisma
COPY --from=build /app/server/src ./server/src
COPY --from=build /app/server/scripts ./server/scripts
COPY --from=build /app/server/public ./server/public

# 3) 每次发版必变的小层：构建产物（改代码只重推这几 MB）
COPY --from=build /app/server/dist ./server/dist
COPY --from=build /app/admin-web/dist ./admin-web/dist

ENV ADMIN_DIST=/app/admin-web/dist
EXPOSE 8000
# 启动前先确保数据库 schema 就绪（幂等）
CMD ["sh", "-c", "cd server && npx prisma migrate deploy && node dist/src/server.js"]
