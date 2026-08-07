-- 상품 검수 이력 테이블 및 product 검수 관련 컬럼 제거
DROP TABLE IF EXISTS `product_inspection_history`;

ALTER TABLE `product`
    DROP COLUMN `inspection_status`,
    DROP COLUMN `admin_memo`,
    DROP COLUMN `reject_reason_type`,
    DROP COLUMN `reject_detail`;
