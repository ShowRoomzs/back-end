-- 판매자 신규 회원(필수 정보 미입력) 여부
ALTER TABLE seller
    ADD COLUMN is_new_member BOOLEAN NOT NULL DEFAULT TRUE;

-- 기존 승인 완료 판매자는 필수 정보 입력 완료로 간주
UPDATE seller
SET is_new_member = FALSE
WHERE status = 'APPROVED';

-- 마켓 배송/반품 설정
ALTER TABLE market
    ADD COLUMN shipping_recipient_name VARCHAR(64),
    ADD COLUMN shipping_contact VARCHAR(20),
    ADD COLUMN shipping_address VARCHAR(255),
    ADD COLUMN shipping_detail_address VARCHAR(255),
    ADD COLUMN default_delivery_fee INT,
    ADD COLUMN free_shipping_threshold INT,
    ADD COLUMN remote_area_surcharge INT NOT NULL DEFAULT 0,
    ADD COLUMN delivery_method VARCHAR(50) NOT NULL DEFAULT '택배',
    ADD COLUMN shipping_lead_days INT,
    ADD COLUMN return_fee INT NOT NULL DEFAULT 3000,
    ADD COLUMN exchange_fee INT NOT NULL DEFAULT 6000;
