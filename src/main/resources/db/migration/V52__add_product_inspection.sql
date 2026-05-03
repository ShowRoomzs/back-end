-- 상품 검수 상태 및 반려 정보
ALTER TABLE `product`
    ADD COLUMN `inspection_status` VARCHAR(32) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING,HOLD,APPROVED,REJECTED,REAPPLIED',
    ADD COLUMN `admin_memo` VARCHAR(500) NULL COMMENT '관리자 검수 메모',
    ADD COLUMN `reject_reason_type` VARCHAR(64) NULL COMMENT '반려 사유 타입',
    ADD COLUMN `reject_detail` VARCHAR(500) NULL COMMENT '반려 상세 사유';

-- 검수 상태 변경 이력
CREATE TABLE `product_inspection_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT NOT NULL,
    `previous_status` VARCHAR(32) NULL,
    `new_status` VARCHAR(32) NOT NULL,
    `reject_reason_type` VARCHAR(64) NULL,
    `reject_detail` VARCHAR(500) NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_pih_product_id_created` (`product_id`, `created_at`),
    CONSTRAINT `fk_pih_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
