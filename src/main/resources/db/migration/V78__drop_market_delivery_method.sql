-- 배송방법 필드 제거 (택배 고정 정책 폐기)
ALTER TABLE market
    DROP COLUMN delivery_method;
