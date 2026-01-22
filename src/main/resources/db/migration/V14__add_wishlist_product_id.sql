CREATE TABLE IF NOT EXISTS `wishlist` (
  `wishlist_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `product_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`wishlist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @product_id_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'wishlist'
    AND column_name = 'product_id'
);
SET @add_product_id_sql := IF(
  @product_id_exists = 0,
  'ALTER TABLE `wishlist` ADD COLUMN `product_id` bigint DEFAULT NULL',
  'SELECT 1'
);
PREPARE add_product_id_stmt FROM @add_product_id_sql;
EXECUTE add_product_id_stmt;
DEALLOCATE PREPARE add_product_id_stmt;

SET @created_at_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'wishlist'
    AND column_name = 'created_at'
);
SET @add_created_at_sql := IF(
  @created_at_exists = 0,
  'ALTER TABLE `wishlist` ADD COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
  'SELECT 1'
);
PREPARE add_created_at_stmt FROM @add_created_at_sql;
EXECUTE add_created_at_stmt;
DEALLOCATE PREPARE add_created_at_stmt;
