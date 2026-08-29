-- CreateTable
CREATE TABLE `settings` (
    `id` INTEGER NOT NULL DEFAULT 1,
    `package_name` VARCHAR(255) NOT NULL,
    `onesignal_app_id` VARCHAR(500) NOT NULL,
    `onesignal_rest_key` VARCHAR(500) NOT NULL,
    `app_name` VARCHAR(255) NOT NULL,
    `app_logo` VARCHAR(255) NOT NULL,
    `app_email` VARCHAR(255) NOT NULL,
    `app_version` VARCHAR(255) NOT NULL,
    `app_author` VARCHAR(255) NOT NULL,
    `app_contact` VARCHAR(255) NOT NULL,
    `app_website` VARCHAR(255) NOT NULL,
    `app_description` TEXT NOT NULL,
    `app_developed_by` VARCHAR(255) NOT NULL,
    `app_privacy_policy` TEXT NOT NULL,
    `api_latest_limit` INTEGER NOT NULL,
    `api_cat_order_by` VARCHAR(255) NOT NULL,
    `api_cat_post_order_by` VARCHAR(255) NOT NULL,
    `publisher_id` VARCHAR(500) NOT NULL,
    `interstital_ad` VARCHAR(500) NOT NULL,
    `interstital_ad_id` VARCHAR(500) NOT NULL,
    `interstital_ad_click` VARCHAR(500) NOT NULL,
    `banner_ad` VARCHAR(500) NOT NULL,
    `banner_ad_id` VARCHAR(500) NOT NULL,
    `banner_ad_type` VARCHAR(30) NOT NULL DEFAULT 'admob',
    `banner_facebook_id` TEXT NOT NULL,
    `interstital_ad_type` VARCHAR(30) NOT NULL DEFAULT 'admob',
    `interstital_facebook_id` TEXT NOT NULL,
    `native_ad` VARCHAR(20) NOT NULL DEFAULT 'false',
    `native_ad_type` VARCHAR(30) NOT NULL DEFAULT 'admob',
    `native_ad_id` TEXT NOT NULL,
    `native_facebook_id` TEXT NOT NULL,
    `native_position` INTEGER NOT NULL DEFAULT 5,
    `app_update_status` VARCHAR(10) NOT NULL DEFAULT 'false',
    `app_new_version` DOUBLE NOT NULL DEFAULT 1,
    `app_update_desc` TEXT NOT NULL,
    `app_redirect_url` TEXT NOT NULL,
    `cancel_update_status` VARCHAR(10) NOT NULL DEFAULT 'false',
    `song_download` VARCHAR(255) NOT NULL DEFAULT 'true',

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `categories` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `image` VARCHAR(255) NOT NULL,
    `status` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `artists` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `image` VARCHAR(255) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `albums` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `image` VARCHAR(255) NOT NULL,
    `status` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `songs` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `category_id` INTEGER NOT NULL,
    `album_id` INTEGER NULL,
    `type` VARCHAR(20) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `audio_url` VARCHAR(500) NOT NULL,
    `thumbnail` VARCHAR(255) NOT NULL,
    `description` TEXT NOT NULL,
    `lrc_text` TEXT NULL,
    `lrc_url` VARCHAR(255) NULL,
    `total_views` INTEGER NOT NULL DEFAULT 0,
    `total_download` INTEGER NOT NULL DEFAULT 0,
    `total_rate` INTEGER NOT NULL DEFAULT 0,
    `rate_avg` INTEGER NOT NULL DEFAULT 0,
    `status` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `songs_category_id_idx`(`category_id`),
    INDEX `songs_album_id_idx`(`album_id`),
    INDEX `songs_status_title_idx`(`status`, `title`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `song_artists` (
    `song_id` INTEGER NOT NULL,
    `artist_id` INTEGER NOT NULL,
    `sort` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`song_id`, `artist_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `album_artists` (
    `album_id` INTEGER NOT NULL,
    `artist_id` INTEGER NOT NULL,
    `sort` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`album_id`, `artist_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `banners` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL,
    `sort_info` VARCHAR(500) NOT NULL,
    `image` VARCHAR(255) NOT NULL,
    `link` VARCHAR(500) NULL,
    `status` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `banner_songs` (
    `banner_id` INTEGER NOT NULL,
    `song_id` INTEGER NOT NULL,
    `sort` INTEGER NOT NULL DEFAULT 0,

    INDEX `banner_songs_banner_id_sort_idx`(`banner_id`, `sort`),
    PRIMARY KEY (`banner_id`, `song_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `playlists` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(255) NOT NULL,
    `image` VARCHAR(255) NOT NULL,
    `status` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `playlist_songs` (
    `playlist_id` INTEGER NOT NULL,
    `song_id` INTEGER NOT NULL,
    `sort` INTEGER NOT NULL DEFAULT 0,

    INDEX `playlist_songs_playlist_id_sort_idx`(`playlist_id`, `sort`),
    PRIMARY KEY (`playlist_id`, `song_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `trending_songs` (
    `song_id` INTEGER NOT NULL,
    `sort` INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (`song_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `app_users` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `user_type` VARCHAR(20) NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `password` VARCHAR(255) NULL,
    `phone` VARCHAR(50) NULL,
    `auth_id` VARCHAR(255) NULL,
    `verified` BOOLEAN NOT NULL DEFAULT true,
    `status` BOOLEAN NOT NULL DEFAULT true,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `app_users_email_idx`(`email`),
    INDEX `app_users_auth_id_idx`(`auth_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `favourites` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `user_id` INTEGER NOT NULL,
    `post_id` INTEGER NOT NULL,
    `type` VARCHAR(20) NOT NULL DEFAULT 'song',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `favourites_user_id_post_id_type_key`(`user_id`, `post_id`, `type`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `ratings` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `post_id` INTEGER NOT NULL,
    `user_id` INTEGER NOT NULL,
    `rate` INTEGER NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `ratings_post_id_user_id_key`(`post_id`, `user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `reports` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `user_id` INTEGER NOT NULL,
    `song_id` INTEGER NOT NULL,
    `report` TEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `song_suggestions` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `user_id` INTEGER NOT NULL,
    `song_title` VARCHAR(255) NOT NULL,
    `song_image` VARCHAR(255) NULL,
    `message` TEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `user_active_logs` (
    `user_id` INTEGER NOT NULL,
    `last_active_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`user_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `admin_users` (
    `id` INTEGER NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(100) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `email` VARCHAR(255) NULL,
    `image` VARCHAR(255) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `admin_users_username_key`(`username`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `songs` ADD CONSTRAINT `songs_category_id_fkey` FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `songs` ADD CONSTRAINT `songs_album_id_fkey` FOREIGN KEY (`album_id`) REFERENCES `albums`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `song_artists` ADD CONSTRAINT `song_artists_song_id_fkey` FOREIGN KEY (`song_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `song_artists` ADD CONSTRAINT `song_artists_artist_id_fkey` FOREIGN KEY (`artist_id`) REFERENCES `artists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `album_artists` ADD CONSTRAINT `album_artists_album_id_fkey` FOREIGN KEY (`album_id`) REFERENCES `albums`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `album_artists` ADD CONSTRAINT `album_artists_artist_id_fkey` FOREIGN KEY (`artist_id`) REFERENCES `artists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `banner_songs` ADD CONSTRAINT `banner_songs_banner_id_fkey` FOREIGN KEY (`banner_id`) REFERENCES `banners`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `banner_songs` ADD CONSTRAINT `banner_songs_song_id_fkey` FOREIGN KEY (`song_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `playlist_songs` ADD CONSTRAINT `playlist_songs_playlist_id_fkey` FOREIGN KEY (`playlist_id`) REFERENCES `playlists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `playlist_songs` ADD CONSTRAINT `playlist_songs_song_id_fkey` FOREIGN KEY (`song_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `trending_songs` ADD CONSTRAINT `trending_songs_song_id_fkey` FOREIGN KEY (`song_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `favourites` ADD CONSTRAINT `favourites_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `app_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `favourites` ADD CONSTRAINT `favourites_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `ratings` ADD CONSTRAINT `ratings_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `ratings` ADD CONSTRAINT `ratings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `app_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `reports` ADD CONSTRAINT `reports_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `app_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `reports` ADD CONSTRAINT `reports_song_id_fkey` FOREIGN KEY (`song_id`) REFERENCES `songs`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `song_suggestions` ADD CONSTRAINT `song_suggestions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `app_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `user_active_logs` ADD CONSTRAINT `user_active_logs_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `app_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
