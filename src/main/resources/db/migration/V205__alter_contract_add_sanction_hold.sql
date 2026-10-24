-- 1017 설계서 8-4 — 당사자(브랜드 · 인플루언서) 정지로 인한 계약 보류.
-- 정지되면 그 당사자의 검토 대기 · 서명 진행중 · 체결 처리 대기 계약에 보류 시작 시각을 찍는다(상태는 바꾸지 않는다).
-- 보류 중에는 운영자가 검토 통과 · 체결 완료를 하지 않고 서명 기한이 멈춘다. 해제되면(양측 모두 풀리면) 멈춘 만큼 signature_deadline_at 을 민다.
ALTER TABLE contract
    ADD COLUMN sanction_hold_since DATETIME(6) NULL COMMENT '당사자 정지로 보류가 시작된 시각 — SIGNING 서명 기한이 멈춘 시각' AFTER deleted_at,
    ADD COLUMN sanction_hold_party VARCHAR(16) NULL COMMENT 'BRAND, CREATOR, BOTH — 보류 중인 당사자' AFTER sanction_hold_since;
