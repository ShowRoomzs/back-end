ALTER TABLE seller_application_history
    ADD COLUMN processed_by BIGINT NULL COMMENT '처리 운영자(SELLER.ADMIN) ID' AFTER reason;
