-- 하위주문 처리 이력 — append-only(34 설계서 1-4). 상세 모달 「처리 이력」과 송장 수정 이력의 원본.
CREATE TABLE `order_fulfillment_history` (
    `fulfillment_history_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `delivery_group_id`      BIGINT       NOT NULL,
    `event_type`             VARCHAR(64)  NOT NULL,
    `actor_type`             VARCHAR(16)  NOT NULL COMMENT 'SYSTEM, SELLER, ADMIN, CONSUMER, TRACKER',
    `actor_id`               BIGINT       NULL,
    `detail`                 VARCHAR(500) NULL COMMENT '송장 수정 「구 → 신」 · 직권 취소 사유 등',
    `occurred_at`            DATETIME(6)  NOT NULL,
    PRIMARY KEY (`fulfillment_history_id`),
    KEY `idx_order_fulfillment_history_group` (`delivery_group_id`, `occurred_at`),
    CONSTRAINT `fk_order_fulfillment_history_group` FOREIGN KEY (`delivery_group_id`)
        REFERENCES `order_delivery_group` (`delivery_group_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
