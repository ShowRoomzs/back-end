-- 1021 설계서 1절 — 계약 성립 시점(기-05) · 해지(기-05) · 초안 승인 기록(기-30)

-- 계약 성립 = 양측 서명 완료 시각. concluded_at 은 운영자의 공구 생성 처리 시각으로 남는다.
ALTER TABLE `contract`
    ADD COLUMN `established_at` DATETIME(6) NULL COMMENT '계약 성립 = 양측 서명 완료 시각(기-05) · concluded_at 은 공구 생성 처리 시각';

UPDATE `contract`
   SET `established_at` = GREATEST(`brand_signed_at`, `creator_signed_at`)
 WHERE `status` IN ('CONCLUSION_PENDING', 'CONCLUDED')
   AND `brand_signed_at` IS NOT NULL
   AND `creator_signed_at` IS NOT NULL;

-- 해지 요청 — 성립 뒤 · 공구 오픈 전. 처리는 운영자가 상대방 동의를 운영팀 채널에서 확인한 뒤 한다.
CREATE TABLE `contract_termination_request` (
    `request_id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `contract_id`         BIGINT        NOT NULL,
    `requester_type`      VARCHAR(16)   NOT NULL COMMENT 'SELLER · CREATOR',
    `requester_id`        BIGINT        NOT NULL,
    `reason_code`         VARCHAR(40)   NOT NULL COMMENT 'SuspensionReasonCode — QUALITY_ISSUE · PRICE_TERMS_ERROR · NEGOTIATION_BROKEN · ETC',
    `memo`                VARCHAR(1000) NULL,
    `group_buy_id`        BIGINT        NULL,
    `consent_card_id`     BIGINT        NULL COMMENT '상대방 운영팀 채널 동의 확인 카드 message_id',
    `status`              VARCHAR(16)   NOT NULL COMMENT 'PENDING · HANDLED · WITHDRAWN · LAPSED',
    `requested_at`        DATETIME(6)   NOT NULL,
    `closed_at`           DATETIME(6)   NULL,
    `closed_by`           BIGINT        NULL,
    `pending_contract_id` BIGINT GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN `contract_id` END) VIRTUAL,
    PRIMARY KEY (`request_id`),
    UNIQUE KEY `uk_contract_termination_pending` (`pending_contract_id`),
    KEY `idx_contract_termination_contract` (`contract_id`, `requested_at`),
    KEY `idx_contract_termination_status` (`status`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 초안 승인 기록 — 브랜드가 인플루언서 콘텐츠 초안을 승인한 사실(기록 전용 · 체결완료 상세 카드)
CREATE TABLE `contract_draft_approval` (
    `approval_id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `contract_id`           BIGINT       NOT NULL,
    `title`                 VARCHAR(100) NOT NULL COMMENT '대상 — 「릴스 1차 초안」',
    `approved_at`           DATETIME(6)  NOT NULL,
    `approved_by_market_id` BIGINT       NOT NULL,
    `source`                VARCHAR(16)  NOT NULL DEFAULT 'THREAD' COMMENT 'THREAD · ETC',
    `thread_message_id`     BIGINT       NULL,
    `note`                  VARCHAR(300) NULL,
    `created_at`            DATETIME(6)  NOT NULL,
    `deleted_at`            DATETIME(6)  NULL,
    PRIMARY KEY (`approval_id`),
    KEY `idx_contract_draft_approval_contract` (`contract_id`, `approved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
