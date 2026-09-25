-- 기간 연장 요청 — §29-6 「공구당 1회 · 수락되든 거절되든 기회 소진 · 재요청 경로 없음」.
-- UNIQUE(group_buy_id) 하나로 1회 규칙이 끝난다(설계서 1-5). 서비스의 선검사는 친절한 에러를 위한 것이고,
-- 동시 요청은 이 유니크가 떨어뜨린다.
CREATE TABLE `group_buy_extension_request` (
    `extension_request_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `group_buy_id`         BIGINT        NOT NULL,
    `requested_by`         BIGINT        NOT NULL COMMENT '셀러 id',
    `extension_days`       INT           NOT NULL,
    `reason`               VARCHAR(300)  NULL COMMENT '선택(C1)',
    `before_end_at`        DATETIME(6)   NOT NULL COMMENT '요청 시점 종료 예정 스냅샷',
    `after_end_at`         DATETIME(6)   NOT NULL COMMENT '수락 시 새 종료 예정',
    `status`               VARCHAR(16)   NOT NULL COMMENT 'PENDING, ACCEPTED, REJECTED, EXPIRED',
    `requested_at`         DATETIME(6)   NOT NULL,
    `responded_at`         DATETIME(6)   NULL,
    `response_actor_type`  VARCHAR(16)   NULL COMMENT 'CREATOR(수락·명시 거절) / SYSTEM(만료)',
    `reject_reason_code`   VARCHAR(64)   NULL COMMENT '스튜디오 C2 — 선택',
    `reject_memo`          VARCHAR(1000) NULL,
    PRIMARY KEY (`extension_request_id`),
    CONSTRAINT `uk_group_buy_extension_group_buy` UNIQUE (`group_buy_id`),
    KEY `idx_group_buy_extension_status` (`status`),
    CONSTRAINT `fk_group_buy_extension_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`),
    CONSTRAINT `ck_group_buy_extension_days` CHECK (`extension_days` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
