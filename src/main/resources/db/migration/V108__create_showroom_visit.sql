-- §22-4 쇼룸 현황 — 쇼룸 도달·유입 경로·팔로워 행동 지표의 원천 로그.
--
-- 행 1건 = 순방문 1회다. §22-4의 "순방문 = 방문 횟수, 같은 소비자의 재방문은 30분 세션 기준 1회"를
-- 집계가 아니라 적재 시점에 적용한다 — 30분 안에 같은 visitor_key가 다시 들어오면 새 행을 만들지 않는다.
-- 따라서 COUNT(*) = 순방문, COUNT(DISTINCT visitor_key) = 방문자 수(중복 제거한 사람 수)가 된다.
--
-- visitor_key — 로그인 방문은 `u:{user_id}`, 비로그인 방문은 클라이언트가 보낸 디바이스 식별자다.
-- user_id는 로그인 방문에만 채워지며, 팔로워 행동(재방문율·방문자 중 팔로워 비중) 집계에 쓴다.
--
-- source — §22-5 전제: 쇼룸 링크의 소스 값(`?from=ig`)과 앱 딥링크의 소스 보존 규칙이 확정되어야
-- 신뢰할 수 있다. 규칙이 없으면 대부분이 DIRECT로 뭉친다는 점을 알고 적재한다.
CREATE TABLE `showroom_visit` (
    `visit_id`     BIGINT NOT NULL AUTO_INCREMENT,
    `creator_id`   BIGINT NOT NULL,
    `user_id`      BIGINT NULL COMMENT '로그인 방문만 — 비로그인은 NULL',
    `visitor_key`  VARCHAR(64) NOT NULL COMMENT '사람 단위 식별자 — 로그인은 u:{user_id}, 비로그인은 디바이스 식별자',
    `source`       VARCHAR(20) NOT NULL COMMENT 'INSTAGRAM_LINK, APP_SEARCH, GROUP_BUY_POST, DIRECT',
    `visited_at`   DATETIME(6) NOT NULL,
    PRIMARY KEY (`visit_id`),
    -- 기간 집계(쇼룸별 + 기간)와 30분 세션 판정(쇼룸별 + 방문자별 최근 방문)에 각각 대응한다.
    KEY `idx_showroom_visit_creator_visited` (`creator_id`, `visited_at`),
    KEY `idx_showroom_visit_session` (`creator_id`, `visitor_key`, `visited_at`),
    KEY `idx_showroom_visit_user` (`creator_id`, `user_id`, `visited_at`),
    CONSTRAINT `fk_showroom_visit_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`),
    CONSTRAINT `fk_showroom_visit_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
