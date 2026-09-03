-- DropForeignKey
ALTER TABLE `songs` DROP FOREIGN KEY `songs_category_id_fkey`;

-- AlterTable
ALTER TABLE `albums` ADD COLUMN `category_id` INTEGER NULL,
    ADD COLUMN `category_sort` INTEGER NOT NULL DEFAULT 0;

-- CreateIndex
CREATE INDEX `albums_category_id_idx` ON `albums`(`category_id`);

-- CreateIndex
CREATE INDEX `albums_category_id_category_sort_idx` ON `albums`(`category_id`, `category_sort`);

-- AddForeignKey
ALTER TABLE `albums` ADD CONSTRAINT `albums_category_id_fkey` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
-- 歌曲分类 FK 保持 RESTRICT（ADR 0007 先例）：过渡期存量歌曲仍挂 categoryId，删除分类禁止静默置空； albums 的 SET NULL 符合可空归属语义（应用层已阻止删除有专辑的分类）
ALTER TABLE `songs` ADD CONSTRAINT `songs_category_id_fkey` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;
