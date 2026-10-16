-- 54_어드민_일반게시물_모니터링_BE_설계서.md 2-3 — 반려 유예 중 작성자의 사진 원본 내려받기 기록.
--
-- C8 삭제 경위의 「작성자 내려받기 1회(08.10 21:14)」가 이 행이다. 「내 사진을 돌려 달라」 문의의 답이 된다.
-- URL 발급 기록이지 실제 전송 성공 기록은 아니다(S3 URL을 받고 안 받을 수 있다).
--
-- post_id에 FK를 걸지 않는다 — 게시물이 보관 만료로 파기된 뒤에도 경위는 남아야 한다(post_notification_log와 같은 규칙).
-- 파기 배치(PostPurgeService)는 이 테이블을 건드리지 않는다.

CREATE TABLE `post_original_download` (
  `download_id`   BIGINT      NOT NULL AUTO_INCREMENT,
  `post_id`       BIGINT      NOT NULL COMMENT 'FK 없음 — 파기 뒤에도 남는다',
  `creator_id`    BIGINT      NOT NULL,
  `appeal_id`     BIGINT      NULL COMMENT '어느 반려의 유예 중 내려받았는지',
  `image_count`   INT         NOT NULL,
  `downloaded_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`download_id`),
  KEY `idx_post_original_download_post` (`post_id`, `downloaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
