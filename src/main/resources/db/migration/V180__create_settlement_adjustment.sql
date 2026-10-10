-- 정산 조정 협의(44 정산조정 이슈스레드 설계서 1-1 · 1-2 · 1-9) — 정산 1건에 협의 1건(settlement_id UNIQUE) + 제안 N건.
--
-- 금액이 든 글은 전부 제안이다 — 첫 요청이 seq 1, 다른 금액 제안은 이전 제안을 COUNTERED 로 닫고 seq + 1 을 만든다. 동의 · 반대는
-- 제안 행의 상태 변화다(별도 응답 테이블 없음). 모든 전이는 조건부 UPDATE 라 낙관적 잠금 컬럼을 두지 않는다.
-- 3자 스레드는 그 쌍의 PAIR 연결에 thread_kind = SETTLEMENT_ADJUSTMENT 로 붙는다(새 채팅 테이블 없음).
CREATE TABLE `settlement_adjustment` (
    `adjustment_id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `settlement_id`          BIGINT       NOT NULL,
    `settlement_number`      VARCHAR(16)  NOT NULL COMMENT 'STL-YYMM-NNN 스냅샷',
    `group_buy_id`           BIGINT       NOT NULL COMMENT '비정규화 — 스레드 subject_id 와 같은 값',
    `market_id`              BIGINT       NOT NULL,
    `creator_id`             BIGINT       NOT NULL,
    `thread_id`              BIGINT       NOT NULL,
    `requester_type`         VARCHAR(16)  NOT NULL COMMENT 'SELLER, CREATOR — 처음 요청한 쪽',
    `status`                 VARCHAR(16)  NOT NULL COMMENT 'OPEN, AGREED, EXPIRED',
    `original_reward_amount` BIGINT       NOT NULL COMMENT '요청 시점 정산 reward_amount 스냅샷',
    `max_reward_amount`      BIGINT       NOT NULL COMMENT '제안 상한 — 개설 시 정산 포트가 계산한 값',
    `agreed_reward_amount`   BIGINT       NULL,
    `final_reward_amount`    BIGINT       NULL COMMENT '합의면 agreed · 만료면 original · 종결 전 NULL',
    `opened_at`              DATETIME(6)  NOT NULL,
    `deadline_at`            DATETIME(6)  NOT NULL COMMENT '개설 + N영업일 + 마감 시각 · 불변',
    `notice_due_at`          DATETIME(6)  NOT NULL COMMENT 'D-1 통지 예정 시각',
    `notice_sent_at`         DATETIME(6)  NULL,
    `closed_at`              DATETIME(6)  NULL,
    `created_at`             DATETIME(6)  NULL,
    `modified_at`            DATETIME(6)  NULL,
    PRIMARY KEY (`adjustment_id`),
    UNIQUE KEY `uk_settlement_adjustment_settlement` (`settlement_id`),
    UNIQUE KEY `uk_settlement_adjustment_thread` (`thread_id`),
    KEY `idx_settlement_adjustment_deadline` (`status`, `deadline_at`),
    KEY `idx_settlement_adjustment_notice` (`status`, `notice_due_at`),
    KEY `idx_settlement_adjustment_market` (`market_id`),
    KEY `idx_settlement_adjustment_creator` (`creator_id`),
    KEY `idx_settlement_adjustment_group_buy` (`group_buy_id`),
    CONSTRAINT `fk_settlement_adjustment_settlement` FOREIGN KEY (`settlement_id`) REFERENCES `settlement` (`settlement_id`),
    CONSTRAINT `fk_settlement_adjustment_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`),
    CONSTRAINT `fk_settlement_adjustment_thread` FOREIGN KEY (`thread_id`) REFERENCES `message_thread` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `settlement_adjustment_proposal` (
    `proposal_id`     BIGINT        NOT NULL AUTO_INCREMENT,
    `adjustment_id`   BIGINT        NOT NULL,
    `seq`             INT           NOT NULL COMMENT '1부터 — 동시 제안 차단',
    `proposer_type`   VARCHAR(16)   NOT NULL COMMENT 'SELLER, CREATOR',
    `proposer_id`     BIGINT        NOT NULL COMMENT '마켓 id 또는 크리에이터 id',
    `reward_amount`   BIGINT        NOT NULL COMMENT '제안 리워드(공급가)',
    `reason`          VARCHAR(1000) NULL COMMENT '첫 요청은 필수 · 다른 금액 제안은 선택',
    `status`          VARCHAR(16)   NOT NULL COMMENT 'PENDING, ACCEPTED, REJECTED, COUNTERED, CLOSED',
    `proposed_at`     DATETIME(6)   NOT NULL,
    `responded_at`    DATETIME(6)   NULL,
    `responder_type`  VARCHAR(16)   NULL,
    `responder_id`    BIGINT        NULL,
    `card_message_id` BIGINT        NULL COMMENT '이 제안의 카드(message_id)',
    PRIMARY KEY (`proposal_id`),
    UNIQUE KEY `uk_settlement_adjustment_proposal_seq` (`adjustment_id`, `seq`),
    CONSTRAINT `fk_settlement_adjustment_proposal_adjustment` FOREIGN KEY (`adjustment_id`)
        REFERENCES `settlement_adjustment` (`adjustment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 값 목록 문서화 — 길이는 그대로 들어간다(card_type 최장 SETTLEMENT_ADJUSTMENT_DEADLINE_NOTICE 37자 · ref_type 최장
-- SETTLEMENT_ADJUSTMENT_PROPOSAL 30자). message_thread.thread_kind(VARCHAR 32 · SETTLEMENT_ADJUSTMENT 21자)는 생성 컬럼
-- connection_thread_key 가 참조하는 열이라 정의를 다시 쓰지 않는다 — 값 목록은 ThreadKind enum 이 정본이다.
ALTER TABLE `message`
    MODIFY COLUMN `card_type` VARCHAR(40) NULL COMMENT 'CONTRACT_RESEND_REQUEST, CONTRACT_ADMIN_CANCELED, SETTLEMENT_ADJUSTMENT_OPENED, SETTLEMENT_ADJUSTMENT_REQUEST, SETTLEMENT_ADJUSTMENT_COUNTER, SETTLEMENT_ADJUSTMENT_ACCEPTED, SETTLEMENT_ADJUSTMENT_REJECTED, SETTLEMENT_ADJUSTMENT_DEADLINE_NOTICE, SETTLEMENT_ADJUSTMENT_AGREED, SETTLEMENT_ADJUSTMENT_EXPIRED',
    MODIFY COLUMN `ref_type` VARCHAR(32) NULL COMMENT 'CONTRACT_RESEND_REQUEST, CONTRACT, SETTLEMENT_ADJUSTMENT, SETTLEMENT_ADJUSTMENT_PROPOSAL';
