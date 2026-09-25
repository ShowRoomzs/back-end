-- 계약 이행 확인 — §29-10 「계약 전체에 1회 · 이행/미이행 2지 · 불가역」(설계서 1-9).
-- 행이 없음 = 확인 전. 「체크 안 함」과 「미이행」은 뜻이 다르므로 boolean false로 표현하지 않는다.
-- 정산 보류는 파생값이다 — EXISTS(result = UNFULFILLED) AND group_buy.fulfillment_resolved_at IS NULL.
CREATE TABLE `group_buy_fulfillment_check` (
    `fulfillment_check_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `group_buy_id`         BIGINT        NOT NULL,
    `checker_side`         VARCHAR(16)   NOT NULL COMMENT 'SELLER, CREATOR — 측별 1회',
    `result`               VARCHAR(16)   NOT NULL COMMENT 'FULFILLED, UNFULFILLED',
    `reason`               VARCHAR(2000) NULL COMMENT 'UNFULFILLED면 필수 — 3자 스레드의 첫 글',
    `auto_confirmed`       BIT(1)        NOT NULL DEFAULT b'0' COMMENT '무응답 자동 이행 여부',
    `checked_at`           DATETIME(6)   NOT NULL,
    `checked_by`           BIGINT        NULL COMMENT '자동 이행이면 NULL',
    `thread_id`            BIGINT        NULL COMMENT '미이행 시 열린 3자 스레드',
    PRIMARY KEY (`fulfillment_check_id`),
    CONSTRAINT `uk_group_buy_fulfillment_side` UNIQUE (`group_buy_id`, `checker_side`),
    CONSTRAINT `fk_group_buy_fulfillment_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 이슈 스레드 — 정산과 무관한 이견을 여는 창구(§29-10). 이슈는 상태가 아니다.
-- 열린 건 1개 제약(B5e 중복 개설 방지)만 걸고 총 건수는 막지 않는다 — 종결 후 새 이견은 새 행이다.
CREATE TABLE `group_buy_issue` (
    `issue_id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `group_buy_id`      BIGINT        NOT NULL,
    `opener_type`       VARCHAR(16)   NOT NULL COMMENT 'SELLER, CREATOR',
    `opener_id`         BIGINT        NOT NULL,
    `issue_type`        VARCHAR(32)   NOT NULL COMMENT 'CONTENT_FULFILLMENT, TERMS_INTERPRETATION, SETTLEMENT_AMOUNT, ETC',
    `content`           VARCHAR(2000) NOT NULL COMMENT '스레드 첫 글',
    `thread_id`         BIGINT        NULL,
    `status`            VARCHAR(16)   NOT NULL COMMENT 'OPEN, CLOSED',
    `opened_at`         DATETIME(6)   NOT NULL,
    `closed_at`         DATETIME(6)   NULL,
    `open_group_buy_id` BIGINT GENERATED ALWAYS AS (IF(`status` = 'OPEN', `group_buy_id`, NULL)) STORED,
    PRIMARY KEY (`issue_id`),
    CONSTRAINT `uk_group_buy_issue_open` UNIQUE (`open_group_buy_id`),
    KEY `idx_group_buy_issue_group_buy` (`group_buy_id`, `opened_at`),
    CONSTRAINT `fk_group_buy_issue_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
