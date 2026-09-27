-- 결제 계획서 3-2 · 3-3 — orders 를 리뷰 선행 테이블(V36)에서 실제 주문 테이블로 확장한다.
-- 기존 행(리뷰용 시드)은 status='PAID', order_number 는 yyyyMMdd-NNNNNN(id 6자리)로 백필한다.
-- paid_payment_id 의 FK 는 payment 테이블이 생기는 V145 뒤에 건다.
ALTER TABLE `orders`
    ADD COLUMN `order_number` VARCHAR(30) NULL COMMENT 'yyyyMMdd-NNNNNN' AFTER `user_id`,
    ADD COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_PENDING' COMMENT 'PAYMENT_PENDING · PAID · CANCELLED · EXPIRED' AFTER `order_number`,
    ADD COLUMN `product_total` INT NOT NULL DEFAULT 0 COMMENT '정가 합',
    ADD COLUMN `discount_total` INT NOT NULL DEFAULT 0 COMMENT '정가 합 − 판매가 합',
    ADD COLUMN `delivery_fee_total` INT NOT NULL DEFAULT 0,
    ADD COLUMN `total_amount` INT NOT NULL DEFAULT 0 COMMENT '판매가 합 + 배송비 합 — 결제 금액',
    ADD COLUMN `recipient_name` VARCHAR(64) NULL COMMENT '배송지 스냅샷',
    ADD COLUMN `recipient_phone` VARCHAR(20) NULL,
    ADD COLUMN `zip_code` VARCHAR(10) NULL,
    ADD COLUMN `address` VARCHAR(255) NULL,
    ADD COLUMN `detail_address` VARCHAR(255) NULL,
    ADD COLUMN `delivery_memo` VARCHAR(50) NULL COMMENT 'C9 요청사항',
    ADD COLUMN `order_name` VARCHAR(100) NULL COMMENT '포트원 orderName',
    ADD COLUMN `idempotency_key` VARCHAR(64) NULL COMMENT '앱이 주문 생성마다 보내는 UUID',
    ADD COLUMN `expires_at` DATETIME(6) NULL COMMENT '결제 대기 만료 시각',
    ADD COLUMN `paid_payment_id` VARCHAR(64) NULL COMMENT '이 주문을 완료한 결제(payment.payment_id)',
    ADD COLUMN `stock_released_at` DATETIME(6) NULL COMMENT '재고 복원 시각 — 1회만',
    ADD COLUMN `expiry_check_failures` INT NOT NULL DEFAULT 0,
    ADD COLUMN `expiry_deferrals` INT NOT NULL DEFAULT 0,
    ADD COLUMN `paid_at` DATETIME(6) NULL,
    ADD COLUMN `cancelled_at` DATETIME(6) NULL,
    ADD COLUMN `expired_at` DATETIME(6) NULL,
    ADD COLUMN `cancel_reason` VARCHAR(255) NULL;

-- 기존 행 백필 — 리뷰용 시드는 결제 완료 주문으로 본다.
UPDATE `orders`
   SET `status` = 'PAID',
       `paid_at` = `created_at`,
       `order_number` = CONCAT(DATE_FORMAT(`created_at`, '%Y%m%d'), '-', LPAD(`order_id`, 6, '0'))
 WHERE `order_number` IS NULL;

UPDATE `orders` o
  JOIN (SELECT `order_id`, SUM(`price` * `quantity`) AS `sale_total`
          FROM `order_product` GROUP BY `order_id`) x ON x.`order_id` = o.`order_id`
   SET o.`product_total` = x.`sale_total`,
       o.`total_amount` = x.`sale_total`;

ALTER TABLE `orders`
    MODIFY COLUMN `order_number` VARCHAR(30) NOT NULL,
    ADD UNIQUE KEY `uk_orders_order_number` (`order_number`),
    ADD UNIQUE KEY `uk_orders_user_idempotency_key` (`user_id`, `idempotency_key`),
    ADD INDEX `idx_orders_status_expires_at` (`status`, `expires_at`);
