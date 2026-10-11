-- 정산 본체(44 어드민 정산관리 설계서 1-2 · 1-3 · 1-4 · 1-8 · 1-9 · 10절).
--
-- 공구 1건 = 정산 1건(group_buy_id UNIQUE). 금액은 생성 시점의 스냅샷이다 — 분해 표의 행 하나가 컬럼 하나이고 적용 요율도
-- 같이 적는다(0-3). 조정 협의로 리워드가 바뀌면 리워드 계열 컬럼만 다시 쓰고 original_reward_amount 는 그대로 둔다(4-3).
-- 상태 전이는 전부 조건부 UPDATE(WHERE status = …)이고 version 은 엔티티 변경 감지의 낙관적 잠금이다.
-- 조정 협의(V180) · 증빙(V181) · 차감(V182)은 뒤 마이그레이션이 더한다. clawback_number_sequence 는 V182 전까지 비어 있다.
CREATE TABLE `settlement` (
    `settlement_id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `settlement_number`            VARCHAR(16)  NOT NULL COMMENT 'STL-YYMM-NNN',
    `group_buy_id`                 BIGINT       NOT NULL,
    `contract_id`                  BIGINT       NOT NULL,
    `market_id`                    BIGINT       NOT NULL,
    `creator_id`                   BIGINT       NOT NULL,
    `status`                       VARCHAR(20)  NOT NULL COMMENT 'REVIEWING, ADJUSTING, PAYOUT_SCHEDULED, PAID, PAYOUT_FAILED',
    `creator_business_type`        VARCHAR(16)  NOT NULL COMMENT '생성 시점 스냅샷 · INDIVIDUAL, BUSINESS',
    `period_start_at`              DATETIME(6)  NOT NULL COMMENT '공구 start_at',
    `period_end_at`                DATETIME(6)  NOT NULL COMMENT '공구 ended_at',
    `orders_closed_at`             DATETIME(6)  NOT NULL COMMENT '마지막 하위주문 종결 시각',
    `created_at`                   DATETIME(6)  NOT NULL COMMENT '정산 생성 = 금액 공개',
    `review_due_at`                DATETIME(6)  NOT NULL COMMENT '확인 마감 23:59:59',
    `confirmed_at`                 DATETIME(6)  NULL,
    `confirm_reason`               VARCHAR(10)  NULL COMMENT 'AUTO, AGREED, EXPIRED',
    `payout_due_date`              DATE         NULL COMMENT '브랜드 · 플랫폼 · 비사업자 인플루언서 몫 지급 예정일 = 확정일 + N영업일',
    `paid_at`                      DATETIME(6)  NULL COMMENT '전 수취자 지급 완료',
    `gross_order_amount`           BIGINT       NOT NULL COMMENT '총 주문 금액(상품 결제금액 · 배송비 제외)',
    `cancel_deduction`             BIGINT       NOT NULL,
    `cancel_count`                 INT          NOT NULL,
    `return_deduction`             BIGINT       NOT NULL,
    `return_count`                 INT          NOT NULL,
    `delivery_exception_deduction` BIGINT       NOT NULL COMMENT '반송 완료 · 분실 처리 · 정산 전 집행된 운영자 사유 환불',
    `delivery_exception_count`     INT          NOT NULL,
    `confirmed_sales_amount`       BIGINT       NOT NULL COMMENT '확정 거래액 = Σ settlement_item.settled_amount',
    `pg_fee_rate`                  DECIMAL(5,4) NOT NULL,
    `pg_fee_amount`                BIGINT       NOT NULL,
    `platform_fee_rate`            DECIMAL(5,4) NOT NULL,
    `platform_fee_amount`          BIGINT       NOT NULL COMMENT '베타 0 · 행 유지',
    `original_reward_amount`       BIGINT       NOT NULL COMMENT '산출 시점 리워드 — 조정 전 · 불변',
    `reward_amount`                BIGINT       NOT NULL COMMENT '현재 유효 리워드 — 합의로만 바뀐다',
    `reward_vat_rate`              DECIMAL(5,4) NOT NULL,
    `reward_vat_amount`            BIGINT       NOT NULL,
    `reship_fee_amount`            BIGINT       NOT NULL COMMENT '교환 · 반려 재발송비 — 브랜드 가산',
    `reship_count`                 INT          NOT NULL,
    `consumer_delivery_fee_amount` BIGINT       NOT NULL COMMENT '소비자 결제 배송비 — 브랜드 가산(13절 B-6)',
    `brand_clawback_amount`        BIGINT       NOT NULL DEFAULT 0,
    `brand_payout_amount`          BIGINT       NOT NULL COMMENT '브랜드 수취액(차감 후)',
    `reward_clawback_amount`       BIGINT       NOT NULL DEFAULT 0,
    `withholding_income_rate`      DECIMAL(5,4) NOT NULL,
    `withholding_local_rate`       DECIMAL(5,4) NOT NULL,
    `withholding_amount`           BIGINT       NOT NULL COMMENT '비사업자만 · 차감 후 기준 · 소득세 + 지방소득세',
    `creator_vat_amount`           BIGINT       NOT NULL COMMENT '사업자만 · 세금계산서 부가세',
    `creator_payout_amount`        BIGINT       NOT NULL COMMENT '인플루언서 실지급액',
    `platform_share_amount`        BIGINT       NOT NULL COMMENT '플랫폼 수수료 + (리워드 부가세 − 인플루언서 부가세) + 차감 회수',
    `version`                      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`settlement_id`),
    UNIQUE KEY `uk_settlement_number` (`settlement_number`),
    UNIQUE KEY `uk_settlement_group_buy` (`group_buy_id`),
    KEY `idx_settlement_status_due` (`status`, `review_due_at`),
    KEY `idx_settlement_status_payout` (`status`, `payout_due_date`),
    KEY `idx_settlement_market` (`market_id`, `status`, `created_at`),
    KEY `idx_settlement_creator` (`creator_id`, `status`, `created_at`),
    CONSTRAINT `fk_settlement_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`),
    CONSTRAINT `fk_settlement_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract` (`contract_id`),
    CONSTRAINT `fk_settlement_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`),
    CONSTRAINT `fk_settlement_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 명세 — 주문 항목 1행(1-3). 상품명 · 소비자 · 단가는 스냅샷이다. 한 항목은 한 정산에만(order_product_id UNIQUE).
-- 소비자 열은 어드민 · 파트너 명세에만 내린다(스튜디오 DTO 에 없다).
CREATE TABLE `settlement_item` (
    `settlement_item_id`   BIGINT       NOT NULL AUTO_INCREMENT,
    `settlement_id`        BIGINT       NOT NULL,
    `order_id`             BIGINT       NOT NULL,
    `delivery_group_id`    BIGINT       NOT NULL,
    `order_product_id`     BIGINT       NOT NULL,
    `order_number`         VARCHAR(30)  NOT NULL,
    `sub_order_number`     VARCHAR(40)  NULL,
    `consumer_user_id`     BIGINT       NULL,
    `consumer_name_masked` VARCHAR(64)  NULL,
    `product_id`           BIGINT       NULL,
    `product_name`         VARCHAR(255) NOT NULL,
    `option_name`          VARCHAR(255) NULL,
    `quantity`             INT          NOT NULL,
    `returned_quantity`    INT          NOT NULL,
    `settled_quantity`     INT          NOT NULL COMMENT 'quantity − returned_quantity − 정산 전 환불 수량 · 취소 · 배송 예외면 0',
    `unit_price`           BIGINT       NOT NULL COMMENT 'order_product.price = 공구가 + 옵션가',
    `paid_amount`          BIGINT       NOT NULL COMMENT 'unit_price × quantity',
    `settled_amount`       BIGINT       NOT NULL COMMENT '정산 반영액',
    `reward_rate`          DECIMAL(4,1) NOT NULL COMMENT '계약 항목 리워드율(%)',
    `unit_reward`          BIGINT       NOT NULL COMMENT 'RewardCalculator.calcUnitReward(unit_price, reward_rate)',
    `reward_amount`        BIGINT       NOT NULL COMMENT 'unit_reward × settled_quantity',
    `status`               VARCHAR(20)  NOT NULL COMMENT 'CONFIRMED, PARTIAL_RETURNED, RETURNED, CANCELLED, DELIVERY_EXCEPTION',
    PRIMARY KEY (`settlement_item_id`),
    UNIQUE KEY `uk_settlement_item_order_product` (`order_product_id`),
    KEY `idx_settlement_item_settlement` (`settlement_id`, `order_number`),
    KEY `idx_settlement_item_group` (`delivery_group_id`),
    CONSTRAINT `fk_settlement_item_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `settlement` (`settlement_id`),
    CONSTRAINT `fk_settlement_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`),
    CONSTRAINT `fk_settlement_item_group` FOREIGN KEY (`delivery_group_id`) REFERENCES `order_delivery_group` (`delivery_group_id`),
    CONSTRAINT `fk_settlement_item_order_product` FOREIGN KEY (`order_product_id`) REFERENCES `order_product` (`order_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3자 분배 — 수취자 1행(1-4). 정산 상태는 이 행들에서 파생해 저장한다(0-4). 계좌는 지급 지시 시점 스냅샷이다.
CREATE TABLE `settlement_payout` (
    `payout_id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `settlement_id`      BIGINT       NOT NULL,
    `payee`              VARCHAR(10)  NOT NULL COMMENT 'BRAND, CREATOR, PLATFORM',
    `amount`             BIGINT       NOT NULL,
    `status`             VARCHAR(16)  NOT NULL COMMENT 'WAITING, HELD, BLOCKED, SCHEDULED, REQUESTED, PAID, FAILED, NOT_APPLICABLE',
    `due_date`           DATE         NULL,
    `bank_name`          VARCHAR(50)  NULL,
    `account_number_enc` VARCHAR(255) NULL COMMENT 'PersonalDataCipher 암호문',
    `account_holder`     VARCHAR(64)  NULL,
    `pg_reference`       VARCHAR(100) NULL,
    `requested_at`       DATETIME(6)  NULL,
    `paid_at`            DATETIME(6)  NULL,
    `failed_at`          DATETIME(6)  NULL,
    `fail_code`          VARCHAR(50)  NULL,
    `fail_reason`        VARCHAR(500) NULL,
    `attempt`            INT          NOT NULL DEFAULT 0 COMMENT '재분배 횟수',
    PRIMARY KEY (`payout_id`),
    UNIQUE KEY `uk_settlement_payout_payee` (`settlement_id`, `payee`),
    KEY `idx_settlement_payout_status_due` (`status`, `due_date`),
    CONSTRAINT `fk_settlement_payout_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 처리 이력(1-8) — 07b 우측 「처리 이력 · 최신순」. 파트너 · 스튜디오 응답에는 싣지 않는다.
CREATE TABLE `settlement_history` (
    `history_id`    BIGINT       NOT NULL AUTO_INCREMENT,
    `settlement_id` BIGINT       NOT NULL,
    `event_type`    VARCHAR(40)  NOT NULL,
    `actor_type`    VARCHAR(10)  NOT NULL COMMENT 'SYSTEM, PG, ADMIN, SELLER, CREATOR',
    `actor_id`      BIGINT       NULL,
    `detail`        VARCHAR(500) NULL,
    `occurred_at`   DATETIME(6)  NOT NULL,
    PRIMARY KEY (`history_id`),
    KEY `idx_settlement_history_settlement` (`settlement_id`, `occurred_at`),
    CONSTRAINT `fk_settlement_history_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 정산번호 STL-YYMM-NNN 의 월별 일련번호(1-9) — contract_number_sequence 와 같은 upsert 구조.
CREATE TABLE `settlement_number_sequence` (
    `seq_month`  VARCHAR(4) NOT NULL COMMENT 'YYMM',
    `last_no`    INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`seq_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 차감번호 CLW-NNNN 의 전역 일련번호(1-9) — 행 1개(id = 1). 차감(V182)이 쓴다.
CREATE TABLE `clawback_number_sequence` (
    `id`      INT NOT NULL,
    `last_no` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
