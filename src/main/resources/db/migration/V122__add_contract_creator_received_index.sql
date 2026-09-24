-- §27 쇼룸 스튜디오 계약 관리 — 「받은 순」 정렬 인덱스(설계서 0-6).
--
-- 스튜디오 목록의 기본 정렬은 「받은 순」이고(시안 S1) 그 컬럼은 signature_requested_at이다.
-- V116이 판 idx_contract_creator_status_deadline은 「서명 기한순」 전용이라 이 정렬을 타지 못한다 —
-- (creator_id, status, signature_deadline_at)의 두 번째 키가 status라서 status 조건 없는
-- 「전체」 탭의 received 정렬에서는 선두 컬럼만 쓰고 filesort로 떨어진다.
--
-- 「받은 일시」를 위한 별도 컬럼은 만들지 않는다. 서명 요청 발송 시각이 곧 도착 시각이고,
-- 두 컬럼을 두면 같은 사실이 두 곳에 기록되어 어긋날 자리가 생긴다(설계서 0-6).
ALTER TABLE `contract`
    ADD KEY `idx_contract_creator_received` (`creator_id`, `signature_requested_at` DESC);
