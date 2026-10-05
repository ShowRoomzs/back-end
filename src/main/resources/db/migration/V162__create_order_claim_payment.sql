-- 클레임 재발송 배송비 결제 시도(앱 클레임 설계서 1-5) — 주문 결제(payment)와 테이블을 섞지 않는다.
-- 주문 결제의 상태 기계(주문 PAID 전이 · 재고 · 만료)와 무관한 작은 결제라, 포트원 호출부만 같이 쓴다.
--
-- payment_id 는 포트원 paymentId 와 같은 문자열이다 — clm-{charge_id}-{attempt}. 접두로 주문 결제와 구분해 웹훅이
-- 이쪽으로 넘긴다. charge_id · collection_id 에 FK 를 걸지 않는다 — 결제 대기 요청은 30분 뒤 지워지는데(초안 삭제),
-- 그 뒤에 도착한 결제를 자동 취소하려면 결제 행이 남아 있어야 한다.
CREATE TABLE `order_claim_payment` (
    `payment_id`          VARCHAR(64)  NOT NULL,
    `charge_id`           BIGINT       NOT NULL COMMENT 'order_claim_charge — FK 없음(초안 삭제 뒤에도 남는다)',
    `collection_id`       BIGINT       NOT NULL,
    `user_id`             BIGINT       NOT NULL,
    `attempt`             INT          NOT NULL,
    `status`              VARCHAR(30)  NOT NULL COMMENT 'READY · PAID · FAILED · CANCEL_REQUESTED · CANCELLED',
    `method`              VARCHAR(20)  NOT NULL COMMENT 'CARD · EASY_PAY',
    `card_issuer`         VARCHAR(20)  NULL,
    `easy_pay_provider`   VARCHAR(20)  NULL,
    `amount`              INT          NOT NULL,
    `channel_key`         VARCHAR(100) NULL,
    `pg_tx_id`            VARCHAR(100) NULL,
    `paid_at`             DATETIME(6)  NULL,
    `failed_at`           DATETIME(6)  NULL,
    `fail_code`           VARCHAR(100) NULL,
    `cancel_requested_at` DATETIME(6)  NULL,
    `cancelled_at`        DATETIME(6)  NULL,
    `raw_response`        TEXT         NULL COMMENT '마지막 GET /payments/{id} 원문',
    `created_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`payment_id`),
    CONSTRAINT `uk_order_claim_payment_attempt` UNIQUE (`charge_id`, `attempt`),
    KEY `idx_order_claim_payment_status` (`status`, `created_at`),
    KEY `idx_order_claim_payment_collection` (`collection_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
