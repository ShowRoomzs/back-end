-- PG 즉시 자동 환불(1009 기획 수정본 2절 · 거래 관리 결정 1).
--
-- 환불 큐(order_refund_task)를 비우는 주체가 어드민에서 시스템으로 옮긴다 — 취소 요청 승인 · 직권 취소 · 반품 검수 통과 · 반송 완료는
-- 커밋 직후 포트원 부분 취소로 돌려주고, 운영자 사유 환불(반려 이의 인용 · 구매확정 후 하자 · 위해성 리콜)만 운영자가 집행한다.
-- 한 결제에 하위주문 · 항목이 여럿이라 부분 취소가 기본이다 — 결제에 누적 취소액을 둔다.
ALTER TABLE order_refund_task
    ADD COLUMN origin        VARCHAR(16)  NOT NULL DEFAULT 'PG_AUTO' COMMENT 'PG_AUTO, OPERATOR — 어드민 환불 관리 「출처」' AFTER status,
    ADD COLUMN payment_id    VARCHAR(64)  NULL COMMENT '취소할 결제 — 적재 시점의 orders.paid_payment_id' AFTER origin,
    ADD COLUMN reason_code   VARCHAR(30)  NULL COMMENT '운영자 사유 환불 — DISPUTE_ACCEPTED, POST_CONFIRM_DEFECT, RECALL' AFTER payment_id,
    ADD COLUMN reason_detail VARCHAR(500) NULL AFTER reason_code,
    ADD COLUMN requested_by  BIGINT       NULL COMMENT '운영자 사유 환불을 편입한 운영자' AFTER reason_detail,
    ADD COLUMN attempt       INT          NOT NULL DEFAULT 0 COMMENT '집행 시도 횟수' AFTER requested_by,
    ADD COLUMN last_error    VARCHAR(500) NULL COMMENT '마지막 실패 사유' AFTER attempt,
    MODIFY COLUMN status     VARCHAR(16)  NOT NULL COMMENT 'PENDING, EXECUTING, DONE, FAILED, VOID',
    MODIFY COLUMN source     VARCHAR(30)  NOT NULL COMMENT 'CANCEL_REQUEST_APPROVED, SELLER_DIRECT_CANCEL, RETURN_COMPLETED, CLAIM_RETURN_PASSED, OPERATOR_REASON',
    ADD KEY idx_order_refund_task_payment (payment_id, status);

-- 기존 대기 행은 지금 주문 결제로 이어 둔다 — 재시도 배치가 PG 자동으로 집행한다(운영 데이터는 테스트 결제뿐이다).
UPDATE order_refund_task t
    JOIN orders o ON o.order_id = t.order_id
SET t.payment_id = o.paid_payment_id
WHERE t.payment_id IS NULL;

ALTER TABLE payment
    ADD COLUMN cancelled_amount INT NOT NULL DEFAULT 0 COMMENT '부분 취소 누적액 — 환불 큐 집행이 올린다. 결제액에 닿으면 CANCELLED' AFTER amount;

ALTER TABLE payment_cancel
    ADD COLUMN refund_task_id BIGINT NULL COMMENT '환불 큐 집행의 부분 취소면 그 큐 행' AFTER raw_response;
