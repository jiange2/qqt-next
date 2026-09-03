-- DropForeignKey
ALTER TABLE `songs` DROP FOREIGN KEY `songs_category_id_fkey`;

-- AlterTable
ALTER TABLE `songs` ADD COLUMN `album_sort` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `category_sort` INTEGER NOT NULL DEFAULT 0,
    MODIFY `category_id` INTEGER NULL;

-- CreateIndex
CREATE INDEX `songs_category_id_category_sort_idx` ON `songs`(`category_id`, `category_sort`);

-- CreateIndex
CREATE INDEX `songs_album_id_album_sort_idx` ON `songs`(`album_id`, `album_sort`);

-- AddForeignKey
-- 保持 RESTRICT：删除仍有歌曲的分类继续被阻止（Prisma 因关系变可选自动改成了 SET NULL，此处纠正）
ALTER TABLE `songs` ADD CONSTRAINT `songs_category_id_fkey` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
