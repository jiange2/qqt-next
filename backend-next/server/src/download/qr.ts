// 下载二维码（仓库级 ADR 0013）：令牌生成/校验 + 扫码落点页模板。
// 不是 App 接口——不经遗留协议门面，不适用 ADR 0001 的签名要求。
import { randomBytes, timingSafeEqual } from "node:crypto";
import type { Setting } from "@prisma/client";

// 令牌格式守卫：base64url 字符集 + 长度区间；兼作落点页模板的注入防线（渲染前必须通过）
const TOKEN_RE = /^[A-Za-z0-9_-]{16,64}$/;

/** 新令牌：16 字节随机 → 22 字符 base64url */
export function newQrToken(): string {
  return randomBytes(16).toString("base64url");
}

/** 令牌等于当前有效值且未到期才算有效；令牌或到期时刻任一为空即无效 */
export function qrTokenValid(
  s: Pick<Setting, "downloadQrToken" | "downloadQrExpiresAt">,
  token: string,
): boolean {
  const current = s.downloadQrToken ?? "";
  if (!TOKEN_RE.test(token) || token.length !== current.length) return false;
  const expiresAt = s.downloadQrExpiresAt?.getTime() ?? 0;
  if (expiresAt <= Date.now()) return false;
  return timingSafeEqual(Buffer.from(token), Buffer.from(current));
}

/**
 * 扫码落点页（单文件 HTML，进页按 UA 分流）：
 * - 非微信：直接 replace 到下载跳转（/apk 再校验一次令牌后 302 到安装包地址）
 * - 微信：展示引导浮层（右上角 ··· 在浏览器打开），浮层内附「复制下载链接」
 * 版本号渲染时内联（本页动态生成，无需再取 app-meta）。
 */
export function renderQrPage(token: string, version: number): string {
  if (!TOKEN_RE.test(token)) throw new Error("invalid qr token");
  const apkPath = `/download/q/${token}/apk`;
  const ver = Number.isFinite(version) && version > 0 ? ` · v${version}` : "";
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="robots" content="noindex,nofollow">
<title>倾轻听 · Android 版下载</title>
<link rel="icon" href="http://audio2-1252918564.file.myqcloud.com/download/icon.png">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  html, body { height: 100%; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
    background: #fff;
    color: #17181a;
    display: flex;
    flex-direction: column;
    align-items: center;
    min-height: 100dvh;
    padding: 24px 20px calc(20px + env(safe-area-inset-bottom));
    -webkit-tap-highlight-color: transparent;
  }
  main {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
  }
  .logo { width: 96px; height: 96px; border-radius: 50%; display: block; }
  h1 { margin-top: 18px; font-size: 24px; font-weight: 700; letter-spacing: .5px; }
  .meta { margin-top: 8px; font-size: 14px; color: #8a8f99; }
  .btn {
    margin-top: 36px;
    display: block;
    width: min(320px, 78vw);
    padding: 15px 0;
    background: #ff5a2d;
    color: #fff;
    font-size: 17px;
    font-weight: 600;
    text-align: center;
    text-decoration: none;
    border-radius: 999px;
    box-shadow: 0 8px 20px rgba(255, 90, 45, .28);
    transition: transform .12s ease, background .12s ease;
  }
  .btn:active { transform: scale(.97); background: #e8431b; }
  footer { font-size: 12px; color: #b3b7be; text-align: center; line-height: 1.8; }

  /* 微信内置浏览器引导浮层：进页即展示，浮层内提供复制链接 */
  #wx-guide { position: fixed; inset: 0; z-index: 99; background: rgba(0, 0, 0, .65); display: none; }
  #wx-guide.show { display: flex; flex-direction: column; align-items: center; }
  #wx-guide .tip {
    margin-top: 22vh;
    padding: 0 32px;
    color: #fff;
    font-size: 17px;
    font-weight: 600;
    line-height: 1.7;
    text-align: center;
    text-shadow: 0 1px 3px rgba(0, 0, 0, .3);
  }
  #wx-guide .arrow { position: absolute; top: 1px; right: 4px; width: 150px; height: 150px; }
  #copy {
    margin-top: 28px;
    padding: 11px 34px;
    background: rgba(255, 255, 255, .14);
    border: 1px solid rgba(255, 255, 255, .6);
    border-radius: 999px;
    color: #fff;
    font-size: 15px;
  }
  #copy:active { background: rgba(255, 255, 255, .28); }
</style>
</head>
<body>
<main>
  <img class="logo" src="http://audio2-1252918564.file.myqcloud.com/download/icon.png" alt="倾轻听" width="96" height="96">
  <h1>倾轻听</h1>
  <p class="meta">Android 版${ver}</p>
  <a class="btn" id="dl" href="${apkPath}">下载 Android 版</a>
</main>
<footer>© 2026 倾轻听</footer>

<div id="wx-guide">
  <svg class="arrow" viewBox="0 0 150 150" fill="none" aria-hidden="true">
    <path d="M14 146 C 38 76 80 34 134 18" stroke="#fff" stroke-width="5" stroke-linecap="round"/>
    <path d="M134 18 L111 40 M134 18 L104 14" stroke="#fff" stroke-width="5" stroke-linecap="round"/>
  </svg>
  <p class="tip">点击右上角 ··· 选择“在浏览器打开”</p>
  <button id="copy" type="button">复制下载链接</button>
</div>

<script>
(function () {
  var isWeChat = /MicroMessenger/i.test(navigator.userAgent || "");
  var guide = document.getElementById("wx-guide");
  var btn = document.getElementById("dl");

  if (isWeChat) {
    guide.classList.add("show");
  } else {
    // pageshow persisted 屏障：从往返缓存返回（用户按了后退）时不再跳走
    window.addEventListener("pageshow", function (e) {
      if (!e.persisted) location.replace(btn.href);
    });
  }

  btn.addEventListener("click", function (e) {
    if (isWeChat) {
      e.preventDefault();
      guide.classList.add("show");
    }
  });
  guide.addEventListener("click", function () {
    guide.classList.remove("show");
  });

  // 页面为明文 HTTP，navigator.clipboard 不可用，走 execCommand 兼容路径
  document.getElementById("copy").addEventListener("click", function (e) {
    e.stopPropagation();
    var ta = document.createElement("textarea");
    ta.value = location.href;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    var ok = false;
    try { ok = document.execCommand("copy"); } catch (err) {}
    document.body.removeChild(ta);
    this.textContent = ok ? "已复制，去浏览器粘贴打开" : "复制失败，请点击右上角在浏览器打开";
  });
})();
</script>
</body>
</html>`;
}
