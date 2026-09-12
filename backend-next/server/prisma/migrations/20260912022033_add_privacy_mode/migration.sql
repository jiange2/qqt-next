-- AlterTable
ALTER TABLE `albums` ADD COLUMN `is_private` BOOLEAN NOT NULL DEFAULT false;

-- AlterTable
ALTER TABLE `categories` ADD COLUMN `is_private` BOOLEAN NOT NULL DEFAULT false;

-- AlterTable
ALTER TABLE `settings` ADD COLUMN `privacy_mode` VARCHAR(10) NOT NULL DEFAULT 'false';

-- AlterTable
ALTER TABLE `songs` ADD COLUMN `is_private` BOOLEAN NOT NULL DEFAULT false;
