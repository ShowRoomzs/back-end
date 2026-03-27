-- 쿠폰 발급 수량 (NULL: 무제한)
ALTER TABLE coupon
    ADD COLUMN total_quantity INT NULL COMMENT '총 발급 수량 (NULL: 무제한)',
    ADD COLUMN remaining_quantity INT NULL COMMENT '잔여 발급 수량 (NULL: 무제한)';
