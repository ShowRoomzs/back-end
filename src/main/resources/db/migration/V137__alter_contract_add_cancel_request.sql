-- 운영자 [계약 취소]의 요청자 — 누가 · 어느 경로로 · 언제 취소를 요청했는지 운영자가 취소 모달에서 기록한다(28-1 수정계획 1).
-- 브랜드 계약 취소 기능이 없어 요청은 스레드·전화·메일로 들어오고, 서버가 판정할 근거가 없다.
-- 기존 취소 건은 소급하지 않는다(NULL). requester_type = ADMIN이면 운영자 직권이라 경로·시각이 NULL이다.
ALTER TABLE `contract`
    ADD COLUMN `cancel_requester_type`  VARCHAR(16) NULL COMMENT '취소 요청자 — SELLER · CREATOR · ADMIN(직권)' AFTER `close_reason_memo`,
    ADD COLUMN `cancel_request_channel` VARCHAR(16) NULL COMMENT '취소 요청 경로 — THREAD · PHONE · EMAIL · ETC' AFTER `cancel_requester_type`,
    ADD COLUMN `cancel_requested_at`    DATETIME(6) NULL COMMENT '취소 요청 시각' AFTER `cancel_request_channel`;
