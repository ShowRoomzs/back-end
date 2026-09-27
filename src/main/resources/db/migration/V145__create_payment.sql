-- 결제 계획서 3-2 — payment · payment_cancel · payment_webhook_event · payment_reconciliation_issue.
-- 문자셋·콜레이션을 명시하지 않는다 — orders(V36)와 같은 DB 기본값을 따라야 orders.paid_payment_id → payment.payment_id FK 가 걸린다(콜레이션이 다르면 MySQL 3780).
-- payment.payment_id 는 포트원 paymentId 와 같은 문자열({order_number}-{attempt})이라 웹훅·조회에서 조인 없이 찾는다.
CREATE TABLE `payment` (
    `payment_id`           VARCHAR(64)  NOT NULL,
    `order_id`             BIGINT       NOT NULL,
    `attempt`              INT          NOT NULL,
    `status`               VARCHAR(30)  NOT NULL COMMENT 'READY · PAID · FAILED · EXPIRED · SUPERSEDED · CANCELLED · CANCEL_REQUESTED · CANCELLED_MISMATCH · CANCEL_FAILED',
    `pre_registered_at`    DATETIME(6)  NULL COMMENT '포트원 사전 등록 성공 시각',
    `cancel_requested_at`  DATETIME(6)  NULL COMMENT '취소 선점 시각',
    `cancel_attempts`      INT          NOT NULL DEFAULT 0,
    `next_cancel_retry_at` DATETIME(6)  NULL,
    `mismatch_reason`      VARCHAR(40)  NULL COMMENT 'AMOUNT · ORDER_CLOSED · NOT_ORDER_PAYMENT — NULL 이면 사용자·운영자 취소',
    `method`               VARCHAR(20)  NOT NULL COMMENT 'CARD · EASY_PAY',
    `card_issuer`          VARCHAR(20)  NULL,
    `easy_pay_provider`    VARCHAR(20)  NULL,
    `amount`               INT          NOT NULL COMMENT '주문 total_amount 복사',
    `currency`             CHAR(3)      NOT NULL DEFAULT 'KRW',
    `channel_key`          VARCHAR(100) NULL,
    `pg_provider`          VARCHAR(50)  NULL,
    `pg_tx_id`             VARCHAR(100) NULL,
    `pg_method_json`       TEXT         NULL COMMENT '포트원이 실제로 처리한 결제수단 원문',
    `paid_at`              DATETIME(6)  NULL,
    `failed_at`            DATETIME(6)  NULL,
    `fail_code`            VARCHAR(100) NULL,
    `fail_message`         VARCHAR(500) NULL,
    `raw_response`         TEXT         NULL COMMENT '마지막 GET /payments/{id} 원문',
    `created_at`           DATETIME(6)  NOT NULL,
    `modified_at`          DATETIME(6)  NOT NULL,
    PRIMARY KEY (`payment_id`),
    UNIQUE KEY `uk_payment_order_attempt` (`order_id`, `attempt`),
    INDEX `idx_payment_status_next_cancel_retry` (`status`, `next_cancel_retry_at`),
    INDEX `idx_payment_order_status` (`order_id`, `status`),
    CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
) ENGINE=InnoDB;

CREATE TABLE `payment_cancel` (
    `cancel_id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `payment_id`         VARCHAR(64)  NOT NULL,
    `amount`             INT          NOT NULL,
    `reason`             VARCHAR(255) NULL,
    `status`             VARCHAR(20)  NOT NULL COMMENT 'REQUESTED · SUCCEEDED · FAILED',
    `pg_cancellation_id` VARCHAR(100) NULL,
    `requested_by`       VARCHAR(20)  NOT NULL COMMENT 'USER · SYSTEM · ADMIN',
    `requested_at`       DATETIME(6)  NOT NULL,
    `completed_at`       DATETIME(6)  NULL,
    `raw_response`       TEXT         NULL,
    `created_at`         DATETIME(6)  NOT NULL,
    `modified_at`        DATETIME(6)  NOT NULL,
    PRIMARY KEY (`cancel_id`),
    INDEX `idx_payment_cancel_payment` (`payment_id`, `status`),
    CONSTRAINT `fk_payment_cancel_payment` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`payment_id`)
) ENGINE=InnoDB;

CREATE TABLE `payment_webhook_event` (
    `event_id`      BIGINT        NOT NULL AUTO_INCREMENT,
    `webhook_id`    VARCHAR(100)  NOT NULL COMMENT '헤더 webhook-id',
    `payment_id`    VARCHAR(64)   NULL COMMENT '모르는 결제도 기록한다',
    `event_type`    VARCHAR(100)  NULL,
    `payload`       TEXT          NULL,
    `received_at`   DATETIME(6)   NOT NULL,
    `processed_at`  DATETIME(6)   NULL,
    `result`        VARCHAR(20)   NOT NULL COMMENT 'RECEIVED · PROCESSED · IGNORED · FAILED',
    `error_message` VARCHAR(1000) NULL,
    `attempts`      INT           NOT NULL DEFAULT 1,
    PRIMARY KEY (`event_id`),
    UNIQUE KEY `uk_payment_webhook_event_webhook_id` (`webhook_id`),
    INDEX `idx_payment_webhook_event_result` (`result`, `attempts`)
) ENGINE=InnoDB;

CREATE TABLE `payment_reconciliation_issue` (
    `issue_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `payment_id`      VARCHAR(64)  NOT NULL,
    `kind`            VARCHAR(40)  NOT NULL COMMENT 'PORTONE_PAID_NOT_OURS · PORTONE_CANCELLED_NOT_OURS · OURS_PAID_NOT_PORTONE · AMOUNT_DIFF',
    `portone_status`  VARCHAR(30)  NULL,
    `portone_amount`  BIGINT       NULL,
    `our_status`      VARCHAR(30)  NULL,
    `our_amount`      INT          NULL,
    `detected_at`     DATETIME(6)  NOT NULL,
    `resolved_at`     DATETIME(6)  NULL,
    `resolution_note` VARCHAR(500) NULL,
    PRIMARY KEY (`issue_id`),
    UNIQUE KEY `uk_payment_reconciliation_issue_payment_kind` (`payment_id`, `kind`)
) ENGINE=InnoDB;

-- orders ↔ payment 순환 FK — payment 가 생긴 뒤에 건다(V143 참고).
ALTER TABLE `orders`
    ADD CONSTRAINT `fk_orders_paid_payment` FOREIGN KEY (`paid_payment_id`) REFERENCES `payment` (`payment_id`);
