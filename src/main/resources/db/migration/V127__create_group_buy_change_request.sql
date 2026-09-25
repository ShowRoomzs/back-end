-- 중단 · 조기 마감 요청 — 승인권자·가능 시점·검토 흐름이 같고 결과 상태만 다르다(설계서 1-6).
--
-- 「검토 중 추가 요청 불가」(§29-6)를 생성 컬럼 UNIQUE로 막는다. MySQL은 NULL을 여러 개 허용하므로
-- PENDING인 행만 공구당 1개로 제한된다. 브랜드와 인플루언서의 중단 요청이 동시에 들어와도 하나는 DB에서 떨어진다.
--
-- group_buy_id FK에 CASCADE를 걸지 않는다 — 생성 컬럼의 기반 컬럼 FK는 CASCADE를 쓸 수 없다(MySQL 제약).
CREATE TABLE `group_buy_change_request` (
    `change_request_id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `group_buy_id`                 BIGINT        NOT NULL,
    `request_type`                 VARCHAR(16)   NOT NULL COMMENT 'SUSPEND, EARLY_CLOSE',
    `requester_type`               VARCHAR(16)   NOT NULL COMMENT 'SELLER, CREATOR — 조기 마감은 SELLER만',
    `requester_id`                 BIGINT        NOT NULL,
    `reason_code`                  VARCHAR(64)   NOT NULL,
    `memo`                         VARCHAR(1000) NULL COMMENT 'ETC면 필수',
    `status_at_request`            VARCHAR(32)   NOT NULL COMMENT 'READY, IN_PROGRESS — C2·C4 문구 분기',
    `sales_order_count_at_request` INT           NULL COMMENT '판매 포트가 비어 있으면 NULL — 0이 아니다',
    `sales_amount_at_request`      BIGINT        NULL COMMENT '판매 포트가 비어 있으면 NULL — 0이 아니다',
    `status`                       VARCHAR(16)   NOT NULL COMMENT 'PENDING, APPROVED, REJECTED, LAPSED',
    `requested_at`                 DATETIME(6)   NOT NULL,
    `decided_at`                   DATETIME(6)   NULL,
    `decided_by`                   BIGINT        NULL,
    `decision_reason`              VARCHAR(1000) NULL COMMENT '승인·반려 모두 필수(M2·M3)',
    `pending_group_buy_id`         BIGINT GENERATED ALWAYS AS (IF(`status` = 'PENDING', `group_buy_id`, NULL)) STORED,
    PRIMARY KEY (`change_request_id`),
    CONSTRAINT `uk_group_buy_change_request_pending` UNIQUE (`pending_group_buy_id`),
    KEY `idx_group_buy_change_request_group_buy` (`group_buy_id`, `requested_at`),
    KEY `idx_group_buy_change_request_status` (`status`, `requested_at`),
    CONSTRAINT `fk_group_buy_change_request_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
