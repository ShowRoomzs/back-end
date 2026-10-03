-- V151 누락 보정 — OrderRefundTask 는 BaseTimeEntity(created_at + modified_at)다.
-- V151 이 이미 적용된 환경이 있어 수정 대신 추가 마이그레이션으로 간다.
ALTER TABLE `order_refund_task`
    ADD COLUMN `modified_at` DATETIME(6) NOT NULL AFTER `created_at`;
