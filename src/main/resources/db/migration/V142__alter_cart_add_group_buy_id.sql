-- 장바구니 행의 공구 귀속(가격 계획서 3-1). 소비자 가격은 공구 계약의 스냅샷에서 나오므로
-- 「어느 공구에서 담았나」를 모르면 가격을 정할 수 없다. 같은 옵션도 공구가 다르면 다른 줄이다.
ALTER TABLE `cart`
    ADD COLUMN `group_buy_id` BIGINT NULL COMMENT '담은 공구 — NULL은 귀속을 정하지 못한 옛 행(마감으로 표시)',
    ADD CONSTRAINT `fk_cart_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`);

-- 유니크 키 교체. 새 키를 먼저 만든다 — 옛 키(user_id, variant_id)가 user_id FK의 인덱스 역할을 하고 있어
-- 대체 인덱스 없이 지우면 MySQL이 거부한다. 옛 키 이름은 V20의 이름 없는 unique가 받은 `user_id`다.
ALTER TABLE `cart` ADD UNIQUE KEY `uk_cart_user_variant_group_buy` (`user_id`, `variant_id`, `group_buy_id`);
ALTER TABLE `cart` DROP INDEX `user_id`;

-- 1회 백필 — 옵션의 상품이 걸린 활성 공구가 정확히 1개일 때만 그 공구로 귀속한다.
-- 둘 이상이면 공구가(가격)가 둘이라 서버가 고를 수 없다. NULL로 남은 행은 목록에서 마감으로 보이고 사용자가 다시 담는다.
UPDATE `cart` c
  JOIN (SELECT v.`variant_id`,
               MIN(g.`group_buy_id`)              AS `group_buy_id`,
               COUNT(DISTINCT g.`group_buy_id`)   AS `cnt`
          FROM `product_variant` v
          JOIN `contract_item` ci ON ci.`product_id` = v.`product_id`
          JOIN `group_buy` g      ON g.`contract_id` = ci.`contract_id`
         WHERE g.`status` IN ('PREPARING', 'READY', 'IN_PROGRESS', 'SUSPENSION_SCHEDULED')
         GROUP BY v.`variant_id`) x
    ON x.`variant_id` = c.`variant_id` AND x.`cnt` = 1
   SET c.`group_buy_id` = x.`group_buy_id`
 WHERE c.`group_buy_id` IS NULL;
