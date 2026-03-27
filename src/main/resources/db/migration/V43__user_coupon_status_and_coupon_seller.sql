-- user_coupon.status / coupon.seller_id + FK (이전 실패로 컬럼만 생긴 경우에도 재실행 가능)

SET @db := DATABASE();

-- 1) user_coupon.status
SET @has_status := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user_coupon' AND COLUMN_NAME = 'status');
SET @sql_status := IF(@has_status = 0,
    'ALTER TABLE user_coupon ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''AVAILABLE'' COMMENT ''AVAILABLE, USED''',
    'SELECT 1');
PREPARE ps FROM @sql_status;
EXECUTE ps;
DEALLOCATE PREPARE ps;

-- 2) coupon.seller_id + FK (한 번에 추가) 또는 FK만 추가
SET @has_seller_col := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'seller_id');
SET @has_fk := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = @db AND TABLE_NAME = 'coupon' AND CONSTRAINT_NAME = 'fk_coupon_seller');

SET @sql_coupon := IF(@has_seller_col = 0,
    'ALTER TABLE coupon ADD COLUMN seller_id BIGINT NULL COMMENT ''발행 판매자 (seller_id)'', ADD CONSTRAINT fk_coupon_seller FOREIGN KEY (seller_id) REFERENCES seller (seller_id)',
    IF(@has_fk = 0,
        'ALTER TABLE coupon ADD CONSTRAINT fk_coupon_seller FOREIGN KEY (seller_id) REFERENCES seller (seller_id)',
        'SELECT 1'));
PREPARE pc FROM @sql_coupon;
EXECUTE pc;
DEALLOCATE PREPARE pc;
