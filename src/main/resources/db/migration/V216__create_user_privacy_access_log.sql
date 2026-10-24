-- 어드민 소비자 상세 — 본인확인 정보 열람(마스킹 해제) 로그(55 계획서 M3-3 · ui-admin-13 M2).
-- 인플루언서 creator_privacy_access_log(V193)와 같은 열 구성이다 — 1행 = 해제 세션 1개(이 소비자 1건 · 이 운영자 · 10분 고정 만료).
-- 보존 기간 미정(시안 ⚠ 법률 자문 ⓒ)이라 삭제 배치는 두지 않는다. 처리 이력 이벤트 PRIVACY_UNMASKED 는 user_admin_history.event_type
-- VARCHAR(40) 안이라 스키마 변경이 없다.
CREATE TABLE `user_privacy_access_log` (
    `log_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `operator_id` BIGINT       NOT NULL,
    `reason`      VARCHAR(40)  NOT NULL COMMENT 'UserPrivacyAccessReason',
    `fields`      VARCHAR(255) NOT NULL COMMENT 'NAME,BIRTHDAY,PHONE,ADDRESS',
    `accessed_at` DATETIME(6)  NOT NULL,
    `expires_at`  DATETIME(6)  NOT NULL COMMENT 'accessed_at + 10분(고정)',
    `locked_at`   DATETIME(6)  NULL     COMMENT '[지금 잠금]',
    PRIMARY KEY (`log_id`),
    KEY `idx_user_privacy_user` (`user_id`, `accessed_at`),
    KEY `idx_user_privacy_operator` (`operator_id`, `accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
