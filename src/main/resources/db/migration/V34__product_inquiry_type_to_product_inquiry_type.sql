-- 상품문의 type을 상품문의 전용 타입으로 변경 (PRODUCT_INQUIRY, SIZE_INQUIRY, STOCK_INQUIRY)
-- 1) 컬럼을 varchar로 변경 후 기존 값 매핑
ALTER TABLE product_inquiry MODIFY COLUMN type VARCHAR(50) NOT NULL;

-- 2) 기존 1:1 문의 타입 값들을 상품문의 기본 타입으로 통일
UPDATE product_inquiry
SET type = 'PRODUCT_INQUIRY'
WHERE type NOT IN ('PRODUCT_INQUIRY', 'SIZE_INQUIRY', 'STOCK_INQUIRY');
