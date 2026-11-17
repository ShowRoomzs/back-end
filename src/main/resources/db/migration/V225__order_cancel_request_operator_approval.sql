-- 취소 요청 자동 승인 폐지(대표 결정 2026-11-10 · 수정요청 A-1).
--
-- 응답 기한이 요청 + 2영업일로 바뀌고, 기한이 지나도 시스템은 승인하지 않는다. 지난 요청은 어드민 예외 관리 「처리 지연」에
-- 올라오고, 운영자가 브랜드에 확인한 뒤 승인(취소 · 환불)한다. 운영자 승인 여부와 브랜드 확인 내용을 남긴다.
-- auto_approved 는 폐지 전 자동 승인된 행의 기록이라 지우지 않는다. 기존 행의 respond_due_at 은 요청 시점 스냅샷이라 고치지 않는다.
ALTER TABLE order_cancel_request
    ADD COLUMN operator_approved BIT(1)       NOT NULL DEFAULT b'0' COMMENT '응답 기한 경과 후 운영자가 브랜드 확인 뒤 승인 — decided_by 는 운영자 id' AFTER auto_approved,
    ADD COLUMN operator_note     VARCHAR(500) NULL COMMENT '운영자 승인 시 브랜드 확인 내용' AFTER operator_approved;
