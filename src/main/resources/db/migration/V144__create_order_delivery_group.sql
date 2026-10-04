-- 결제 계획서 3-1 · 3-2 — 배송 그룹(키는 공구)과 order_product 확장.
-- order_product 는 컬럼 추가만 한다 — V47 뷰 market_inquiry_view 가 order_id·variant_id 를 조인한다.
CREATE TABLE `order_delivery_group` (
    `delivery_group_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`              BIGINT       NOT NULL,
    `group_buy_id`          BIGINT       NULL COMMENT 'NULL 은 리뷰용 시드 백필 행뿐 — 신규 주문은 항상 채운다',
    `market_id`             BIGINT       NOT NULL,
    `product_total`         INT          NOT NULL DEFAULT 0 COMMENT '그룹 판매가 합',
    `delivery_fee`          INT          NOT NULL DEFAULT 0 COMMENT '실제 부과 배송비 — 무료면 0',
    `free_shipping_applied` BIT(1)       NOT NULL DEFAULT 0,
    `market_name`           VARCHAR(100) NULL COMMENT '쇼룸명 스냅샷',
    `group_buy_number`      VARCHAR(30)  NULL COMMENT '공구번호 스냅샷',
    `created_at`            DATETIME(6)  NOT NULL,
    `modified_at`           DATETIME(6)  NOT NULL,
    PRIMARY KEY (`delivery_group_id`),
    -- NULL 은 중복을 허용하므로 백필 행에는 이 UK 가 의미 없다(신규 행만 막는다).
    UNIQUE KEY `uk_order_delivery_group_order_group_buy` (`order_id`, `group_buy_id`),
    CONSTRAINT `fk_order_delivery_group_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`),
    CONSTRAINT `fk_order_delivery_group_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`),
    CONSTRAINT `fk_order_delivery_group_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`MARKET_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `order_product`
    ADD COLUMN `delivery_group_id` BIGINT NULL AFTER `order_id`,
    ADD COLUMN `group_buy_id`      BIGINT NULL COMMENT '공구 귀속 — 판매 집계가 조인 없이 돈다' AFTER `delivery_group_id`,
    ADD COLUMN `regular_price`     INT    NULL COMMENT '정가 스냅샷 — price 는 판매가' AFTER `price`,
    ADD COLUMN `cart_id`           BIGINT NULL COMMENT '어느 장바구니 행에서 왔나(FK 없음) — PAID 전이 때 지운다. 바로 구매는 NULL';

-- 기존 order_product 백필 — 마켓별 한 그룹. 정가는 알 수 없어 판매가를 넣는다.
INSERT INTO `order_delivery_group`
    (`order_id`, `group_buy_id`, `market_id`, `product_total`, `delivery_fee`, `free_shipping_applied`,
     `market_name`, `group_buy_number`, `created_at`, `modified_at`)
SELECT op.`order_id`, NULL, p.`market_id`, SUM(op.`price` * op.`quantity`), 0, 0,
       m.`MARKET_NAME`, NULL, NOW(6), NOW(6)
  FROM `order_product` op
  JOIN `product_variant` pv ON pv.`variant_id` = op.`variant_id`
  JOIN `product` p          ON p.`product_id` = pv.`product_id`
  JOIN `market` m           ON m.`MARKET_ID` = p.`market_id`
 GROUP BY op.`order_id`, p.`market_id`, m.`MARKET_NAME`;

UPDATE `order_product` op
  JOIN `product_variant` pv ON pv.`variant_id` = op.`variant_id`
  JOIN `product` p          ON p.`product_id` = pv.`product_id`
  JOIN `order_delivery_group` g ON g.`order_id` = op.`order_id` AND g.`market_id` = p.`market_id`
   SET op.`delivery_group_id` = g.`delivery_group_id`,
       op.`regular_price` = op.`price`
 WHERE op.`delivery_group_id` IS NULL;

ALTER TABLE `order_product`
    ADD CONSTRAINT `fk_order_product_delivery_group` FOREIGN KEY (`delivery_group_id`) REFERENCES `order_delivery_group` (`delivery_group_id`),
    ADD CONSTRAINT `fk_order_product_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`),
    ADD INDEX `idx_order_product_group_buy` (`group_buy_id`, `status`);
