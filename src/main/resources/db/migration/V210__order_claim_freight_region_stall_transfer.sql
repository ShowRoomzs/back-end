-- 1021 설계서 3 · 4 · 5절 — 반품 · 교환 비용 구조(기-18 · 기-20) · 귀책 변경 운임 정산(기-93) · 초과 환급 송금 · 자동 입고(기-17)
-- · 추적 정지 소비자 선택(기-15) · 직권 취소 주체(기-16).

-- 결제 시점 스냅샷 — 무료배송 기준 · 배송 권역 · 권역 추가비(편도).
ALTER TABLE `order_delivery_group`
    ADD COLUMN `free_shipping_threshold` INT NULL COMMENT '결제 시점 무료배송 기준금액 스냅샷(기-20) · null = 무료배송 없음',
    ADD COLUMN `delivery_region` VARCHAR(16) NOT NULL DEFAULT 'STANDARD' COMMENT 'STANDARD · JEJU · REMOTE — 우편번호 판정 스냅샷',
    ADD COLUMN `region_surcharge` INT NOT NULL DEFAULT 0 COMMENT '결제 시점 권역 추가비(편도) — delivery_fee 에 포함 · 재발송 배송비에 더한다',
    ADD COLUMN `stall_choice` VARCHAR(16) NULL COMMENT '추적 정지 소비자 선택 — RESHIP · REFUND(기-15)',
    ADD COLUMN `stall_choice_at` DATETIME(6) NULL,
    ADD COLUMN `stall_choice_open_at` DATETIME(6) NULL COMMENT '선택 개방 시각 = 추적 정지 판정 + N일',
    ADD COLUMN `cancel_actor_type` VARCHAR(16) NULL COMMENT '직권 취소 주체 — SELLER · ADMIN(기-16 취소율 · 어드민 대행 구분)';

-- 기존 주문 백필 — 결제 시점 값은 복원할 수 없어 마켓 현재 값(1021 설계서 Q11).
UPDATE `order_delivery_group` g
    JOIN `market` m ON m.market_id = g.market_id
   SET g.free_shipping_threshold = m.free_shipping_threshold;

-- 추가 배송비 권역 우편번호(운영 등록). 제주는 코드도 안다.
CREATE TABLE `remote_area_postcode` (
    `postcode_id`   BIGINT       NOT NULL AUTO_INCREMENT,
    `postcode_from` CHAR(5)      NOT NULL,
    `postcode_to`   CHAR(5)      NOT NULL,
    `region`        VARCHAR(16)  NOT NULL COMMENT 'JEJU · REMOTE',
    `name`          VARCHAR(100) NULL,
    `created_at`    DATETIME(6)  NOT NULL,
    PRIMARY KEY (`postcode_id`),
    KEY `idx_remote_area_postcode_range` (`postcode_from`, `postcode_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `remote_area_postcode` (`postcode_from`, `postcode_to`, `region`, `name`, `created_at`)
VALUES ('63000', '63644', 'JEJU', '제주특별자치도', NOW(6));

-- 검수 · 자동 입고 요약.
ALTER TABLE `order_claim_collection`
    ADD COLUMN `auto_received` BIT NOT NULL DEFAULT 0 COMMENT '택배 도착 후 N영업일 자동 입고(기-17)',
    ADD COLUMN `inspection_result` VARCHAR(24) NULL COMMENT 'PASSED · REASON_OVERTURNED · REJECTED — 요청 단위 요약',
    ADD COLUMN `inspection_note` VARCHAR(1000) NULL COMMENT '사유 번복 판정 근거 — 소비자에게 전달';

-- 귀책 변경 운임 정산(기-93 · 보완) — 운임은 전부 기본 배송비(편도) 정액.
CREATE TABLE `order_claim_freight_adjustment` (
    `adjustment_id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `claim_collection_id`   BIGINT      NOT NULL,
    `claim_id`              BIGINT      NULL,
    `delivery_group_id`     BIGINT      NOT NULL,
    `group_buy_id`          BIGINT      NULL,
    `market_id`             BIGINT      NOT NULL,
    `trigger_type`          VARCHAR(32) NOT NULL COMMENT 'REASON_OVERTURNED · FAULT_TO_SELLER_ON_PASS · REJECT_WITH_COLLECT · DISPUTE_ACCEPTED',
    `direction`             VARCHAR(16) NOT NULL COMMENT 'CHARGE_CONSUMER(브랜드 +) · REFUND_CONSUMER(브랜드 −)',
    `collect_freight`       INT         NOT NULL DEFAULT 0,
    `prepaid_freight`       INT         NOT NULL DEFAULT 0,
    `initial_fee_restored`  INT         NOT NULL DEFAULT 0,
    `amount`                INT         NOT NULL COMMENT '합계(양수)',
    `settled_via`           VARCHAR(16) NOT NULL COMMENT 'REFUND_DEDUCTION · CHARGE · REFUND_ADDITION · BANK_TRANSFER',
    `refund_task_id`        BIGINT      NULL,
    `charge_id`             BIGINT      NULL,
    `applied_settlement_id` BIGINT      NULL,
    `created_at`            DATETIME(6) NOT NULL,
    PRIMARY KEY (`adjustment_id`),
    KEY `idx_claim_freight_group_buy` (`group_buy_id`, `applied_settlement_id`),
    KEY `idx_claim_freight_collection` (`claim_collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 원래 결제액 초과 환급 — 운영자 계좌 송금(기록만 · 시스템은 송금하지 않는다).
ALTER TABLE `order_refund_task`
    ADD COLUMN `method` VARCHAR(16) NOT NULL DEFAULT 'PG_CANCEL' COMMENT 'PG_CANCEL · BANK_TRANSFER',
    ADD COLUMN `transfer_bank_code` VARCHAR(10) NULL,
    ADD COLUMN `transfer_account_enc` VARCHAR(255) NULL,
    ADD COLUMN `transfer_holder_name` VARCHAR(50) NULL,
    ADD COLUMN `transferred_at` DATETIME(6) NULL,
    ADD COLUMN `transferred_by` BIGINT NULL;

-- 소비자가 1:1 문의로 송금 계좌를 알려 준다(C10-5) — 51-6 의 sanction_id 와 같은 방식.
ALTER TABLE `one_to_one_inquiry`
    ADD COLUMN `refund_task_id` BIGINT NULL COMMENT '초과 환급 송금 항목 — 「환불 계좌 안내」 문의';

-- 브랜드 반품비 · 교환비 입력 폐기(기-18) — 반품 · 교환 배송비는 상품(마켓) 배송비로 자동 계산한다.
ALTER TABLE `market`
    DROP COLUMN `return_fee`,
    DROP COLUMN `exchange_fee`;
