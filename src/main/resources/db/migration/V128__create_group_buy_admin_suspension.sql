-- 직권 중단 통지 · 소명 · 집행(§29-7) — 통지 → 소명 → 집행/철회 3단계와 긴급 예외를 한 행으로 기록한다.
-- EMERGENCY는 NOTICED를 거치지 않고 EXECUTED로 바로 생성된다(설계서 1-7).
CREATE TABLE `group_buy_admin_suspension` (
    `admin_suspension_id`   BIGINT        NOT NULL AUTO_INCREMENT,
    `group_buy_id`          BIGINT        NOT NULL,
    `kind`                  VARCHAR(16)   NOT NULL COMMENT 'NOTICE(사전 통지) / EMERGENCY(제17조③ 즉시 집행)',
    `reason_clause`         VARCHAR(32)   NULL COMMENT 'NOTICE: 제17조① 1~4호 고정',
    `emergency_reason`      VARCHAR(32)   NULL COMMENT 'EMERGENCY: 제17조③ 3종 고정',
    `notice_body`           VARCHAR(2000) NOT NULL COMMENT '브랜드에 그대로 노출',
    `noticed_at`            DATETIME(6)   NOT NULL,
    `noticed_by`            BIGINT        NOT NULL,
    `execute_scheduled_at`  DATETIME(6)   NULL COMMENT '집행 예정 — 통지일 +3영업일 이후',
    `appeal_deadline_at`    DATETIME(6)   NULL COMMENT '소명 기한 — 수신일 +3영업일(제17조④)',
    `status`                VARCHAR(16)   NOT NULL COMMENT 'NOTICED, WITHDRAWN, EXECUTED, LAPSED',
    `appeal_content`        VARCHAR(2000) NULL COMMENT '브랜드 소명 — 제출 후 수정 불가',
    `appeal_submitted_at`   DATETIME(6)   NULL,
    `appeal_submitted_by`   BIGINT        NULL,
    `withdrawn_at`          DATETIME(6)   NULL,
    `withdrawn_by`          BIGINT        NULL,
    `withdraw_reason`       VARCHAR(1000) NULL,
    `executed_at`           DATETIME(6)   NULL,
    `executed_by`           BIGINT        NULL,
    `active_group_buy_id`   BIGINT GENERATED ALWAYS AS (IF(`status` = 'NOTICED', `group_buy_id`, NULL)) STORED,
    PRIMARY KEY (`admin_suspension_id`),
    CONSTRAINT `uk_group_buy_admin_suspension_active` UNIQUE (`active_group_buy_id`),
    KEY `idx_group_buy_admin_suspension_group_buy` (`group_buy_id`, `noticed_at`),
    KEY `idx_group_buy_admin_suspension_status_deadline` (`status`, `appeal_deadline_at`),
    CONSTRAINT `fk_group_buy_admin_suspension_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소명 증빙 — message_attachment와 같은 presign → HeadObject 검증 흐름(설계서 1-7).
CREATE TABLE `group_buy_appeal_attachment` (
    `attachment_id`        BIGINT        NOT NULL AUTO_INCREMENT,
    `admin_suspension_id`  BIGINT        NOT NULL,
    `uploader_id`          BIGINT        NOT NULL COMMENT '셀러 id',
    `s3_key`               VARCHAR(512)  NOT NULL,
    `original_name`        VARCHAR(255)  NOT NULL,
    `content_type`         VARCHAR(128)  NOT NULL COMMENT 'image/png, image/jpeg, application/pdf',
    `size_bytes`           BIGINT        NOT NULL COMMENT '10MB 이하',
    `status`               VARCHAR(20)   NOT NULL COMMENT 'PENDING, UPLOADED, REJECTED',
    `uploaded_at`          DATETIME(6)   NULL,
    `created_at`           DATETIME(6)   NOT NULL,
    PRIMARY KEY (`attachment_id`),
    KEY `idx_group_buy_appeal_attachment_suspension` (`admin_suspension_id`),
    KEY `idx_group_buy_appeal_attachment_status_created` (`status`, `created_at`),
    CONSTRAINT `fk_group_buy_appeal_attachment_suspension` FOREIGN KEY (`admin_suspension_id`)
        REFERENCES `group_buy_admin_suspension` (`admin_suspension_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
