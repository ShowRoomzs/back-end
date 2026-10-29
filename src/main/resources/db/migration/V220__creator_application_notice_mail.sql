-- 입점 반려 통지 메일 결과(58 계획 5-24 · 1021 9절) — 반려 응답 뒤에 열어 보는 상세에서도 발송 성공 · 실패를 알 수 있게.
-- 둘 다 비어 있으면 기록 없음(이 열 이전 반려 · 업무 이메일 없음).
ALTER TABLE creator_application
    ADD COLUMN notice_mail_sent_at   DATETIME(6) NULL COMMENT '반려 통지 메일 발송 성공 시각' AFTER reject_reason_detail,
    ADD COLUMN notice_mail_failed_at DATETIME(6) NULL COMMENT '반려 통지 메일 발송 실패 시각' AFTER notice_mail_sent_at;
