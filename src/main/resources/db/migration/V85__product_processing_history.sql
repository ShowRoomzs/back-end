-- 상품 미진열 사유 (현재 상태용)
ALTER TABLE `product`
    ADD COLUMN `hide_reason_type` VARCHAR(64) NULL
        COMMENT '미진열 사유: PRODUCT_NOTICE_ERROR, AD_DISPLAY_VIOLATION, BRAND_REQUEST, OTHER'
        AFTER `reject_detail`,
    ADD COLUMN `hide_detail` VARCHAR(500) NULL
        COMMENT '미진열 상세 사유 (선택)'
        AFTER `hide_reason_type`;

-- 상품 처리 이력 (브랜드/어드민 공유)
CREATE TABLE `product_processing_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `history_type` VARCHAR(32) NOT NULL
        COMMENT 'PRODUCT_CREATED, PRODUCT_INFO_UPDATED, STOCK_UPDATED, HIDDEN, REDISPLAYED, HIDE_REQUESTED, PENDING_REVIEW',
    `previous_display_status` VARCHAR(32) NULL,
    `new_display_status` VARCHAR(32) NULL,
    `hide_reason_type` VARCHAR(64) NULL,
    `hide_detail` VARCHAR(500) NULL,
    `stock_quantity` INT NULL,
    `processed_by` BIGINT NULL COMMENT '처리 운영자 seller_id',
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_pph_product_id_created` (`product_id`, `created_at`),
    CONSTRAINT `fk_pph_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
