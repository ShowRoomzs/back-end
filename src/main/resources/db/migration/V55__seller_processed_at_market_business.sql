-- 가입 신청 처리 일시 (승인/반려 시 어드민이 기록)
alter table seller add column processed_at datetime(6) null;