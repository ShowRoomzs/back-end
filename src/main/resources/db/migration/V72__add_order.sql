-- =======================================================================
-- 1. 신규 테이블 생성 (New Tables)
-- =======================================================================
CREATE TABLE cancel_return (
    cancel_return_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    pickup_tracking_number VARCHAR(100),
    quantity INTEGER NOT NULL,
    reason VARCHAR(500),
    refund_amount INTEGER,
    refunded_at DATETIME(6),
    refunded_by VARCHAR(64),
    rejection_reason VARCHAR(500),
    status ENUM('COLLECTING', 'REFUNDED', 'REJECTED', 'REQUESTED', 'RETURNED') NOT NULL,
    type ENUM('CANCEL', 'RETURN') NOT NULL,
    warehoused_at DATETIME(6),
    order_product_id BIGINT NOT NULL,
    PRIMARY KEY (cancel_return_id)
) ENGINE=InnoDB;

CREATE TABLE communication_thread (
    thread_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    creator_last_read_at DATETIME(6),
    last_activity_at DATETIME(6),
    seller_last_read_at DATETIME(6),
    status ENUM('DORMANT', 'OPEN') NOT NULL,
    creator_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    PRIMARY KEY (thread_id)
) ENGINE=InnoDB;

CREATE TABLE connection (
    connection_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    accepted_at DATETIME(6),
    disconnect_type ENUM('REJECTED', 'RELEASED'),
    disconnected_at DATETIME(6),
    requested_at DATETIME(6) NOT NULL,
    status ENUM('CONNECTED', 'DISCONNECTED', 'REQUESTED') NOT NULL,
    creator_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    PRIMARY KEY (connection_id)
) ENGINE=InnoDB;

CREATE TABLE contract (
    contract_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    audit_trail_cert_url VARCHAR(2048),
    brand_signed BIT NOT NULL,
    completed_at DATETIME(6),
    external_document_id VARCHAR(255),
    group_buy_end_date DATE,
    group_buy_start_date DATE,
    influencer_signed BIT NOT NULL,
    signed_pdf_url VARCHAR(2048),
    status ENUM('AWAITING_SIGN', 'CANCELED', 'COMPLETED', 'DRAFT', 'EXPIRED', 'REJECTED') NOT NULL,
    connection_id BIGINT NOT NULL,
    PRIMARY KEY (contract_id)
) ENGINE=InnoDB;

CREATE TABLE contract_product (
    contract_product_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    group_buy_price INTEGER NOT NULL,
    reward_rate DECIMAL(5, 2) NOT NULL,
    contract_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (contract_product_id)
) ENGINE=InnoDB;

CREATE TABLE group_buy (
    group_buy_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    approved_at DATETIME(6),
    approved_by VARCHAR(64),
    cancel_reason VARCHAR(500),
    end_date DATE NOT NULL,
    start_date DATE NOT NULL,
    status ENUM('CANCELED', 'CLOSED', 'IN_PROGRESS', 'PREPARING', 'READY', 'SETTLED') NOT NULL,
    contract_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    PRIMARY KEY (group_buy_id)
) ENGINE=InnoDB;

CREATE TABLE group_buy_post (
    group_buy_post_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    content TEXT,
    scheduled_at DATETIME(6),
    status ENUM('CLOSED', 'DRAFT', 'HIDDEN', 'SCHEDULED', 'VISIBLE') NOT NULL,
    title VARCHAR(200),
    creator_id BIGINT NOT NULL,
    group_buy_id BIGINT NOT NULL,
    PRIMARY KEY (group_buy_post_id)
) ENGINE=InnoDB;

CREATE TABLE notification (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    content VARCHAR(500),
    event_type ENUM('ADMIN_ENFORCEMENT', 'CONNECTION_ACCEPTED', 'CONNECTION_REQUESTED', 'CONTRACT_COMPLETED', 'CONTRACT_RECALLED', 'CONTRACT_SIGN_REQUESTED', 'CREATOR_POST_PUBLISHED', 'CS_ANSWER_REGISTERED', 'CS_INQUIRY_RECEIVED', 'DELIVERY_STATUS_CHANGED', 'GROUP_BUY_APPROVAL_RESULT', 'NEW_ORDER', 'ONBOARDING_RESULT', 'RETURN_STATUS_CHANGED', 'SETTLEMENT_COMPLETED', 'THREAD_NEW_MESSAGE') NOT NULL,
    is_read BIT NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_type ENUM('ADMIN', 'CREATOR', 'SELLER', 'USER') NOT NULL,
    target_id BIGINT,
    target_type VARCHAR(40),
    PRIMARY KEY (notification_id)
) ENGINE=InnoDB;

CREATE TABLE settlement (
    settlement_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    brand_payout BIGINT NOT NULL,
    confirmed_at DATETIME(6),
    confirmed_by VARCHAR(64),
    due_date DATE,
    hold_reason VARCHAR(500),
    influencer_payout BIGINT NOT NULL,
    platform_fee BIGINT NOT NULL,
    status ENUM('ADMIN_CONFIRMED', 'ON_HOLD', 'PENDING', 'TRANSFERRED') NOT NULL,
    target_amount BIGINT NOT NULL,
    transferred_at DATETIME(6),
    group_buy_id BIGINT NOT NULL,
    PRIMARY KEY (settlement_id)
) ENGINE=InnoDB;

CREATE TABLE sub_order (
    sub_order_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    confirm_due_date DATE,
    confirmed_at DATETIME(6),
    delivered_at DATETIME(6),
    delivery_status ENUM('CONFIRMED', 'DELIVERED', 'PAID', 'PREPARING', 'SHIPPING') NOT NULL,
    tracking_number VARCHAR(100),
    group_buy_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    PRIMARY KEY (sub_order_id)
) ENGINE=InnoDB;

CREATE TABLE terms_document (
    terms_document_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    is_active BIT NOT NULL,
    content TEXT NOT NULL,
    effective_date DATE NOT NULL,
    type ENUM('CREATOR_TERMS', 'PRIVACY_POLICY', 'SELLER_TERMS', 'USER_TERMS') NOT NULL,
    version VARCHAR(20) NOT NULL,
    PRIMARY KEY (terms_document_id)
) ENGINE=InnoDB;

CREATE TABLE thread_message (
    message_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    modified_at DATETIME(6),
    content TEXT,
    writer_type ENUM('BRAND', 'INFLUENCER') NOT NULL,
    thread_id BIGINT NOT NULL,
    PRIMARY KEY (message_id)
) ENGINE=InnoDB;

CREATE TABLE thread_message_media (
    message_id BIGINT NOT NULL,
    media_url VARCHAR(2048)
) ENGINE=InnoDB;


-- =======================================================================
-- 2. 기존 테이블 컬럼 및 ENUM 수정 (Alter Tables)
-- =======================================================================

ALTER TABLE cart
    ADD COLUMN group_buy_price INTEGER,
    ADD COLUMN group_buy_post_id BIGINT;

ALTER TABLE order_product
    ADD COLUMN group_buy_post_id BIGINT,
    ADD COLUMN sub_order_id BIGINT;

ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(50),
    ADD COLUMN pg_transaction_id VARCHAR(255),
    ADD COLUMN address VARCHAR(255),
    ADD COLUMN address_detail VARCHAR(255),
    ADD COLUMN delivery_memo VARCHAR(255),
    ADD COLUMN receiver_name VARCHAR(64),
    ADD COLUMN receiver_phone VARCHAR(20),
    ADD COLUMN zip_code VARCHAR(10),
    ADD COLUMN total_amount INTEGER;

-- 💡 [중요] 기존 orders 데이터가 있을 경우 NOT NULL 충돌 방지 로직
ALTER TABLE orders ADD COLUMN payment_status ENUM('CANCELED', 'FAILED', 'PAID', 'PENDING');
UPDATE orders SET payment_status = 'PENDING' WHERE payment_status IS NULL;
ALTER TABLE orders MODIFY COLUMN payment_status ENUM('CANCELED', 'FAILED', 'PAID', 'PENDING') NOT NULL;

-- ENUM 타입 스키마 변경 (범위 확장 반영)
ALTER TABLE creator MODIFY COLUMN sns_type ENUM('INSTAGRAM', 'TIKTOK', 'X', 'YOUTUBE') NOT NULL;
ALTER TABLE creator_application MODIFY COLUMN sns_type ENUM('INSTAGRAM', 'TIKTOK', 'X', 'YOUTUBE') NOT NULL;
ALTER TABLE creator_application MODIFY COLUMN status ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL;
ALTER TABLE seller MODIFY COLUMN role_type ENUM('ADMIN', 'CREATOR', 'GUEST', 'SELLER', 'USER') NOT NULL;
ALTER TABLE seller MODIFY COLUMN status ENUM('APPROVED', 'PENDING', 'REJECTED') NOT NULL;
ALTER TABLE users MODIFY COLUMN provider_type ENUM('APPLE', 'FACEBOOK', 'GOOGLE', 'KAKAO', 'LOCAL', 'NAVER') NOT NULL;
ALTER TABLE users MODIFY COLUMN role_type ENUM('ADMIN', 'CREATOR', 'GUEST', 'SELLER', 'USER') NOT NULL;


-- =======================================================================
-- 3. 고유 제약조건 (Unique Constraints)
-- =======================================================================

-- 기존 테이블 Unique 제약조건 교체 (JPA 로그에 있는 구 인덱스 드랍 포함)
CREATE INDEX idx_cart_user_id ON cart(user_id);
ALTER TABLE cart DROP INDEX user_id;
ALTER TABLE cart ADD CONSTRAINT cart_uk UNIQUE (user_id, variant_id, group_buy_post_id);


-- 신규 테이블 Unique 제약조건 추가
ALTER TABLE communication_thread ADD CONSTRAINT communication_thread_uk UNIQUE (seller_id, creator_id);
ALTER TABLE group_buy ADD CONSTRAINT group_buy_contract_uk UNIQUE (contract_id);
ALTER TABLE group_buy_post ADD CONSTRAINT group_buy_post_group_buy_uk UNIQUE (group_buy_id);
ALTER TABLE settlement ADD CONSTRAINT settlement_group_buy_uk UNIQUE (group_buy_id);
ALTER TABLE terms_document ADD CONSTRAINT terms_document_uk UNIQUE (type, version);


-- =======================================================================
-- 4. 외래키 제약조건 (Foreign Keys) - 명시적 네이밍
-- =======================================================================

ALTER TABLE cancel_return ADD CONSTRAINT fk_cancel_return_order_product FOREIGN KEY (order_product_id) REFERENCES order_product (order_product_id);

ALTER TABLE cart ADD CONSTRAINT fk_cart_group_buy_post FOREIGN KEY (group_buy_post_id) REFERENCES group_buy_post (group_buy_post_id);

ALTER TABLE communication_thread ADD CONSTRAINT fk_communication_thread_creator FOREIGN KEY (creator_id) REFERENCES creator (creator_id);
ALTER TABLE communication_thread ADD CONSTRAINT fk_communication_thread_seller FOREIGN KEY (seller_id) REFERENCES seller (seller_id);

ALTER TABLE connection ADD CONSTRAINT fk_connection_creator FOREIGN KEY (creator_id) REFERENCES creator (creator_id);
ALTER TABLE connection ADD CONSTRAINT fk_connection_seller FOREIGN KEY (seller_id) REFERENCES seller (seller_id);

ALTER TABLE contract ADD CONSTRAINT fk_contract_connection FOREIGN KEY (connection_id) REFERENCES connection (connection_id);
ALTER TABLE contract_product ADD CONSTRAINT fk_contract_product_contract FOREIGN KEY (contract_id) REFERENCES contract (contract_id);
ALTER TABLE contract_product ADD CONSTRAINT fk_contract_product_product FOREIGN KEY (product_id) REFERENCES product (product_id);

ALTER TABLE group_buy ADD CONSTRAINT fk_group_buy_contract FOREIGN KEY (contract_id) REFERENCES contract (contract_id);
ALTER TABLE group_buy ADD CONSTRAINT fk_group_buy_creator FOREIGN KEY (creator_id) REFERENCES creator (creator_id);
ALTER TABLE group_buy ADD CONSTRAINT fk_group_buy_seller FOREIGN KEY (seller_id) REFERENCES seller (seller_id);

ALTER TABLE group_buy_post ADD CONSTRAINT fk_group_buy_post_creator FOREIGN KEY (creator_id) REFERENCES creator (creator_id);
ALTER TABLE group_buy_post ADD CONSTRAINT fk_group_buy_post_group_buy FOREIGN KEY (group_buy_id) REFERENCES group_buy (group_buy_id);

ALTER TABLE order_product ADD CONSTRAINT fk_order_product_group_buy_post FOREIGN KEY (group_buy_post_id) REFERENCES group_buy_post (group_buy_post_id);
ALTER TABLE order_product ADD CONSTRAINT fk_order_product_sub_order FOREIGN KEY (sub_order_id) REFERENCES sub_order (sub_order_id);

ALTER TABLE settlement ADD CONSTRAINT fk_settlement_group_buy FOREIGN KEY (group_buy_id) REFERENCES group_buy (group_buy_id);

ALTER TABLE sub_order ADD CONSTRAINT fk_sub_order_group_buy FOREIGN KEY (group_buy_id) REFERENCES group_buy (group_buy_id);
ALTER TABLE sub_order ADD CONSTRAINT fk_sub_order_order FOREIGN KEY (order_id) REFERENCES orders (order_id);
ALTER TABLE sub_order ADD CONSTRAINT fk_sub_order_seller FOREIGN KEY (seller_id) REFERENCES seller (seller_id);

ALTER TABLE thread_message ADD CONSTRAINT fk_thread_message_thread FOREIGN KEY (thread_id) REFERENCES communication_thread (thread_id);
ALTER TABLE thread_message_media ADD CONSTRAINT fk_thread_message_media_message FOREIGN KEY (message_id) REFERENCES thread_message (message_id);