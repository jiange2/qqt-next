// API 客户端：统一携带 JWT，401 时跳登录
import axios from "axios";
import type { Ref } from "vue";
import { ElMessage } from "element-plus";

export const api = axios.create({ baseURL: "/" });

const TOKEN_KEY = "admin_token";

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? "";
}
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/** 解 JWT payload 取用户名，仅用于顶栏展示（不校验签名，签名由服务端负责） */
export function getUsername(): string {
  try {
    const part = getToken().split(".")[1] ?? "";
    const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="))) as {
      username?: string;
    };
    return payload.username ?? "";
  } catch {
    return "";
  }
}

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err.response?.data?.error ?? err.message;
    if (err.response?.status === 401 && location.hash !== "#/login") {
      clearToken();
      location.hash = "#/login";
    } else {
      ElMessage.error(String(msg));
    }
    return Promise.reject(err);
  },
);

/**
 * 构造含文本字段 + 媒体字段的 multipart body。
 * 媒体字段支持三种值：File → 作为文件上传；string → 已绑定 OSS key，作为同名文本字段提交（服务端
 * 以 saved 优先、文本回填兜底）；null/空串 → 忽略（编辑时保留原值）。
 */
export function formBody(fields: Record<string, unknown>, files: Record<string, File | string | null>) {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined && v !== null) fd.append(k, String(v));
  }
  for (const [k, f] of Object.entries(files)) {
    if (f instanceof File) fd.append(k, f);
    else if (typeof f === "string" && f) fd.append(k, f);
  }
  return fd;
}

/** multipart 保存请求：携带上传进度到 pct（0-100，完成后归零），id > 0 走 PUT */
export async function saveForm(
  url: string,
  body: FormData,
  id?: number,
  pct?: Ref<number>,
): Promise<void> {
  const onUploadProgress = (e: { loaded: number; total?: number }) => {
    if (pct) pct.value = e.total ? Math.round((e.loaded / e.total) * 100) : 0;
  };
  if (id) await api.put(`${url}/${id}`, body, { onUploadProgress });
  else await api.post(url, body, { onUploadProgress });
  if (pct) pct.value = 0;
}
