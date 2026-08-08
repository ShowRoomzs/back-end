-- §13-10 재전송 멱등 — client_message_id는 FE가 발급한 UUID. thread_id+client_message_id 유니크로
-- 재전송이 같은 키로 재요청되면 신규 저장 대신 기존 행을 그대로 반환한다.
CREATE TABLE `message` (
    `message_id`         BIGINT NOT NULL AUTO_INCREMENT,
    `thread_id`           BIGINT NOT NULL,
    `sender_type`         VARCHAR(20) NOT NULL COMMENT 'SELLER, CREATOR, ADMIN',
    `sender_id`           BIGINT NOT NULL,
    `content`             TEXT NULL COMMENT '첨부만 전송 시 NULL(§13-11, P3에서 첨부 연결)',
    `client_message_id`   VARCHAR(64) NOT NULL,
    `created_at`          DATETIME(6) NOT NULL,
    `modified_at`         DATETIME(6) NOT NULL,
    PRIMARY KEY (`message_id`),
    UNIQUE KEY `uk_message_thread_client_id` (`thread_id`, `client_message_id`),
    KEY `idx_message_thread_id_desc` (`thread_id`, `message_id`),
    CONSTRAINT `fk_message_thread` FOREIGN KEY (`thread_id`) REFERENCES `message_thread` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
