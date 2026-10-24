-- 어드민 회원 관리 — 브랜드 상세(15) · 브랜드 제재 · 탈퇴 심사 도메인(52 설계서 1 · 10절).
-- 회원 상태는 기존 market.status(MarketStatus)를 그대로 쓴다 — 컬럼 변경 · 백필 없음(0-2 · 0-6 · 0-7).
-- 열린 정지 · 열린 신청은 생성 컬럼 UNIQUE 로 마켓당 1건(connection_thread_key 와 같은 관례). 번호는 세션 간 합의로 V199(V189~V198 은 다른 작업).

-- 1-2. 정지 1건 = 1행 · 해제까지. 열린 정지 = released_at IS NULL.
CREATE TABLE `brand_sanction` (
    `sanction_id`       BIGINT       NOT NULL AUTO_INCREMENT,
    `market_id`         BIGINT       NOT NULL,
    `reason`            VARCHAR(40)  NOT NULL COMMENT 'BrandSuspensionReason',
    `reason_detail`     VARCHAR(500) NULL     COMMENT 'OTHER 면 필수 · 그 밖은 선택',
    `payout_held`       TINYINT(1)   NOT NULL COMMENT '사유 파생 스냅샷 — 브랜드 몫 지급 정지 여부',
    `suspended_at`      DATETIME(6)  NOT NULL,
    `suspended_by`      BIGINT       NOT NULL COMMENT '운영자 seller_id',
    `suspended_by_name` VARCHAR(64)  NOT NULL COMMENT '이름 스냅샷',
    `suspend_card_id`   BIGINT       NULL     COMMENT '운영팀 채널 「계정 정지」 카드 message_id',
    `released_at`       DATETIME(6)  NULL,
    `released_by`       BIGINT       NULL     COMMENT 'SYSTEM 종결(탈퇴)은 NULL',
    `released_by_name`  VARCHAR(64)  NULL,
    `release_reason`    VARCHAR(40)  NULL     COMMENT 'SanctionReleaseReason',
    `release_note`      VARCHAR(500) NULL,
    `release_card_id`   BIGINT       NULL,
    `active_key`        BIGINT GENERATED ALWAYS AS (IF(`released_at` IS NULL, `market_id`, NULL)) STORED,
    PRIMARY KEY (`sanction_id`),
    UNIQUE KEY `uk_brand_sanction_active` (`active_key`),
    KEY `idx_brand_sanction_market` (`market_id`, `suspended_at`),
    CONSTRAINT `fk_brand_sanction_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-3. 탈퇴 신청 1건 = 1행 · 반려 · 취소 뒤 재신청은 새 행. 열린 신청 = status 'PENDING'.
CREATE TABLE `brand_withdrawal_request` (
    `request_id`        BIGINT        NOT NULL AUTO_INCREMENT,
    `market_id`         BIGINT        NOT NULL,
    `status`            VARCHAR(20)   NOT NULL COMMENT 'PENDING, APPROVED, REJECTED, CANCELED',
    `reason`            VARCHAR(40)   NULL     COMMENT 'BrandWithdrawalReason — 선택하지 않음 = NULL',
    `message`           VARCHAR(1000) NULL     COMMENT '남기실 말씀',
    `requested_at`      DATETIME(6)   NOT NULL,
    `requester_name`    VARCHAR(64)   NOT NULL COMMENT '신청 시점 담당자명 — 파기 뒤에도 남는다',
    `processed_at`      DATETIME(6)   NULL,
    `processed_by`      BIGINT        NULL     COMMENT '운영자 seller_id',
    `processed_by_name` VARCHAR(64)   NULL,
    `reject_reason`     VARCHAR(40)   NULL     COMMENT 'BrandWithdrawalRejectReason',
    `reject_note`       VARCHAR(500)  NULL     COMMENT '브랜드에 전달할 안내',
    `canceled_at`       DATETIME(6)   NULL,
    `pending_key`       BIGINT GENERATED ALWAYS AS (IF(`status` = 'PENDING', `market_id`, NULL)) STORED,
    PRIMARY KEY (`request_id`),
    UNIQUE KEY `uk_brand_withdrawal_pending` (`pending_key`),
    KEY `idx_brand_withdrawal_market` (`market_id`, `requested_at`),
    CONSTRAINT `fk_brand_withdrawal_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-4. 관리자 메모 — 누적 · 300자 · soft delete. 기존 market.admin_memo(기획 제외 컨트롤러)는 건드리지 않는다.
CREATE TABLE `brand_admin_memo` (
    `memo_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `market_id`   BIGINT       NOT NULL,
    `author_id`   BIGINT       NOT NULL COMMENT '운영자 seller_id',
    `author_name` VARCHAR(64)  NOT NULL,
    `content`     VARCHAR(300) NOT NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    `modified_at` DATETIME(6)  NULL,
    `deleted_at`  DATETIME(6)  NULL,
    PRIMARY KEY (`memo_id`),
    KEY `idx_brand_admin_memo_market` (`market_id`, `created_at`),
    CONSTRAINT `fk_brand_admin_memo_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-4. 처리 이력 — 이 모듈의 사건만. 가입 · 입점 승인 · 변경 요청은 읽을 때 합친다. 파기 대상 아님.
CREATE TABLE `brand_admin_history` (
    `history_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `market_id`  BIGINT       NOT NULL,
    `event_type` VARCHAR(40)  NOT NULL COMMENT 'BrandAdminEventType(저장 9종)',
    `actor_type` VARCHAR(16)  NOT NULL COMMENT 'ADMIN, BRAND, SYSTEM',
    `actor_id`   BIGINT       NULL,
    `actor_name` VARCHAR(64)  NULL     COMMENT '이름 스냅샷',
    `detail`     VARCHAR(500) NULL,
    `ref_type`   VARCHAR(32)  NULL,
    `ref_id`     BIGINT       NULL,
    `created_at` DATETIME(6)  NOT NULL,
    PRIMARY KEY (`history_id`),
    KEY `idx_brand_admin_history_market` (`market_id`, `created_at`),
    CONSTRAINT `fk_brand_admin_history_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-5. 발송 기한 N 변경 이력 — 값이 바뀔 때만. 어드민 상세 보조줄 「08.20 2→3 변경」.
CREATE TABLE `market_ship_lead_history` (
    `history_id`    BIGINT      NOT NULL AUTO_INCREMENT,
    `market_id`     BIGINT      NOT NULL,
    `previous_days` INT         NULL COMMENT '최초 설정이면 NULL',
    `new_days`      INT         NOT NULL,
    `changed_at`    DATETIME(6) NOT NULL,
    PRIMARY KEY (`history_id`),
    KEY `idx_market_ship_lead_history_market` (`market_id`, `changed_at`),
    CONSTRAINT `fk_market_ship_lead_history_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
