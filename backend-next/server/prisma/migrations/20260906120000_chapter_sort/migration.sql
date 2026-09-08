-- 章节顺序（ADR 0011 修订）：Chapter 加 chapter_sort，口径 sort ASC + id ASC 兜底；
-- 存量不回填（全 0 靠 id 兜底），逐章新增追加书末，TXT 导入按切分序赋 0..N-1
-- AlterTable
ALTER TABLE `chapters` ADD COLUMN `chapter_sort` INTEGER NOT NULL DEFAULT 0;

-- CreateIndex
CREATE INDEX `chapters_book_id_chapter_sort_idx` ON `chapters`(`book_id`, `chapter_sort`);
