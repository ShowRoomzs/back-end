-- 직권 취소 결과 카드가 등록된 운영팀 채널(36 설계 1-4). 요청 경로가 스레드가 아니면 NULL.
ALTER TABLE `contract`
    ADD COLUMN `cancel_request_thread_id` BIGINT NULL AFTER `cancel_requested_at`;
