-- 계약 상품 항목. 종결된 계약도 상품명·정가를 그대로 보여줘야 하므로
-- product_id만 들고 있지 않고 서명 시점의 사실을 스냅샷으로 복사해 둔다(설계서 0-5).
-- product_id는 공구 생성·정산 귀속용 참조로만 남긴다.
CREATE TABLE `contract_item` (
    `contract_item_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `contract_id`      BIGINT        NOT NULL,
    `product_id`       BIGINT        NULL COMMENT '미선택 행 저장 가능(임시저장)',
    `product_name`     VARCHAR(255)  NULL COMMENT '스냅샷',
    `regular_price`    INT           NULL COMMENT '스냅샷 — 화면의 「정가」',
    `group_buy_price`  INT           NULL COMMENT '공구가 · 10원 단위',
    -- 정산이 이 값을 그대로 쓴다. DOUBLE로 두면 15.0이 14.999999로 읽히는 날이 온다(설계서 1-5).
    `reward_rate`      DECIMAL(4,1)  NULL COMMENT '리워드율 0.0~90.0',
    `min_quantity`     INT           NULL COMMENT '최소 물량',
    `sort_order`       INT           NOT NULL DEFAULT 0 COMMENT '화면 행 순서',
    PRIMARY KEY (`contract_item_id`),
    KEY `idx_contract_item_contract_sort` (`contract_id`, `sort_order`),
    CONSTRAINT `fk_contract_item_contract` FOREIGN KEY (`contract_id`)
        REFERENCES `contract` (`contract_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_contract_item_product` FOREIGN KEY (`product_id`)
        REFERENCES `product` (`product_id`),
    CONSTRAINT `ck_contract_item_group_buy_price` CHECK (`group_buy_price` IS NULL OR (`group_buy_price` >= 0 AND `group_buy_price` % 10 = 0)),
    CONSTRAINT `ck_contract_item_reward_rate` CHECK (`reward_rate` IS NULL OR (`reward_rate` >= 0 AND `reward_rate` <= 90)),
    CONSTRAINT `ck_contract_item_min_quantity` CHECK (`min_quantity` IS NULL OR `min_quantity` >= 0),
    CONSTRAINT `ck_contract_item_regular_price` CHECK (`regular_price` IS NULL OR `regular_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- 예상 리워드(공구가 × 리워드율)는 저장하지 않는다 — 순수 파생값이고, 저장하면 두 소스가 어긋난다.
