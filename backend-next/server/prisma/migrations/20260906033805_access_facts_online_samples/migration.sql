-- DropForeignKey
ALTER TABLE `songs` DROP FOREIGN KEY `songs_category_id_fkey`;

-- AlterTable
ALTER TABLE `songs` ADD COLUMN `duration` INTEGER NOT NULL DEFAULT 0;

-- CreateTable
CREATE TABLE `access_facts` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `song_id` INTEGER NOT NULL,
    `device_id` VARCHAR(50) NOT NULL,
    `cache_hit` BOOLEAN NOT NULL DEFAULT false,
    `allocated_storage` BIGINT NULL,
    `used_storage` BIGINT NULL,
    `ip_address` VARCHAR(45) NOT NULL,
    `user_agent` TEXT NULL,
    `accessed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `access_facts_device_id_accessed_at_idx`(`device_id`, `accessed_at`),
    INDEX `access_facts_accessed_at_idx`(`accessed_at`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `online_samples` (
    `sampled_at` DATETIME(3) NOT NULL,
    `total_online` INTEGER NOT NULL,

    PRIMARY KEY (`sampled_at`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `songs` ADD CONSTRAINT `songs_category_id_fkey` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
