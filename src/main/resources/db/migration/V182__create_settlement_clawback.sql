-- 정산 차감(클로백 · 44 어드민 정산관리 설계서 1-7 · 6절) — 이미 생성된 정산의 항목이 운영자 사유 · 반품 통과 환불로 나가면 생긴다.
--
-- 한 환불 = 측별 2행(BRAND · CREATOR) · 같은 번호(CLW-NNNN). 측별로 독립 회수한다 — 브랜드 측은 같은 마켓의 다음 정산, 인플루언서
-- 측은 같은 인플루언서의 다음 정산(0-8). 다음 정산에서 다 못 빼면 남은 금액이 같은 번호의 seq + 1 행(PENDING)으로 넘어간다(6-2).
-- 상태 전이는 전부 조건부 UPDATE 다.
CREATE TABLE `settlement_clawback` (
    `clawback_id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `clawback_number`       VARCHAR(16)  NOT NULL COMMENT 'CLW-NNNN — 전역 순번',
    `seq`                   INT          NOT NULL COMMENT '이월 분할 — 1부터',
    `side`                  VARCHAR(8)   NOT NULL COMMENT 'BRAND, CREATOR',
    `origin_settlement_id`  BIGINT       NOT NULL COMMENT '환불된 항목이 들어 있던 정산',
    `order_id`              BIGINT       NULL,
    `delivery_group_id`     BIGINT       NULL,
    `order_product_id`      BIGINT       NULL,
    `refund_task_id`        BIGINT       NULL,
    `reason`                VARCHAR(32)  NULL COMMENT 'OperatorRefundReason — 반품 통과(운영자 개설)는 NULL',
    `refund_amount`         BIGINT       NOT NULL COMMENT '소비자 환불액 중 상품 금액분',
    `amount`                BIGINT       NOT NULL COMMENT '이 측에서 회수할 금액',
    `market_id`             BIGINT       NOT NULL COMMENT '브랜드 측 대상 키',
    `creator_id`            BIGINT       NOT NULL COMMENT '인플루언서 측 대상 키',
    `status`                VARCHAR(16)  NOT NULL COMMENT 'PENDING, APPLIED, UNRECOVERABLE',
    `applied_settlement_id` BIGINT       NULL,
    `applied_at`            DATETIME(6)  NULL,
    `unrecoverable_reason`  VARCHAR(32)  NULL COMMENT 'CREATOR_WITHDRAWN, MARKET_WITHDRAWN',
    `unrecoverable_at`      DATETIME(6)  NULL,
    `created_at`            DATETIME(6)  NOT NULL COMMENT '환불 집행 완료 시각',
    PRIMARY KEY (`clawback_id`),
    UNIQUE KEY `uk_settlement_clawback_number` (`clawback_number`, `side`, `seq`),
    KEY `idx_settlement_clawback_brand` (`status`, `side`, `market_id`),
    KEY `idx_settlement_clawback_creator` (`status`, `side`, `creator_id`),
    KEY `idx_settlement_clawback_refund_task` (`refund_task_id`),
    KEY `idx_settlement_clawback_delivery_group` (`delivery_group_id`),
    KEY `idx_settlement_clawback_applied` (`applied_settlement_id`),
    CONSTRAINT `fk_settlement_clawback_origin` FOREIGN KEY (`origin_settlement_id`)
        REFERENCES `settlement` (`settlement_id`),
    CONSTRAINT `fk_settlement_clawback_applied` FOREIGN KEY (`applied_settlement_id`)
        REFERENCES `settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 수정세금계산서(BRAND_TAX_INVOICE_CREDIT)는 차감 행마다 1행이다(V181 clawback_id).
ALTER TABLE `settlement_tax_document`
    ADD CONSTRAINT `fk_settlement_tax_document_clawback` FOREIGN KEY (`clawback_id`)
        REFERENCES `settlement_clawback` (`clawback_id`);
