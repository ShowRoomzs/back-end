-- 재발송 요청을 운영팀 채널의 카드에 잇는다(36 설계 1-3). 알림 전송 = 기존 handled_at / handled_by.
-- 이 마이그레이션 이전의 요청은 카드가 없다(card_message_id NULL) — 계약 관리의 처리 기록 API로 닫는다.
ALTER TABLE `contract_resend_request`
    ADD COLUMN `thread_id` BIGINT NULL COMMENT '카드가 등록된 운영팀 채널',
    ADD COLUMN `card_message_id` BIGINT NULL COMMENT '요청 카드 메시지',
    ADD COLUMN `notice_message_id` BIGINT NULL COMMENT '재발송 완료 자동 안내 말풍선';
