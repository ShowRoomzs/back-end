CREATE TABLE coupon_product (
    coupon_product_id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (coupon_product_id),
    UNIQUE KEY uk_coupon_product (coupon_id, product_id),
    CONSTRAINT fk_coupon_product_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT fk_coupon_product_product FOREIGN KEY (product_id) REFERENCES product (product_id)
) ENGINE=InnoDB;
