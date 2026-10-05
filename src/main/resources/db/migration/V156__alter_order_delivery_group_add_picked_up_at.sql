-- 집화 시각 — 소비자 앱 「도착 예정」(집화일 + N영업일)의 기준이자 택배사별 실제 소요일(집화 → 배송완료) 집계의 원천이다.
-- 추적 배치가 본 첫 이벤트 시각을 1회만 적는다. 송장이 수정되면 NULL 로 돌아간다(새 송장 기준으로 다시 잡는다).
-- 기존 배송중 행은 NULL 로 둔다 — 다음 추적 회차가 채운다.
ALTER TABLE `order_delivery_group`
    ADD COLUMN `picked_up_at` DATETIME(6) NULL AFTER `shipped_at`;
