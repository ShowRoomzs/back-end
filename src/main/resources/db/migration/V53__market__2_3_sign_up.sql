-- 사업자 기본 정보 추가
alter table seller
add column business_type varchar(50),
add column representative_name varchar(64),
add column representative_contact varchar(20),
add column company_name varchar(100),
add column business_reg_number varchar(20),
add column business_condition varchar(100),
add column business_address varchar(255),
add column detail_address varchar(255),
add column tax_email varchar(512),
add column business_license_url varchar(1024),
add column mail_order_reg_url varchar(1024),
add column mail_order_reg_num varchar(100),

-- 정산 계좌 정보 추가
add column bank_name varchar(50),
add column account_holder varchar(64),
add column account_number varchar(100),
add column bankbook_url varchar(1024),

-- 약관 동의 내역 추가
add column agree_privacy_policy boolean,
add column agree_terms_of_service boolean,
add column agree_operation_policy boolean;