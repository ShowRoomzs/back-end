-- 계약 상품 옵션 항목 — 옵션별 최소 물량(옵션 계획서 2-2 · 계약서 생성규격 v0.2 「옵션최소물량」).
-- 공구가·리워드율은 contract_item(상품 단위)에 그대로 있고 옵션은 받지 않는다.
-- 옵션 판매가(공구가 + 옵션가)는 저장하지 않는다 — 정가 스냅샷 둘과 공구가의 파생값이다.
CREATE TABLE `contract_item_option` (
    `contract_item_option_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `contract_item_id`        BIGINT       NOT NULL,
    `variant_id`              BIGINT       NULL COMMENT '옵션이 지워지면 NULL — 계약서는 스냅샷으로 남는다',
    `variant_name`            VARCHAR(255) NULL COMMENT '스냅샷 — 옵션 없는 상품은 NULL',
    `regular_price`           INT          NULL COMMENT '스냅샷 — 계약 시점 옵션 정가. 옵션가 = 이 값 − contract_item.regular_price',
    `min_quantity`            INT          NULL COMMENT '옵션별 최소 물량 — 검토 요청 시 필수',
    `sort_order`              INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`contract_item_option_id`),
    -- 같은 옵션이 두 행이면 합계가 두 번 더해진다.
    UNIQUE KEY `uk_contract_item_option_variant` (`contract_item_id`, `variant_id`),
    KEY `idx_contract_item_option_variant` (`variant_id`),
    CONSTRAINT `fk_contract_item_option_item` FOREIGN KEY (`contract_item_id`)
        REFERENCES `contract_item` (`contract_item_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_contract_item_option_variant` FOREIGN KEY (`variant_id`)
        REFERENCES `product_variant` (`variant_id`) ON DELETE SET NULL,
    CONSTRAINT `ck_contract_item_option_min_quantity` CHECK (`min_quantity` IS NULL OR `min_quantity` >= 0),
    CONSTRAINT `ck_contract_item_option_regular_price` CHECK (`regular_price` IS NULL OR `regular_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
