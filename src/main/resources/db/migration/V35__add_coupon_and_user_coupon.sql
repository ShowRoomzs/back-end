-- coupon 테이블
CREATE TABLE coupon (
    coupon_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(19, 2) NOT NULL,
    min_order_amount DECIMAL(19, 2) DEFAULT NULL,
    max_discount_amount DECIMAL(19, 2) DEFAULT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    modified_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (coupon_id),
    UNIQUE KEY uk_coupon_code (code)
) ENGINE=InnoDB;

-- user_coupon 테이블 (사용자-쿠폰 매핑)
CREATE TABLE user_coupon (
    user_coupon_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    registered_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (user_coupon_id),
    UNIQUE KEY user_coupon_uk (user_id, coupon_id),
    CONSTRAINT fk_user_coupon_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id)
) ENGINE=InnoDB;
