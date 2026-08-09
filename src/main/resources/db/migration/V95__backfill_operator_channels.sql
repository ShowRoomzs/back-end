-- 운영자 고정 채널(§13-3·§14-6) 백필 — 이미 입점 승인된 브랜드와 등록을 마친 쇼룸에도
-- OPERATOR_MARKET/OPERATOR_CREATOR 연결·스레드를 만들어 준다.
-- 신규 가입 건은 승인/등록완료 훅(OperatorChannelService)이 같은 결과를 만든다.
--
-- 모든 구문에 NOT EXISTS 가드를 둔 이유: 훅이 먼저 만들어 둔 채널과 겹쳐도 중복이 생기지 않게 하기 위함이다.
-- 브랜드 채널은 [2026.08 변경] 첫 안내 메시지 없이 빈 스레드로 연다(최초 문구가 가입 시점과
-- 무관한 고정값이라 오해를 유발해 제거). 쇼룸(크리에이터) 채널만 안내 메시지를 남긴다.
-- 안내 메시지의 CLIENT_MESSAGE_ID('operator-welcome')는 애플리케이션 상수와 반드시 같아야 한다
-- (UNIQUE(thread_id, client_message_id)가 스레드당 1건을 보장하는 근거).

-- 1) 운영자↔브랜드 연결 — 승인 완료 + 탈퇴하지 않은 마켓
INSERT INTO `connection` (`type`, `market_id`, `creator_id`, `status`, `requested_at`, `responded_at`, `created_at`, `modified_at`)
SELECT 'OPERATOR_MARKET', m.`market_id`, NULL, 'CONNECTED', NOW(6), NOW(6), NOW(6), NOW(6)
FROM `market` m
JOIN `seller` s ON s.`seller_id` = m.`seller_id`
WHERE s.`status` = 'APPROVED'
  AND m.`status` <> 'WITHDRAWN'
  AND NOT EXISTS (
      SELECT 1 FROM `connection` c
      WHERE c.`type` = 'OPERATOR_MARKET' AND c.`market_id` = m.`market_id`
  );

-- 2) 운영자↔쇼룸 연결 — 등록 완료(쇼룸명 확정) + 탈퇴하지 않은 크리에이터
INSERT INTO `connection` (`type`, `market_id`, `creator_id`, `status`, `requested_at`, `responded_at`, `created_at`, `modified_at`)
SELECT 'OPERATOR_CREATOR', NULL, cr.`creator_id`, 'CONNECTED', NOW(6), NOW(6), NOW(6), NOW(6)
FROM `creator` cr
JOIN `users` u ON u.`user_id` = cr.`user_id`
WHERE cr.`showroom_name` IS NOT NULL
  AND cr.`showroom_name` <> ''
  AND u.`status` <> 'WITHDRAWN'
  AND NOT EXISTS (
      SELECT 1 FROM `connection` c
      WHERE c.`type` = 'OPERATOR_CREATOR' AND c.`creator_id` = cr.`creator_id`
  );

-- 3) 스레드 — 운영자 연결은 요청·수락 단계가 없으므로 생성과 동시에 OPEN이다(§1-3)
INSERT INTO `message_thread` (`connection_id`, `status`, `last_message_at`, `last_message_preview`, `created_at`, `modified_at`)
SELECT c.`connection_id`, 'OPEN', NULL, NULL, NOW(6), NOW(6)
FROM `connection` c
WHERE c.`type` IN ('OPERATOR_MARKET', 'OPERATOR_CREATOR')
  AND NOT EXISTS (
      SELECT 1 FROM `message_thread` t WHERE t.`connection_id` = c.`connection_id`
  );

-- 4) 첫 안내 메시지 — 쇼룸(크리에이터) 채널만 남긴다(브랜드 채널은 빈 스레드).
-- SENDER_ID=0은 개별 어드민이 아닌 "SHOWROOMZ 운영팀"을 뜻하는 시스템 값
INSERT INTO `message` (`thread_id`, `sender_type`, `sender_id`, `content`, `client_message_id`, `created_at`, `modified_at`)
SELECT t.`thread_id`, 'ADMIN', 0,
       CONCAT('안녕하세요, ', cr.`showroom_name`,
              '님. 아직 연결된 브랜드가 없네요. 브랜드가 연결 요청을 보내면 [요청함] 탭에서 확인하실 수 있어요.'),
       'operator-welcome', NOW(6), NOW(6)
FROM `message_thread` t
JOIN `connection` c ON c.`connection_id` = t.`connection_id`
JOIN `creator` cr ON cr.`creator_id` = c.`creator_id`
WHERE c.`type` = 'OPERATOR_CREATOR'
  AND NOT EXISTS (
      SELECT 1 FROM `message` msg
      WHERE msg.`thread_id` = t.`thread_id` AND msg.`client_message_id` = 'operator-welcome'
  );

-- 5) 목록 미리보기·정렬 키 반영 — 스레드만 있고 미리보기가 비면 목록에서 빈 줄로 보인다
UPDATE `message_thread` t
JOIN `connection` c ON c.`connection_id` = t.`connection_id`
JOIN `message` msg ON msg.`thread_id` = t.`thread_id` AND msg.`client_message_id` = 'operator-welcome'
SET t.`last_message_at` = msg.`created_at`,
    t.`last_message_preview` = LEFT(msg.`content`, 255),
    t.`modified_at` = NOW(6)
WHERE c.`type` = 'OPERATOR_CREATOR'
  AND t.`last_message_at` IS NULL;
