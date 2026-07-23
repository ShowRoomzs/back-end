CREATE TABLE seller_application (
    seller_application_id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    market_name VARCHAR(255),
    cs_number VARCHAR(255),
    seller_name VARCHAR(64),
    seller_contact VARCHAR(20),
    business_type VARCHAR(50),
    representative_name VARCHAR(64),
    representative_contact VARCHAR(20),
    company_name VARCHAR(100),
    business_reg_number VARCHAR(128),
    business_condition VARCHAR(100),
    business_address VARCHAR(255),
    detail_address VARCHAR(255),
    tax_email VARCHAR(512),
    business_license_url VARCHAR(1024),
    mail_order_reg_url VARCHAR(1024),
    mail_order_reg_num VARCHAR(100),
    bank_name VARCHAR(50),
    account_holder VARCHAR(64),
    account_number VARCHAR(100),
    bankbook_url VARCHAR(1024),
    agree_privacy_policy BOOLEAN,
    agree_terms_of_service BOOLEAN,
    agree_operation_policy BOOLEAN,
    status ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL,
    processed_at DATETIME(6),
    reject_reason VARCHAR(500),
    reject_reason_detail VARCHAR(1000),
    created_at DATETIME(6),
    modified_at DATETIME(6),
    PRIMARY KEY (seller_application_id),
    CONSTRAINT fk_seller_application_seller
        FOREIGN KEY (seller_id) REFERENCES seller (seller_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seller_application_history (
    seller_application_history_id BIGINT NOT NULL AUTO_INCREMENT,
    seller_application_id BIGINT NOT NULL,
    previous_status ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL,
    new_status ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL,
    reason VARCHAR(500),
    created_at DATETIME(6),
    modified_at DATETIME(6),
    PRIMARY KEY (seller_application_history_id),
    CONSTRAINT fk_seller_application_history_application
        FOREIGN KEY (seller_application_id) REFERENCES seller_application (seller_application_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 기존 SELLER 계정 신청서 백필
INSERT INTO seller_application (
    seller_id,
    market_name,
    cs_number,
    seller_name,
    seller_contact,
    business_type,
    representative_name,
    representative_contact,
    company_name,
    business_reg_number,
    business_condition,
    business_address,
    detail_address,
    tax_email,
    business_license_url,
    mail_order_reg_url,
    mail_order_reg_num,
    bank_name,
    account_holder,
    account_number,
    bankbook_url,
    agree_privacy_policy,
    agree_terms_of_service,
    agree_operation_policy,
    status,
    processed_at,
    reject_reason,
    reject_reason_detail,
    created_at,
    modified_at
)
SELECT
    s.seller_id,
    m.market_name,
    m.cs_number,
    s.name,
    s.phone_number,
    s.business_type,
    s.representative_name,
    s.representative_contact,
    s.company_name,
    s.business_reg_number,
    s.business_condition,
    s.business_address,
    s.detail_address,
    s.tax_email,
    s.business_license_url,
    s.mail_order_reg_url,
    s.mail_order_reg_num,
    s.bank_name,
    s.account_holder,
    s.account_number,
    s.bankbook_url,
    s.agree_privacy_policy,
    s.agree_terms_of_service,
    s.agree_operation_policy,
    s.status,
    s.processed_at,
    s.rejection_reason,
    s.rejection_reason_detail,
    s.created_at,
    s.modified_at
FROM seller s
LEFT JOIN market m ON m.seller_id = s.seller_id
WHERE s.role_type = 'SELLER';

-- 승인/반려된 기존 신청서의 이력 백필
INSERT INTO seller_application_history (
    seller_application_id,
    previous_status,
    new_status,
    reason,
    created_at,
    modified_at
)
SELECT
    sa.seller_application_id,
    'PENDING',
    sa.status,
    CASE
        WHEN sa.reject_reason_detail IS NOT NULL AND sa.reject_reason_detail <> ''
            THEN CONCAT(IFNULL(sa.reject_reason, ''), ' - ', sa.reject_reason_detail)
        ELSE sa.reject_reason
    END,
    IFNULL(sa.processed_at, sa.modified_at),
    IFNULL(sa.processed_at, sa.modified_at)
FROM seller_application sa
WHERE sa.status IN ('APPROVED', 'REJECTED');
