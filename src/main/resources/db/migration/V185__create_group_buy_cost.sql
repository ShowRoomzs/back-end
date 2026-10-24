-- 성과 관리 원가 · 수익(48 파트너센터 성과관리 설계서 0-10 · 6-1) — 브랜드 전용 선택 입력.
--
-- 공구 1건 = 원가 1행 + 계약 상품마다 개당 원가 1행. 확정 수량 · 소계 · 총 원가 · 수익은 열이 없다 — 정산액 · 고정 지급비가 조정 합의로
-- 바뀌면 같이 바뀌어야 하므로 조회 시 계산한다. 이 테이블은 /v1/seller/** 만 읽는다(원가는 리워드율 협상 카드 · §48-3).
-- 이력을 두지 않는다 — 브랜드의 사적 메모라 운영자가 볼 일이 없다.
CREATE TABLE `group_buy_cost` (
    `group_buy_cost_id`    BIGINT      NOT NULL AUTO_INCREMENT,
    `group_buy_id`         BIGINT      NOT NULL,
    `market_id`            BIGINT      NOT NULL COMMENT '소유 검사 · 인덱스용 복제',
    `packaging_cost`       BIGINT      NULL COMMENT '부자재비 — 박스 · 완충재 · 증정품 · 인쇄물',
    `shipping_cost`        BIGINT      NULL COMMENT '배송비 — 실제 택배 계약 기준 지출액',
    `return_disposal_cost` BIGINT      NULL COMMENT '반품 폐기 손실 — 재판매 불가로 폐기한 상품의 원가',
    `etc_cost`             BIGINT      NULL COMMENT '기타 — 위에 해당하지 않는 이번 공구 지출',
    `created_at`           DATETIME(6) NOT NULL,
    `updated_at`           DATETIME(6) NOT NULL,
    `updated_by`           BIGINT      NOT NULL COMMENT 'seller_id',
    PRIMARY KEY (`group_buy_cost_id`),
    UNIQUE KEY `uk_group_buy_cost_group_buy` (`group_buy_id`),
    KEY `idx_group_buy_cost_market` (`market_id`),
    CONSTRAINT `fk_group_buy_cost_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `group_buy_cost_item` (
    `group_buy_cost_item_id` BIGINT NOT NULL AUTO_INCREMENT,
    `group_buy_cost_id`      BIGINT NOT NULL,
    `contract_item_id`       BIGINT NOT NULL,
    `unit_cost`              BIGINT NULL COMMENT '개당 원가 — 미입력은 NULL',
    PRIMARY KEY (`group_buy_cost_item_id`),
    UNIQUE KEY `uk_group_buy_cost_item` (`group_buy_cost_id`, `contract_item_id`),
    KEY `idx_group_buy_cost_item_contract_item` (`contract_item_id`),
    CONSTRAINT `fk_group_buy_cost_item_cost` FOREIGN KEY (`group_buy_cost_id`)
        REFERENCES `group_buy_cost` (`group_buy_cost_id`),
    CONSTRAINT `fk_group_buy_cost_item_contract_item` FOREIGN KEY (`contract_item_id`)
        REFERENCES `contract_item` (`contract_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
