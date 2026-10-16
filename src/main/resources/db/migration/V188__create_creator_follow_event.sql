-- 49 스튜디오 성과 관리 설계서 1-6 — 팔로우 · 언팔로우 이력.
--
-- creator_follow 는 현재 관계만 들고 언팔로우하면 행을 지운다. 성과의 「사건 시각 팔로워」 판정과
-- 「공구 기간 팔로워 증감」은 이력이 있어야 셀 수 있다. 판정식 = t 이전 마지막 이벤트가 FOLLOW.
-- 언팔로우 수는 어디에도 노출하지 않는다(§22-4) — 집계 입력일 뿐이다.
CREATE TABLE `creator_follow_event` (
    `event_id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `creator_id`         BIGINT      NOT NULL,
    `user_id`            BIGINT      NOT NULL,
    `event_type`         VARCHAR(10) NOT NULL COMMENT 'FOLLOW, UNFOLLOW',
    `occurred_at`        DATETIME(6) NOT NULL,
    `attributed_post_id` BIGINT      NULL COMMENT 'FOLLOW 만 — creator_follow.attributed_post_id 복사',
    PRIMARY KEY (`event_id`),
    KEY `idx_cfe_creator_time` (`creator_id`, `occurred_at`),
    KEY `idx_cfe_user_creator_time` (`user_id`, `creator_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='팔로우 이력';

-- 백필 — 지금의 팔로우 관계를 FOLLOW 로 옮긴다. 이전 언팔로우 이력은 복구할 수 없다(설계서 7-3).
INSERT INTO `creator_follow_event` (`creator_id`, `user_id`, `event_type`, `occurred_at`, `attributed_post_id`)
SELECT `creator_id`, `user_id`, 'FOLLOW', `created_at`, `attributed_post_id` FROM `creator_follow`;
