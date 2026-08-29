// 旧协议数值字符串化（mysqli 全字符串语义），view.ts 与各 handler 共用
export const S = (v: number | string | null | undefined) => String(v ?? "");
