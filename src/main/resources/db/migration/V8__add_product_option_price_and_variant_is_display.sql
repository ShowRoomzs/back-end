-- product_option 테이블에 price 컬럼 추가
ALTER TABLE product_option
ADD COLUMN price INT NOT NULL DEFAULT 0 COMMENT '옵션 추가 가격';

-- product_variant 테이블에 is_display 컬럼 추가
ALTER TABLE product_variant
ADD COLUMN is_display BOOLEAN NOT NULL DEFAULT TRUE COMMENT '옵션 조합 진열 여부';
