-- 어드민 회원 관리 — 인플루언서 상세(18) · 인플루언서 제재 · 탈퇴 심사 도메인(53 설계서 1절 · 구현 메모 12절).
-- 브랜드(V199 · 52 설계서)와 같은 골격이다 — 정지 = 행 · 탈퇴 = 신청 행 · 메모 · 이력은 회원 유형별 테이블.
-- 열린 정지 · 열린 신청은 생성 컬럼 UNIQUE 로 회원당 1건(connection_thread_key 와 같은 관례).

-- 1-2. 인플루언서 회원 상태 — 소비자 계정(users.status)과 다른 축이다(0-1).
ALTER TABLE creator
    ADD COLUMN status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'CreatorStatus — ACTIVE, SUSPENDED, WITHDRAWN',
    ADD COLUMN withdrawn_at      DATETIME(6) NULL COMMENT '탈퇴 승인 시각',
    ADD COLUMN withdrawn_by      BIGINT      NULL COMMENT '승인 운영자 seller_id',
    ADD COLUMN privacy_purged_at DATETIME(6) NULL COMMENT '탈퇴 승인 즉시 파기 수행 시각(0-10)',
    ADD KEY idx_creator_status (status);

-- 지금까지는 소비자 계정 상태가 인플루언서도 막았다 — 보수적으로 잇는다(9-2 #1). 사유 · 처리자가 없으므로 정지 행은 만들지 않는다.
UPDATE creator c JOIN users u ON u.user_id = c.user_id
   SET c.status = CASE u.status WHEN 'SUSPENDED' THEN 'SUSPENDED' WHEN 'WITHDRAWN' THEN 'WITHDRAWN' ELSE 'ACTIVE' END,
       c.withdrawn_at = CASE WHEN u.status = 'WITHDRAWN' THEN u.modified_at END;

-- 정지 1건 = 1행 · 해제까지. 열린 정지 = released_at IS NULL(인플루언서당 1건).
CREATE TABLE `creator_sanction` (
    `sanction_id`       BIGINT       NOT NULL AUTO_INCREMENT,
    `creator_id`        BIGINT       NOT NULL,
    `reason`            VARCHAR(40)  NOT NULL COMMENT 'CreatorSuspensionReason',
    `reason_detail`     VARCHAR(500) NULL     COMMENT 'OTHER 면 필수',
    `payout_held`       TINYINT(1)   NOT NULL COMMENT '사유 파생 스냅샷 — 리워드 지급 정지 여부',
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
    `active_key`        BIGINT GENERATED ALWAYS AS (IF(`released_at` IS NULL, `creator_id`, NULL)) STORED,
    PRIMARY KEY (`sanction_id`),
    UNIQUE KEY `uk_creator_sanction_active` (`active_key`),
    KEY `idx_creator_sanction_creator` (`creator_id`, `suspended_at`),
    CONSTRAINT `fk_creator_sanction_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-3. 탈퇴 신청 — 신청 · 반려 이력은 지우지 않는다(재신청 = 새 행). 열린 신청은 인플루언서당 1건.
CREATE TABLE `creator_withdrawal_request` (
    `request_id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `creator_id`             BIGINT       NOT NULL,
    `status`                 VARCHAR(20)  NOT NULL COMMENT 'PENDING, APPROVED, REJECTED, CANCELED',
    `reason_text`            VARCHAR(500) NOT NULL COMMENT '스튜디오 S6 사유(필수 · 선택지 미정의 §55 B-13)',
    `requested_at`           DATETIME(6)  NOT NULL,
    `due_date`               DATE         NULL     COMMENT '승인 기한 안내 — 신청일 + 5영업일(스튜디오 표시용)',
    `canceled_at`            DATETIME(6)  NULL,
    `processed_at`           DATETIME(6)  NULL,
    `processed_by`           BIGINT       NULL     COMMENT '운영자 seller_id',
    `processed_by_name`      VARCHAR(64)  NULL,
    `reject_reason`          VARCHAR(40)  NULL     COMMENT 'CreatorWithdrawalRejectReason',
    `reject_note`            VARCHAR(500) NULL     COMMENT '인플루언서에게 전달할 안내',
    `approved_group_buy_count` INT        NULL     COMMENT '승인 시점 재검증 스냅샷 — 전부 0',
    `approved_contract_count`  INT        NULL,
    `approved_unsettled_count` INT        NULL,
    `version`                BIGINT       NOT NULL DEFAULT 0,
    `pending_key`            BIGINT GENERATED ALWAYS AS (IF(`status` = 'PENDING', `creator_id`, NULL)) STORED,
    PRIMARY KEY (`request_id`),
    UNIQUE KEY `uk_creator_withdrawal_pending` (`pending_key`),
    KEY `idx_creator_withdrawal_creator` (`creator_id`, `requested_at`),
    CONSTRAINT `fk_creator_withdrawal_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-5. 관리자 메모 — 누적 · 300자 · 작성자 24시간 · soft delete.
CREATE TABLE `creator_admin_memo` (
    `memo_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `creator_id`  BIGINT       NOT NULL,
    `author_id`   BIGINT       NOT NULL COMMENT '운영자 seller_id',
    `author_name` VARCHAR(64)  NOT NULL COMMENT '이름 스냅샷',
    `content`     VARCHAR(300) NOT NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    `modified_at` DATETIME(6)  NULL,
    `deleted_at`  DATETIME(6)  NULL,
    PRIMARY KEY (`memo_id`),
    KEY `idx_creator_admin_memo_creator` (`creator_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-6. 처리 이력 — 이 모듈이 일으킨 사건만 저장한다. 가입 신청 · 입점 승인은 creator_application 에서 읽어 합친다. 파기 대상 아님.
CREATE TABLE `creator_admin_history` (
    `history_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `creator_id`  BIGINT       NOT NULL,
    `event_type`  VARCHAR(40)  NOT NULL COMMENT 'CreatorAdminEventType',
    `actor_type`  VARCHAR(16)  NOT NULL COMMENT 'ADMIN, CREATOR, SYSTEM',
    `actor_id`    BIGINT       NULL,
    `actor_name`  VARCHAR(64)  NULL     COMMENT '이름 스냅샷',
    `detail`      VARCHAR(500) NULL,
    `ref_type`    VARCHAR(32)  NULL,
    `ref_id`      BIGINT       NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    PRIMARY KEY (`history_id`),
    KEY `idx_creator_admin_history_creator` (`creator_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-7. 개인정보 열람 로그 — 사유 정형 4종 · 상세 사유 없음 · 보존 기간 미정이라 삭제 배치 없음.
CREATE TABLE `creator_privacy_access_log` (
    `log_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `creator_id`  BIGINT       NOT NULL,
    `operator_id` BIGINT       NOT NULL,
    `reason`      VARCHAR(40)  NOT NULL COMMENT 'CreatorPrivacyAccessReason',
    `fields`      VARCHAR(255) NOT NULL COMMENT 'NAME,BIRTHDAY,PHONE',
    `accessed_at` DATETIME(6)  NOT NULL,
    `expires_at`  DATETIME(6)  NOT NULL COMMENT 'accessed_at + 10분(고정)',
    `locked_at`   DATETIME(6)  NULL     COMMENT '[지금 잠금]',
    PRIMARY KEY (`log_id`),
    KEY `idx_creator_privacy_creator` (`creator_id`, `accessed_at`),
    KEY `idx_creator_privacy_operator` (`operator_id`, `accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-4. 정산 계좌 변경 이력 — 행 = 바뀐 시점의 이전 · 이후 계좌. 전체 계좌번호는 암호문으로만(응답에 내리지 않는다).
CREATE TABLE `creator_settlement_account_history` (
    `history_id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `creator_id`             BIGINT       NOT NULL,
    `previous_bank_name`     VARCHAR(50)  NULL,
    `previous_last4`         CHAR(4)      NULL,
    `previous_account_enc`   VARCHAR(255) NULL,
    `bank_name`              VARCHAR(50)  NOT NULL,
    `last4`                  CHAR(4)      NULL,
    `account_enc`            VARCHAR(255) NOT NULL,
    `changed_by_type`        VARCHAR(16)  NOT NULL COMMENT 'ADMIN, CREATOR, SYSTEM',
    `changed_by`             BIGINT       NULL,
    `changed_at`             DATETIME(6)  NOT NULL,
    PRIMARY KEY (`history_id`),
    KEY `idx_creator_account_history_creator` (`creator_id`, `changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
