// 智能分章节（书籍阅读域 ADR 0011，切分规则 v2）：整本 TXT 按章节标记切分为章节数组，只切分
// 不入库——入库前必须经管理面板预览确认（强确认弹窗列明删 N 写 M，预览可改标题、并章）。
// 规则按馆藏 7 本真实书籍核查扩充（原规则 6 本塌成单章、灵花切出 40 个空章）：启发式 + 预览兜底。
import iconv from "iconv-lite";

// 中文数字字符集：补 廿/卅（公教读物「第廿章」）与大写数字（默想约伯记「壹、」）
const CN_NUM = "0-9零〇一二三四五六七八九十百千两廿卅壹贰貳叁參肆伍陆柒捌玖拾";
const CN_NUM_U = CN_NUM.replace("0-9", "");

// 标记族一「第X<单位>」：单位补 篇/辑/讲/函/首/话/谈，二字后缀 次谈话/封信（与神同在
// 「第一次谈话」「第二封信」）。标记后必须跟行尾/空白/标点/数字/括号，避免「第三章写完了」
// 这类正文句误命中；「卷/部/集」不在单位内——书只有章一层，卷行按普通文本并入正文（ADR 0011）
const CHAPTER_MARK = new RegExp(
  `^(第[${CN_NUM}]+(次谈话|封信|章|节|回|篇|辑|函|首|话|封|讲|谈)|序章|序言|楔子|引子|引言|前言|后记|尾声|终章|番外|目录|译者序|译序|新版说明)(\\s|$|[：:、.。·\\-—～,，!！?？0-9（(])`,
);

// 标记族二「前置量词」：前进（芬乃伦）「函一　谦卑的益处」
const PREFIX_MARK = new RegExp(`^[函书][${CN_NUM_U}]+(\\s|$|[：:、.。·\\-—～,，!！?？])`);

// 标记族三「数字+标点+短标题」：耶稣受难记「001→试探」、小德兰诗集「1、我今日歌唱」
const NUM_PUNCT_MARK = /^\d{1,4}[、.．:：→]\s*\S.{0,46}$/;

// 纯数字独行（1–4 位）算章标题（网文常见"123"式编号）
const NUMERIC_LINE = /^\d{1,4}$/;

// 标记族四「中文数字+顿号」：默想约伯记「壹、罪與己」（仅单字数字，不含百/千/两）
const CN_NUM_PUNCT_MARK = /^[一二三四五六七八九十壹贰貳叁參肆伍陆柒捌玖拾][、.]\s*\S.{0,46}$/;

// 章标题行长度上限：超长视为正文（防长句以「第…」开头被误切）
const MAX_TITLE_LEN = 50;

// 超短章阈值：正文不足此数视为页眉/重复标题残渣，并入上一章（诗集重复「译者序」实测）；
// 首章无上一章保留，admin-web 预览高亮阈值与此对齐
const TINY_CHAPTER_LEN = 30;

// 目录连排阈值：连续命中标记的行数达此值即整段视为目录块并入正文，不当章边界
const TOC_RUN_LEN = 3;

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
  return (
    CHAPTER_MARK.test(line) ||
    PREFIX_MARK.test(line) ||
    NUM_PUNCT_MARK.test(line) ||
    NUMERIC_LINE.test(line) ||
    CN_NUM_PUNCT_MARK.test(line)
  );
}

/**
 * 切分规则（ADR 0011 v2）：
 * - 命中章标记的行整行作章标题（含标题文字），其后各行归入本章；
 * - 目录连排（TOC_RUN_LEN 行连续命中标记）整段并入正文，不当章边界；连排判定不容忍中间
 *   空行——「前进」目录后紧邻正文「函一」，容忍空行会把正文首章吞进目录块（实测教训）；
 * - 识别不出的行并入上一章；书首无归属行并入第一章开头，不单独成章；
 * - 超短章（正文 < TINY_CHAPTER_LEN）并入上一章，标题行随内容并入；首章无上一章保留；
 * - 整本无任何标记收作单章「正文」。
 */
export function splitChapters(text: string): SplitChapter[] {
  const lines = text.replace(/\r\n?/g, "\n").split("\n");
  const chapters: { title: string; lines: string[] }[] = [];
  let current: { title: string; lines: string[] } | null = null;
  const head: string[] = []; // 第一章标记之前的无归属行
  const push = (raw: string) => (current ? current.lines.push(raw) : head.push(raw));

  for (let i = 0; i < lines.length; ) {
    const line = lines[i].trim();
    if (!isChapterTitle(line)) {
      push(lines[i++]);
      continue;
    }
    // 目录连排探测：只数连续命中行（空行即断）
    let j = i;
    while (j < lines.length && isChapterTitle(lines[j].trim())) j++;
    if (j - i >= TOC_RUN_LEN) {
      for (; i < j; i++) push(lines[i]);
      continue;
    }
    if (current) chapters.push(current);
    current = { title: line, lines: [] };
    i++;
  }
  if (current) chapters.push(current);

  if (chapters.length === 0) {
    return [{ title: "正文", content: head.join("\n").trim() }];
  }
  if (head.some((l) => l.trim())) {
    chapters[0].lines = [...head, ...chapters[0].lines];
  }
  const merged: typeof chapters = [];
  for (const c of chapters) {
    if (merged.length > 0 && c.lines.join("\n").trim().length < TINY_CHAPTER_LEN) {
      merged[merged.length - 1].lines.push(c.title, ...c.lines);
    } else {
      merged.push(c);
    }
  }
  return merged.map((c) => ({ title: c.title, content: c.lines.join("\n").trim() }));
}
