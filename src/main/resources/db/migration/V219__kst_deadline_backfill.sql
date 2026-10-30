-- KST 기한 백필(58 계획 1-2 · D2 · 3-3).
--
-- 영업일 기한은 「한국 날짜의 23:59:59」인데, 운영 JVM 이 UTC 라 서버 날짜에 23:59:59 를 붙여 저장했다 — 서버 시각(UTC)으로 9시간 늦다.
-- 코드(KstDates.endOfDay)가 이 배포부터 맞는 값(한국 23:59:59 = UTC 14:59:59)을 저장하므로, 이미 저장된 열린 기한만 같은 순간으로 옮긴다.
--
-- 대상: 아직 지나지 않았고(옛 값 기준) 시각이 23:59:59 인 기한 — FE 가 UTC 로 보낸 직권 중단 기한 · 집행 시각은 23:59:59 로 끝나지 않아 걸리지
-- 않는다. 정산 조정 D-1 통지(한국 10:00)는 아직 보내지 않은 것만 옮긴다.
--
-- DB 세션 시간대가 UTC 일 때만 돈다(TIMESTAMPDIFF = 0) — 운영 · 개발 서버(docker TZ=UTC)만 해당한다. KST 로 도는 로컬 DB 에 저장된 값은
-- 이미 한국 시각이라 옮기면 안 된다. 코드 배포와 같은 배포에 나가야 한다(먼저 나가면 옛 코드가 새 기한을 다시 9시간 밀어 저장한다).

UPDATE order_delivery_group SET ship_due_at = ship_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(ship_due_at) = '23:59:59' AND ship_due_at > UTC_TIMESTAMP();

UPDATE order_claim SET collect_due_at = collect_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(collect_due_at) = '23:59:59' AND collect_due_at > UTC_TIMESTAMP();

UPDATE order_claim SET inspect_due_at = inspect_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(inspect_due_at) = '23:59:59' AND inspect_due_at > UTC_TIMESTAMP();

UPDATE order_claim_collection SET invoice_due_at = invoice_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(invoice_due_at) = '23:59:59' AND invoice_due_at > UTC_TIMESTAMP();

UPDATE order_claim_charge SET due_at = due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(due_at) = '23:59:59' AND due_at > UTC_TIMESTAMP();

UPDATE order_cancel_request SET respond_due_at = respond_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(respond_due_at) = '23:59:59' AND respond_due_at > UTC_TIMESTAMP();

UPDATE brand_sanction SET appeal_deadline_at = appeal_deadline_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(appeal_deadline_at) = '23:59:59' AND appeal_deadline_at > UTC_TIMESTAMP();

UPDATE creator_sanction SET appeal_deadline_at = appeal_deadline_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(appeal_deadline_at) = '23:59:59' AND appeal_deadline_at > UTC_TIMESTAMP();

UPDATE user_sanction SET appeal_deadline_at = appeal_deadline_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(appeal_deadline_at) = '23:59:59' AND appeal_deadline_at > UTC_TIMESTAMP();

UPDATE group_buy_admin_suspension SET appeal_deadline_at = appeal_deadline_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(appeal_deadline_at) = '23:59:59' AND appeal_deadline_at > UTC_TIMESTAMP();

UPDATE settlement SET review_due_at = review_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(review_due_at) = '23:59:59' AND review_due_at > UTC_TIMESTAMP();

UPDATE settlement_adjustment SET deadline_at = deadline_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(deadline_at) = '23:59:59' AND deadline_at > UTC_TIMESTAMP();

UPDATE settlement_adjustment SET notice_due_at = notice_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND notice_sent_at IS NULL AND TIME(notice_due_at) = '10:00:00' AND notice_due_at > UTC_TIMESTAMP();

UPDATE notification SET todo_due_at = todo_due_at - INTERVAL 9 HOUR
 WHERE TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) = 0
   AND TIME(todo_due_at) = '23:59:59' AND todo_due_at > UTC_TIMESTAMP();
