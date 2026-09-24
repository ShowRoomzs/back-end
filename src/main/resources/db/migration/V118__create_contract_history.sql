-- 계약 이력 — append-only. 화면 「이력」 카드가 그대로 읽는다.
-- UPDATE·DELETE 경로를 만들지 않는다(설계서 1-6). 이력이 비면 분쟁에서 근거가 없다는 뜻이다.
CREATE TABLE `contract_history` (
    `contract_history_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `contract_id`         BIGINT       NOT NULL,
    `event_type`          VARCHAR(64)  NOT NULL COMMENT 'CREATED, REVIEW_REQUESTED, REVIEW_REQUEST_CANCELED, REVIEW_APPROVED, REVIEW_REJECTED, SIGNATURE_SENT, BRAND_SIGNED, CREATOR_SIGNED, SIGNATURE_UPDATED, RESEND_REQUESTED, CONCLUDED, DECLINED, EXPIRED, CANCELED, FIXED_FEE_PAID, GROUP_BUY_CREATED',
    `actor_type`          VARCHAR(16)  NOT NULL COMMENT 'SELLER, CREATOR, ADMIN, SYSTEM',
    `actor_id`            BIGINT       NULL,
    -- 서피스와 무관하게 같은 값(브랜드명·쇼룸명)에만 쓴다. 운영자 호칭은 읽는 서피스가 고른다(§25-9).
    `actor_display_name`  VARCHAR(100) NULL COMMENT '스냅샷 — 브랜드명·쇼룸명',
    `detail`              VARCHAR(500) NULL COMMENT '부가 문구',
    `occurred_at`         DATETIME(6)  NOT NULL,
    PRIMARY KEY (`contract_history_id`),
    KEY `idx_contract_history_contract_occurred` (`contract_id`, `occurred_at`),
    CONSTRAINT `fk_contract_history_contract` FOREIGN KEY (`contract_id`)
        REFERENCES `contract` (`contract_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
