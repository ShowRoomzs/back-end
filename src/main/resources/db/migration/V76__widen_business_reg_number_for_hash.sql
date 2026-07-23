-- 반려 시 사업자등록번호 SHA-256 해시(64자) 적재를 위한 컬럼 확장
ALTER TABLE seller
    MODIFY COLUMN business_reg_number VARCHAR(128);

ALTER TABLE seller_application
    MODIFY COLUMN business_reg_number VARCHAR(128);
