-- 클레임 첨부 · 처리 이력 · 추가 결제 · 미결제 고지(35 설계서 1-9 · 앱 클레임 설계서 1-5).

-- 첨부 — 소비자 신청 사진(CONSUMER) · 브랜드 거절 증빙(SELLER). URL 만 온다(업로드는 기존 이미지 API).
CREATE TABLE `order_claim_attachment` (
    `attachment_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `claim_id`      BIGINT        NOT NULL,
    `owner`         VARCHAR(16)   NOT NULL COMMENT 'CONSUMER, SELLER',
    `image_url`     VARCHAR(2048) NOT NULL,
    `sort_order`    INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`attachment_id`),
    KEY `idx_order_claim_attachment_claim` (`claim_id`),
    CONSTRAINT `fk_order_claim_attachment_claim` FOREIGN KEY (`claim_id`) REFERENCES `order_claim` (`claim_id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 처리 이력 — append-only. order_fulfillment_history 와 같은 모양이다.
CREATE TABLE `order_claim_history` (
    `claim_history_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `claim_id`         BIGINT       NOT NULL,
    `event_type`       VARCHAR(64)  NOT NULL,
    `actor_type`       VARCHAR(16)  NOT NULL,
    `actor_id`         BIGINT       NULL,
    `detail`           VARCHAR(500) NULL,
    `occurred_at`      DATETIME(6)  NOT NULL,
    PRIMARY KEY (`claim_history_id`),
    KEY `idx_order_claim_history_claim` (`claim_id`, `occurred_at`),
    CONSTRAINT `fk_order_claim_history_claim` FOREIGN KEY (`claim_id`) REFERENCES `order_claim` (`claim_id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 추가 결제 — 소비자가 클레임 때문에 내는 돈. 배송비는 박스(요청)에 붙는다.
CREATE TABLE `order_claim_charge` (
    `charge_id`       BIGINT      NOT NULL AUTO_INCREMENT,
    `collection_id`   BIGINT      NOT NULL,
    `type`            VARCHAR(20) NOT NULL COMMENT 'EXCHANGE_RESHIP(교환 요청 시 선결제), REJECT_RESHIP(반려 상품 재발송)',
    `amount`          INT         NOT NULL,
    `status`          VARCHAR(16) NOT NULL COMMENT 'PENDING, PAID, DEDUCTED, COVERED, VOID, REFUNDED',
    `due_at`          DATETIME(6) NULL COMMENT 'REJECT_RESHIP 의 결제 기한 — 지나면 미결제 고지가 시작된다',
    `paid_payment_id` VARCHAR(64) NULL,
    `settled_at`      DATETIME(6) NULL,
    `created_at`      DATETIME(6) NOT NULL,
    PRIMARY KEY (`charge_id`),
    KEY `idx_order_claim_charge_collection` (`collection_id`, `type`, `status`),
    CONSTRAINT `fk_order_claim_charge_collection` FOREIGN KEY (`collection_id`)
        REFERENCES `order_claim_collection` (`collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 미결제 고지 — 거절 보류에서 재배송비를 결제하지 않을 때 보낸 고지의 회차별 기록.
-- UNIQUE (claim_id, seq) — 발송 재시도가 같은 회차를 두 번 세지 않는다.
CREATE TABLE `order_claim_notice` (
    `notice_id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `claim_id`    BIGINT      NOT NULL,
    `seq`         INT         NOT NULL COMMENT '회차 — 1부터',
    `notified_at` DATETIME(6) NOT NULL,
    `channel`     VARCHAR(20) NULL,
    `actor_type`  VARCHAR(16) NOT NULL,
    `actor_id`    BIGINT      NULL,
    PRIMARY KEY (`notice_id`),
    CONSTRAINT `uk_order_claim_notice_seq` UNIQUE (`claim_id`, `seq`),
    CONSTRAINT `fk_order_claim_notice_claim` FOREIGN KEY (`claim_id`) REFERENCES `order_claim` (`claim_id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
