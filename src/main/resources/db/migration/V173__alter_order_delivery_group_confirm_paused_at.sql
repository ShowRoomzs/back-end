-- 구매확정 타이머 정지 · 재개(1009 기획 수정본 4절 · 거래 관리 결정 7).
--
-- 반품·교환이 접수되면 그 하위주문의 구매확정 타이머가 멈추고(요청 시점부터), 진행 중 클레임이 없어지면(철회 · 자동 취소 · 반려 ·
-- 환불 완료) 정지한 시간만큼 기산점(confirm_restart_at)을 밀어 「남은 일수부터」 다시 센다. 교환이 완료되면 7일을 새로 센다.
-- 지금까지는 「보류 클레임이 있으면 확정하지 않음 → 풀리는 순간 기한이 지났으면 즉시 확정」이었다(정지가 아니라 보류).
ALTER TABLE order_delivery_group
    ADD COLUMN confirm_paused_at DATETIME(6) NULL COMMENT '구매확정 타이머 정지 시각 — 진행 중 반품·교환이 있는 동안' AFTER confirm_restart_at;

-- 지금 진행 중 클레임이 걸린 배송완료 그룹은 그 클레임의 가장 이른 접수 시각부터 멈춘 것으로 본다.
UPDATE order_delivery_group g
    JOIN (SELECT c.delivery_group_id, MIN(c.requested_at) AS paused_at
          FROM order_claim c
          WHERE c.status NOT IN ('COMPLETED', 'PAYMENT_PENDING') AND c.rejected_at IS NULL
          GROUP BY c.delivery_group_id) open_claim ON open_claim.delivery_group_id = g.delivery_group_id
SET g.confirm_paused_at = open_claim.paused_at
WHERE g.fulfillment_status = 'DELIVERED';
