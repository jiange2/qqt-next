// API 客户端：统一携带 JWT，401 时跳登录
import axios from "axios";
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

/** 构造含文本字段 + 可选文件的 multipart body */
export function formBody(fields: Record<string, unknown>, files: Record<string, File | null>) {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined && v !== null) fd.append(k, String(v));
  }
  for (const [k, f] of Object.entries(files)) {
    if (f) fd.append(k, f);
  }
  return fd;
}
