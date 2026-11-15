-- 인플루언서 입점 반려 정보 14일 보관 후 파기(수정요청 B-8 · 확정 정책).
--
-- 반려 즉시 파기하던 것을 14일 보관 뒤 배치가 파기하도록 바꾼다. 파기 예정 시각 · 파기 시각을 남긴다.
-- 기존 반려 행은 이미 즉시 파기됐으므로 파기 시각 = 처리 시각으로 채운다(배치가 다시 집지 않는다).
ALTER TABLE creator_application
    ADD COLUMN personal_data_purge_due_at DATETIME(6) NULL COMMENT '반려 정보 파기 예정 — 반려 처리 + 14일' AFTER notice_mail_failed_at,
    ADD COLUMN personal_data_purged_at    DATETIME(6) NULL COMMENT '반려 정보 파기 시각 — 실명 · 생년월일 · 팔로워 수 · 업무 이메일 삭제 · 연락처 해시' AFTER personal_data_purge_due_at;

UPDATE creator_application
SET personal_data_purged_at = COALESCE(processed_at, modified_at, created_at)
WHERE status = 'REJECTED' AND personal_data_purged_at IS NULL;

CREATE INDEX idx_creator_application_purge_due ON creator_application (status, personal_data_purged_at, personal_data_purge_due_at);
