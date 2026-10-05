-- 주문 시점 기본 배송비(앱 클레임 설계서 1-4) — 무료배송이어도 원래 값을 남긴다.
-- delivery_fee 는 실제 부과액이라 무료배송이면 0이다. 반품 배송비 차감 · 교환 재발송비 · 반려 재발송비가 이 값을 쓴다
-- (마켓의 현재 설정이 아니라 주문할 때 본 값). 주문 생성 때 1회 적고 이후 바꾸지 않는다.
ALTER TABLE `order_delivery_group`
    ADD COLUMN `base_delivery_fee` INT NOT NULL DEFAULT 0 COMMENT '주문 시점 기본 배송비 — 무료배송이어도 원래 값' AFTER `delivery_fee`;

-- 유료배송 그룹은 부과액이 곧 기본 배송비다(정확).
UPDATE `order_delivery_group`
SET `base_delivery_fee` = `delivery_fee`
WHERE `free_shipping_applied` = 0;

-- 무료배송 그룹은 주문 당시 값이 남아 있지 않다 — 마켓의 현재 설정으로 추정한다. 설정이 없으면 0.
UPDATE `order_delivery_group` g
    JOIN `market` m ON m.`market_id` = g.`market_id`
SET g.`base_delivery_fee` = COALESCE(m.`default_delivery_fee`, 0)
WHERE g.`free_shipping_applied` = 1;
