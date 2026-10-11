-- 어드민 환불 관리(06c · 39 설계서 1-3 · 42 구현 계획 단계 5).
--
-- 결제 열 「원래 / 추가 · 부분」과 재시도 다이얼로그의 PG 응답 코드를 큐 행에 둔다. 완료 탭 기간이 집행 시각 기준이 되어
-- (status, executed_at) 색인을 함께 둔다. 기록 전용 경로 2종(결제완료 소비자 취소 · 재발송비 결제 취소)이 source 에 더해진다.
-- 컬럼 이름 partial_cancel — PARTIAL 은 MySQL 키워드라 피했다.
ALTER TABLE order_refund_task
    ADD COLUMN payment_kind    VARCHAR(12) NOT NULL DEFAULT 'ORIGINAL' COMMENT 'ORIGINAL, ADDITIONAL — 어드민 06c 결제 열' AFTER payment_id,
    ADD COLUMN partial_cancel  BIT(1)      NOT NULL DEFAULT b'0' COMMENT '원래 결제의 부분 취소' AFTER payment_kind,
    ADD COLUMN last_error_code VARCHAR(50) NULL COMMENT 'PG 응답 코드 — 재시도 다이얼로그(상세에만)' AFTER last_error,
    MODIFY COLUMN source       VARCHAR(30) NOT NULL COMMENT 'CANCEL_REQUEST_APPROVED, SELLER_DIRECT_CANCEL, RETURN_COMPLETED, CLAIM_RETURN_PASSED, OPERATOR_REASON, USER_CANCEL_BEFORE_PREPARE, CLAIM_PAYMENT_CANCELLED',
    ADD KEY idx_order_refund_task_executed (status, executed_at);

-- 기존 행 백필 — 결제액은 바뀌지 않으므로 「지금 결제액 대비」가 곧 「적재 시점 대비」다.
UPDATE order_refund_task t
    JOIN payment p ON p.payment_id = t.payment_id
SET t.partial_cancel = (t.refund_amount < p.amount)
WHERE t.payment_id IS NOT NULL;
