-- 공구 이력 — append-only. contract_history와 같은 모양이다(설계서 1-4).
-- UPDATE·DELETE 경로를 만들지 않는다. 최소 물량 확인 기록은 제25조 제재 판정의 증거라 이력이 비면 증거가 사라진다.
CREATE TABLE `group_buy_history` (
    `group_buy_history_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `group_buy_id`         BIGINT       NOT NULL,
    `event_type`           VARCHAR(64)  NOT NULL COMMENT '설계서 1-4 event_type 표',
    `actor_type`           VARCHAR(16)  NOT NULL COMMENT 'SELLER, CREATOR, ADMIN, SYSTEM',
    `actor_id`             BIGINT       NULL,
    -- 브랜드명·쇼룸명만. 운영자 호칭은 읽는 서피스가 고른다(§29-13).
    `actor_display_name`   VARCHAR(100) NULL COMMENT '스냅샷 — 브랜드명·쇼룸명',
    `detail`               VARCHAR(500) NULL COMMENT '부가 문구 — 「7일 · 수요 증가」',
    `occurred_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`group_buy_history_id`),
    KEY `idx_group_buy_history_group_buy_occurred` (`group_buy_id`, `occurred_at`),
    CONSTRAINT `fk_group_buy_history_group_buy` FOREIGN KEY (`group_buy_id`)
        REFERENCES `group_buy` (`group_buy_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
