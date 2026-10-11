-- 발송 기한 = 공구 마감 + 브랜드가 정한 N영업일(1009 기획 수정본 1절 · 거래 관리 결정 3 · 15).
--
-- ① 주문마다 적용된 N 을 스냅샷한다 — 브랜드가 뒤에 바꿔도 접수된 주문은 그대로다. 어드민 주문 상세가 이 값을 보여 준다.
--    기존 행은 마켓의 현재 값(없으면 3)으로 채운다 — 운영 데이터는 테스트 결제뿐이다.
-- ② ship_due_at 은 이제 「공구가 종결되기 전에는 NULL」이다(종결 순간 마감 + N영업일로 확정). 기존 값은 그대로 둔다.
-- ③ market.shipping_lead_days 의 뜻이 「출고 소요일(결제 + N일)」에서 「발송 기한(마감 + N영업일 · 1~7 · 기본 3)」으로 바뀐다.
ALTER TABLE order_delivery_group
    ADD COLUMN ship_due_business_days INT NOT NULL DEFAULT 3 COMMENT '이 주문에 적용된 발송 기한 N(영업일) — 주문 시점 스냅샷'
        AFTER ship_due_at;

UPDATE order_delivery_group g
    JOIN market m ON m.market_id = g.market_id
SET g.ship_due_business_days = LEAST(7, GREATEST(1, COALESCE(m.shipping_lead_days, 3)));

UPDATE market SET shipping_lead_days = 3 WHERE shipping_lead_days IS NULL;
UPDATE market SET shipping_lead_days = 7 WHERE shipping_lead_days > 7;
UPDATE market SET shipping_lead_days = 1 WHERE shipping_lead_days < 1;
