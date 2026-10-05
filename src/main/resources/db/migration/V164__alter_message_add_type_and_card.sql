-- 어드민 소통 스레드(36 설계 1-1) — 시스템 카드는 메시지의 한 종류다. 기존 행은 기본값으로 전부 TEXT가 된다.
-- 카드에 박히는 사실(계약번호 · 공구명 · 요청자 · 사유)은 card_payload 스냅샷이고, 액션 상태는 참조 객체에서 읽는다.
ALTER TABLE `message`
    ADD COLUMN `message_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT(말풍선), SYSTEM(카드)' AFTER `sender_id`,
    ADD COLUMN `card_type` VARCHAR(40) NULL COMMENT 'CONTRACT_RESEND_REQUEST, CONTRACT_ADMIN_CANCELED' AFTER `message_type`,
    ADD COLUMN `ref_type` VARCHAR(32) NULL COMMENT 'CONTRACT_RESEND_REQUEST, CONTRACT' AFTER `card_type`,
    ADD COLUMN `ref_id` BIGINT NULL AFTER `ref_type`,
    ADD COLUMN `card_payload` TEXT NULL COMMENT '카드 생성 시점 스냅샷(JSON 문자열)' AFTER `ref_id`,
    ADD COLUMN `auto_notice` BIT(1) NOT NULL DEFAULT b'0' COMMENT '정해진 문구의 자동 전송 말풍선 — 어드민 응답에만 내린다' AFTER `card_payload`;
