-- 포트원 V2 본인인증(KCP 휴대폰 본인확인) — 57 설계서 1절.
-- users: CI · DI 암호문 + DI 해시(중복 가입 차단 키 · UNIQUE — MySQL 은 NULL 여러 행을 허용하므로 GUEST · 탈퇴 행과 충돌하지 않는다).
-- identity_verification: 인증 1회 = 1행. 개인정보 열은 Users 로 옮긴 뒤(CONSUMED) 비운다.
ALTER TABLE `users`
    ADD COLUMN `identity_ci_enc`    VARCHAR(512) NULL COMMENT 'CI 암호문(PersonalDataCipher)',
    ADD COLUMN `identity_di_enc`    VARCHAR(512) NULL COMMENT 'DI 암호문',
    ADD COLUMN `identity_di_hash`   CHAR(64)     NULL COMMENT 'DI HMAC-SHA256 hex — 중복 가입 차단 키(활성 회원 1인 1행)',
    ADD COLUMN `identity_operator`  VARCHAR(10)  NULL COMMENT 'SKT·KT·LGU·*_MVNO',
    ADD COLUMN `identity_foreigner` TINYINT(1)   NULL,
    ADD UNIQUE KEY `uk_users_identity_di_hash` (`identity_di_hash`);

CREATE TABLE `identity_verification` (
    `identity_verification_id`    VARCHAR(40)  NOT NULL COMMENT 'idv + ULID — KCP: 영문·숫자 40자 이하',
    `identity_verification_tx_id` VARCHAR(64)  NULL COMMENT 'SDK 응답 identityVerificationTxId(기록용)',
    `user_id`             BIGINT       NOT NULL,
    `purpose`             VARCHAR(10)  NOT NULL COMMENT 'SIGNUP·REVERIFY',
    `status`              VARCHAR(10)  NOT NULL COMMENT 'READY·VERIFIED·FAILED·CONSUMED',
    `channel_key`         VARCHAR(64)  NOT NULL,
    `pg_tx_id`            VARCHAR(64)  NULL,
    `verified_name`       VARCHAR(64)  NULL,
    `verified_birth_date` VARCHAR(10)  NULL,
    `verified_gender`     VARCHAR(10)  NULL,
    `verified_phone`      VARCHAR(20)  NULL,
    `verified_operator`   VARCHAR(10)  NULL,
    `verified_foreigner`  TINYINT(1)   NULL,
    `verified_ci_enc`     VARCHAR(512) NULL,
    `verified_di_enc`     VARCHAR(512) NULL,
    `fail_reason`         VARCHAR(40)  NULL COMMENT 'PG_FAILED·UNDER_MIN_AGE·DUPLICATE_IDENTITY·DI_MISSING·NOT_COMPLETED',
    `pg_code`             VARCHAR(64)  NULL,
    `pg_message`          VARCHAR(255) NULL,
    `requested_at`        DATETIME(6)  NOT NULL,
    `verified_at`         DATETIME(6)  NULL,
    `consumed_at`         DATETIME(6)  NULL,
    `expires_at`          DATETIME(6)  NOT NULL COMMENT 'requested_at + 10분 — 이후 READY·VERIFIED 는 만료로 읽는다',
    PRIMARY KEY (`identity_verification_id`),
    KEY `idx_idv_user` (`user_id`, `requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
