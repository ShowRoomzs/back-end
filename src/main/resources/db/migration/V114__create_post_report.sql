-- 게시물 신고 접수 — 소비자 → 운영자 (C4 게시물 헤더 ⋯ · C4 하단 고지 "게시물 신고").
--
-- V113이 만든 post 계열에서 유일하게 비어 있던 자리다. 운영자 조치(post_suspension)와
-- 이의 신청(post_appeal)은 그때 만들었지만 조치의 *진입*이 없어 운영자가 게시물을 직접 찾아야 했다.
--
-- reason_code를 post_suspension.reason_code와 같은 코드 축으로 둔다(VARCHAR(40) 동일).
-- 신고를 받아 내리는 순간 사유가 그대로 이어져야 사유별 조치 건수를 한 번에 셀 수 있다.
--
-- uk_post_report가 "사람당 게시물당 1회"를 DB로 강제한다. 서비스 검증만 두면 동시 요청에서 뚫리고,
-- 그러면 신고 건수가 "몇 명이 문제라고 봤는가"를 뜻하지 않게 되어 운영자가 우선순위를 매길 수 없다.
--
-- user_id에 FK를 건다 — post_notification_log와 달리 이 행은 게시물과 함께 파기된다(§24-6).
-- 삭제 사실 자체의 영구 보존은 알림 이력이 맡고, 신고 원문까지 영구 보존할 근거는 없다.

CREATE TABLE `post_report` (
  `post_report_id` BIGINT       NOT NULL AUTO_INCREMENT,
  `post_id`        BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL COMMENT '신고자 — 어드민 응답에도 싣지 않는다. 허위 신고 반복 판단에만 쓴다',
  `reason_code`    VARCHAR(40)  NOT NULL COMMENT 'AD_DISCLOSURE / MEDICAL_CLAIM / ... — post_suspension.reason_code와 같은 축',
  `reason_detail`  VARCHAR(500) NULL COMMENT '기타(OTHER)를 고르면 필수',
  `status`         VARCHAR(20)  NOT NULL COMMENT 'PENDING / ACCEPTED / DISMISSED',
  `reported_at`    DATETIME(6)  NOT NULL,
  `handled_at`     DATETIME(6)  NULL,
  `handled_by`     BIGINT       NULL COMMENT '처리자(운영자) — 처리 시점 고정',
  PRIMARY KEY (`post_report_id`),
  UNIQUE KEY `uk_post_report` (`post_id`, `user_id`),
  KEY `idx_post_report_status_time` (`status`, `reported_at`),
  KEY `idx_post_report_post` (`post_id`, `status`),
  CONSTRAINT `fk_post_report_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
  CONSTRAINT `fk_post_report_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
