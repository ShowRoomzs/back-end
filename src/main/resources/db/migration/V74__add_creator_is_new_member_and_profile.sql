-- 크리에이터 신규 회원(추가 정보 미입력) 여부 및 추가 정보 컬럼
ALTER TABLE creator
    ADD COLUMN is_new_member BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN showroom_name VARCHAR(100) NULL,
    ADD COLUMN business_type VARCHAR(30) NULL,
    ADD COLUMN business_registration_number VARCHAR(20) NULL,
    ADD COLUMN business_license_image_url VARCHAR(1024) NULL,
    ADD COLUMN bank_name VARCHAR(50) NULL,
    ADD COLUMN account_number VARCHAR(100) NULL,
    ADD COLUMN bankbook_image_url VARCHAR(1024) NULL;

-- 기존 크리에이터는 추가 정보 입력 완료로 간주
UPDATE creator
SET is_new_member = FALSE;

-- 쇼룸명 중복 방지 (NULL 허용, 입력된 값만 유니크)
CREATE UNIQUE INDEX uk_creator_showroom_name ON creator (showroom_name);
