-- 결제 운영 지표 5종(결제 기능 구현 계획서 4-8). PaymentHealthScheduler 가 10분마다 같은 식을 돌려 임계값을 넘으면 Sentry warning 을 올린다.
-- 다섯 개가 0이면 결제 시스템이 스스로 수렴하고 있다는 뜻이고, 하나라도 0이 아니면 그 줄이 운영자가 오늘 볼 곳이다.

-- ① 취소 미수렴 — CANCEL_REQUESTED 로 30분 넘게 머문 결제 (임계값 1건)
SELECT payment_id, order_id, amount, cancel_requested_at, cancel_attempts, next_cancel_retry_at, mismatch_reason
  FROM payment
 WHERE status = 'CANCEL_REQUESTED'
   AND cancel_requested_at < NOW(6) - INTERVAL 30 MINUTE
 ORDER BY cancel_requested_at;

-- ② 운영자 처리 대기 — 시스템이 돌려주지 못한 돈 (임계값 1건, 매 회차 반복 경고)
--    PG 콘솔에서 취소한 뒤 payment.status 를 CANCELLED_MISMATCH(자동 취소) 또는 CANCELLED(사용자 취소)로 닫고 payment_cancel 에 완료를 남긴다.
SELECT p.payment_id, p.order_id, p.amount, p.mismatch_reason, p.cancel_requested_at, c.reason, c.raw_response
  FROM payment p
  LEFT JOIN payment_cancel c ON c.payment_id = p.payment_id AND c.status = 'FAILED'
 WHERE p.status = 'CANCEL_FAILED'
 ORDER BY p.cancel_requested_at;

-- ③ 자동 취소 발생 — 최근 24시간 CANCELLED_MISMATCH (임계값 3건 — 1~2건은 만료 직후 결제 등 정상 범위)
SELECT payment_id, order_id, amount, mismatch_reason, modified_at
  FROM payment
 WHERE status = 'CANCELLED_MISMATCH'
   AND modified_at >= NOW(6) - INTERVAL 24 HOUR
 ORDER BY modified_at DESC;

-- ④ 웹훅 처리 실패 — 3회 이상 실패한 이벤트 (임계값 1건). 포트원 재시도 상한 뒤엔 여기서 본다.
SELECT webhook_id, payment_id, event_type, attempts, received_at, error_message
  FROM payment_webhook_event
 WHERE result = 'FAILED' AND attempts >= 3
 ORDER BY received_at DESC;

-- ⑤ 대사 미해결 — 사람이 봐야 하는 어긋남 (임계값 1건). 해결하면 resolved_at · resolution_note 를 채운다.
SELECT issue_id, payment_id, kind, portone_status, portone_amount, our_status, our_amount, detected_at
  FROM payment_reconciliation_issue
 WHERE resolved_at IS NULL
 ORDER BY detected_at;

-- 참고 — 결제 대기 예약 수량(옵션별). 셀러 상품 관리의 재고 저장은 「입력값 − 이 값」, 조회는 「저장값 + 이 값」이다(선행 수정 계획서 3-8).
SELECT op.variant_id, SUM(op.quantity) AS reserved
  FROM order_product op
  JOIN orders o ON o.order_id = op.order_id
 WHERE o.status = 'PAYMENT_PENDING' AND o.stock_released_at IS NULL
 GROUP BY op.variant_id;
