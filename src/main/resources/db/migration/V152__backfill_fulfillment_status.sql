-- 백필(34 설계서 1-11) — 이미 PAID 인 주문의 그룹을 NEW 로 올리고 하위주문번호·발송기한을 채운다.
-- 현존 데이터가 테스트 결제뿐이라 NEW 일괄로 충분하다(구매확정 이행 데이터가 없다).
UPDATE `order_delivery_group` g
  JOIN `orders` o ON o.`order_id` = g.`order_id`
  JOIN `MARKET` m ON m.`MARKET_ID` = g.`market_id`
   SET g.`fulfillment_status` = 'NEW',
       g.`ship_due_at` = CASE WHEN m.`shipping_lead_days` IS NULL THEN NULL
                              ELSE TIMESTAMPADD(DAY, m.`shipping_lead_days`, o.`paid_at`) END
 WHERE o.`status` = 'PAID' AND g.`fulfillment_status` = 'PENDING';

-- 결제 후 취소된 주문의 그룹 — 소비자 취소(준비 시작 전)로 간주한다.
UPDATE `order_delivery_group` g
  JOIN `orders` o ON o.`order_id` = g.`order_id`
   SET g.`fulfillment_status` = 'CANCELLED',
       g.`cancel_type` = 'CONSUMER',
       g.`status_at_cancel` = 'NEW',
       g.`cancelled_at` = o.`cancelled_at`
 WHERE o.`status` = 'CANCELLED' AND o.`paid_at` IS NOT NULL AND g.`fulfillment_status` = 'PENDING';

-- 하위주문번호 — 주문 안 그룹 순번(id 오름차순) 2자리.
UPDATE `order_delivery_group` g
  JOIN (SELECT `delivery_group_id`,
               ROW_NUMBER() OVER (PARTITION BY `order_id` ORDER BY `delivery_group_id`) AS rn
          FROM `order_delivery_group`) seq ON seq.`delivery_group_id` = g.`delivery_group_id`
  JOIN `orders` o ON o.`order_id` = g.`order_id`
   SET g.`sub_order_number` = CONCAT(o.`order_number`, '-', LPAD(seq.rn, 2, '0'))
 WHERE g.`fulfillment_status` <> 'PENDING' AND g.`sub_order_number` IS NULL;
