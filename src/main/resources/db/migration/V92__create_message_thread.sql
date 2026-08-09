-- 스레드는 CONNECTION에 1:1로 종속된다 — PAIR/OPERATOR_MARKET/OPERATOR_CREATOR 어느 타입이든
-- 스레드 쪽 로직은 완전히 동일하다(§1-3).
CREATE TABLE `message_thread` (
    `thread_id`             BIGINT NOT NULL AUTO_INCREMENT,
    `connection_id`         BIGINT NOT NULL,
    `status`                VARCHAR(20) NOT NULL COMMENT 'OPEN, DORMANT',
    `last_message_at`       DATETIME(6) NULL,
    `last_message_preview`  VARCHAR(255) NULL,
    `created_at`            DATETIME(6) NOT NULL,
    `modified_at`           DATETIME(6) NOT NULL,
    PRIMARY KEY (`thread_id`),
    UNIQUE KEY `uk_message_thread_connection` (`connection_id`),
    KEY `idx_message_thread_status_last_message` (`status`, `last_message_at`),
    CONSTRAINT `fk_message_thread_connection` FOREIGN KEY (`connection_id`) REFERENCES `connection` (`connection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 스레드별 참가자의 읽음 위치. PAIR 스레드에서 ADMIN은 모니터링 열람이라 이 테이블에 행을 두지 않는다(§13-4).
CREATE TABLE `thread_participant` (
    `id`                    BIGINT NOT NULL AUTO_INCREMENT,
    `thread_id`             BIGINT NOT NULL,
    `participant_type`      VARCHAR(20) NOT NULL COMMENT 'SELLER, CREATOR, ADMIN',
    `participant_id`        BIGINT NOT NULL COMMENT 'SELLER=Market.id, CREATOR=Creator.id, ADMIN=Seller.id(roleType=ADMIN)',
    `last_read_message_id`  BIGINT NULL,
    `last_read_at`          DATETIME(6) NULL,
    `created_at`            DATETIME(6) NOT NULL,
    `modified_at`           DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_thread_participant` (`thread_id`, `participant_type`, `participant_id`),
    CONSTRAINT `fk_thread_participant_thread` FOREIGN KEY (`thread_id`) REFERENCES `message_thread` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
