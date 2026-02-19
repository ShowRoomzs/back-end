-- 주문 테이블 (Review 기능의 선행 테이블)
CREATE TABLE `orders` (
    `order_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `modified_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`order_id`),
    CONSTRAINT `fk_orders_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB;

-- 주문 상품 테이블
CREATE TABLE `order_product` (
    `order_product_id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `variant_id` BIGINT NOT NULL,
    `product_name` VARCHAR(255) NOT NULL,
    `option_name` VARCHAR(255),
    `quantity` INT NOT NULL,
    `price` INT NOT NULL,
    `image_url` VARCHAR(2048),
    `order_date` DATETIME(6) NOT NULL,
    `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME(6) NOT NULL,
    `modified_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`order_product_id`),
    CONSTRAINT `fk_order_product_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`),
    CONSTRAINT `fk_order_product_variant` FOREIGN KEY (`variant_id`) REFERENCES `product_variant` (`variant_id`)
) ENGINE=InnoDB;

-- 리뷰 테이블 (OrderProduct와 1:1, 중복 작성 방지)
CREATE TABLE `review` (
    `review_id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_product_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `rating` INT NOT NULL,
    `content` TEXT NOT NULL,
    `is_promotion_agreed` BIT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL,
    `modified_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`review_id`),
    UNIQUE KEY `uk_review_order_product` (`order_product_id`),
    CONSTRAINT `fk_review_order_product` FOREIGN KEY (`order_product_id`) REFERENCES `order_product` (`order_product_id`),
    CONSTRAINT `fk_review_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB;

-- 리뷰 이미지 테이블 (순서 관리용 sequence 컬럼)
CREATE TABLE `review_image` (
    `review_image_id` BIGINT NOT NULL AUTO_INCREMENT,
    `review_id` BIGINT NOT NULL,
    `url` VARCHAR(2048) NOT NULL,
    `sequence` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`review_image_id`),
    CONSTRAINT `fk_review_image_review` FOREIGN KEY (`review_id`) REFERENCES `review` (`review_id`)
) ENGINE=InnoDB;
