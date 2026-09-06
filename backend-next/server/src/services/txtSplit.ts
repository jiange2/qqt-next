// 智能分章节（书籍阅读域 ADR 0011）：整本 TXT 按章节标记切分为章节数组，只切分不入库——
// 入库前必须经管理面板预览确认（强确认弹窗列明删 N 写 M）。启发式规则，误切靠预览兜底、
// 入库后可逐章编辑修正。
import iconv from "iconv-lite";

// 中文数字字符集（兼容「零〇两」）；纯阿拉伯数字单行另判
const CN_NUM = "0-9零〇一二三四五六七八九十百千两";

// 章标记行首：第X章/回/节（标题可与标记同行）+ 固定词章。
// 标记后必须跟行尾/空白/标点/数字，避免「第三章写完了」这类正文句误命中；
// 「卷/部/集」不在单位内——书只有章一层，卷行按普通文本并入正文（ADR 0011）
const CHAPTER_MARK = new RegExp(
  `^(第[${CN_NUM}]+[章回节]|序章|楔子|引子|前言|后记|尾声|终章|番外)(\\s|$|[：:、.·\\-—～,，!！?？0-9])`,
);

// 纯数字独行（1–4 位）算章标题（网文常见"123"式编号）
const NUMERIC_LINE = /^\d{1,4}$/;

// 章标题行长度上限：超长视为正文（防长句以「第…」开头被误切）
const MAX_TITLE_LEN = 50;

export type SplitChapter = { title: string; content: string };

/** 编码探测：先按 UTF-8 严格解码，非法序列则回退 GBK（中文 TXT 一半是 GBK，靠约定必翻车） */
export function decodeTxt(buffer: Buffer): string {
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(buffer);
  } catch {
    return iconv.decode(buffer, "gbk");
  }
}

function isChapterTitle(line: string): boolean {
  if (!line || line.length > MAX_TITLE_LEN) return false;
  if (NUMERIC_LINE.test(line)) return true;
  return CHAPTER_MARK.test(line);
}

/**
 * 切分规则（ADR 0011）：
 * - 命中章标记的行整行作章标题（含标题文字），其后各行归入本章；
 * - 识别不出的行并入上一章；书首无归属行并入第一章开头，不单独成章；
 * - 整本无任何标记收作单章「正文」。
 */
export function splitChapters(text: string): SplitChapter[] {
  const lines = text.replace(/\r\n?/g, "\n").split("\n");
  const chapters: { title: string; lines: string[] }[] = [];
  let current: { title: string; lines: string[] } | null = null;
  const head: string[] = []; // 第一章标记之前的无归属行

  for (const raw of lines) {
    const line = raw.trim();
    if (isChapterTitle(line)) {
      if (current) chapters.push(current);
      current = { title: line, lines: [] };
    } else if (current) {
      current.lines.push(raw);
    } else {
      head.push(raw);
    }
  }
  if (current) chapters.push(current);

  if (chapters.length === 0) {
    return [{ title: "正文", content: head.join("\n").trim() }];
  }
  if (head.some((l) => l.trim())) {
    chapters[0].lines = [...head, ...chapters[0].lines];
  }
  return chapters.map((c) => ({ title: c.title, content: c.lines.join("\n").trim() }));
}
