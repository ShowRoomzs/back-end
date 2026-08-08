-- §4 S3 Presigned URL 직접 업로드. presign 발급 시점엔 메시지가 아직 없으므로 message_id는 NULL
-- 허용, 메시지 전송 시 조건부 UPDATE로만 연결된다(§4-5).
CREATE TABLE `message_attachment` (
    `attachment_id`    BIGINT NOT NULL AUTO_INCREMENT,
    `message_id`       BIGINT NULL,
    `thread_id`        BIGINT NOT NULL,
    `uploader_type`    VARCHAR(20) NOT NULL COMMENT 'SELLER, CREATOR, ADMIN',
    `uploader_id`      BIGINT NOT NULL,
    `status`           VARCHAR(20) NOT NULL COMMENT 'PENDING, UPLOADED, REJECTED',
    `attachment_type`  VARCHAR(20) NOT NULL COMMENT 'IMAGE, VIDEO, DOCUMENT',
    `s3_key`           VARCHAR(512) NOT NULL,
    `file_url`         VARCHAR(1024) NOT NULL,
    `original_name`    VARCHAR(255) NOT NULL,
    `extension`        VARCHAR(16) NOT NULL,
    `content_type`     VARCHAR(128) NULL,
    `size_bytes`       BIGINT NOT NULL,
    `duration_seconds` INT NULL,
    `sort_order`       INT NULL,
    `created_at`       DATETIME(6) NOT NULL,
    `modified_at`      DATETIME(6) NOT NULL,
    PRIMARY KEY (`attachment_id`),
    KEY `idx_message_attachment_message_sort` (`message_id`, `sort_order`),
    KEY `idx_message_attachment_status_created` (`status`, `created_at`),
    CONSTRAINT `fk_message_attachment_message` FOREIGN KEY (`message_id`) REFERENCES `message` (`message_id`),
    CONSTRAINT `fk_message_attachment_thread` FOREIGN KEY (`thread_id`) REFERENCES `message_thread` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
