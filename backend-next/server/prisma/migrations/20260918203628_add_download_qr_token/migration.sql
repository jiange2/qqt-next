-- AlterTable
ALTER TABLE `settings` ADD COLUMN `download_qr_expires_at` DATETIME(3) NULL,
    ADD COLUMN `download_qr_token` VARCHAR(64) NULL;
