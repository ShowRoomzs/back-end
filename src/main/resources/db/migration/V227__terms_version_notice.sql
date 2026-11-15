-- 약관 개정 고지 기간 검증(수정요청 B-12 · 2026-11-10 확정 정책).
--
-- 개정은 시행 7일 전까지, 이용자에게 불리한 변경은 30일 전까지 고지해야 한다. 고지일은 등록일(소비자 대상은 등록 즉시 알림함 공지)이다.
-- 운영자가 등록 때 불리한 변경 여부를 고르고, 서버가 시행일 - 고지일을 검증한다. 기존 행은 불리 여부를 모르므로 0 · 고지일 NULL 이다.
ALTER TABLE terms_version
    ADD COLUMN unfavorable_change BIT(1) NOT NULL DEFAULT b'0' COMMENT '이용자에게 불리한 변경 — 30일 전 고지(아니면 7일)' AFTER content,
    ADD COLUMN noticed_on         DATE   NULL COMMENT '고지일(KST) — 등록일. 시행일과의 간격을 검증한다' AFTER unfavorable_change;
