-- 푸시 발송 대상 기기 토큰 (FCM)
--
-- 지금까지 로그인 요청의 fcmToken은 받기만 하고 버려졌다. 발송 인프라가 없어 저장할 곳이 없었기
-- 때문인데, 그 상태로는 팔로워 신규 게시물 알림(§24-3)을 보낼 수단 자체가 없다.
--
-- 유니크를 token에 건다 (user_id + token이 아니다).
--   FCM 토큰은 "기기+앱 설치" 하나를 가리키고 계정과 1:1이 아니다. 한 기기에서 로그아웃 후 다른
--   계정으로 로그인하면 같은 토큰이 새 계정에 붙는데, (user_id, token) 유니크로 두면 두 행이 살아남아
--   이전 사용자에게 갈 알림이 그 기기로 계속 간다. token 유니크 + 로그인 시 소유자 재지정으로
--   "이 기기의 현재 주인은 한 명"을 DB가 보장하게 한다.
--
-- user_id에 FK를 건다. post_notification_log와 반대다 — 이력이 아니라 발송 대상 목록이라
-- 회원이 사라지면 함께 사라지는 것이 맞다(C15-4 탈퇴 시 파기).
--
-- last_used_at은 재로그인마다 갱신한다. FCM은 오래 안 쓴 토큰을 스스로 만료시키므로, 발송
-- 대상에서 미리 걸러내거나 정리 배치를 붙일 때 기준이 된다.
CREATE TABLE `device_token` (
  `device_token_id` BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT       NOT NULL,
  `token`           VARCHAR(512) NOT NULL COMMENT 'FCM 등록 토큰 — 기기+앱 설치 하나를 가리킨다',
  `platform`        VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'ANDROID / IOS / WEB / UNKNOWN',
  `created_at`      DATETIME(6)  NOT NULL,
  `last_used_at`    DATETIME(6)  NOT NULL COMMENT '마지막 로그인 시각 — 만료 토큰 정리 기준',
  PRIMARY KEY (`device_token_id`),
  UNIQUE KEY `uk_device_token_token` (`token`),
  KEY `idx_device_token_user` (`user_id`),
  CONSTRAINT `fk_device_token_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
