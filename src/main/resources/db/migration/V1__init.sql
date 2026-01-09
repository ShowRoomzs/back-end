-- Flyway 초기 마이그레이션 스크립트
-- 모든 엔티티 기반 테이블 생성

-- 1. USERS 테이블 (사용자)
CREATE TABLE IF NOT EXISTS USERS (
    USER_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    USERNAME VARCHAR(64) NOT NULL UNIQUE,
    NICKNAME VARCHAR(100) NOT NULL,
    NAME VARCHAR(64),
    PHONE_NUMBER VARCHAR(20),
    PASSWORD VARCHAR(128) NOT NULL,
    EMAIL VARCHAR(512) NOT NULL UNIQUE,
    EMAIL_VERIFIED_YN VARCHAR(1) NOT NULL,
    PROFILE_IMAGE_URL VARCHAR(512),
    GENDER VARCHAR(10),
    BIRTHDAY VARCHAR(10),
    PROVIDER_TYPE VARCHAR(20) NOT NULL,
    ROLE_TYPE VARCHAR(20) NOT NULL,
    CREATED_AT DATETIME(6) NOT NULL,
    MODIFIED_AT DATETIME(6) NOT NULL,
    SERVICE_AGREE BOOLEAN DEFAULT FALSE,
    PRIVACY_AGREE BOOLEAN DEFAULT FALSE,
    MARKETING_AGREE BOOLEAN DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. SELLER 테이블 (판매자)
CREATE TABLE IF NOT EXISTS SELLER (
    SELLER_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    EMAIL VARCHAR(512) NOT NULL UNIQUE,
    PASSWORD VARCHAR(128) NOT NULL,
    NAME VARCHAR(64) NOT NULL,
    PHONE_NUMBER VARCHAR(20),
    ROLE_TYPE VARCHAR(20) NOT NULL,
    STATUS VARCHAR(20) NOT NULL,
    REJECTION_REASON VARCHAR(500),
    CREATED_AT DATETIME(6) NOT NULL,
    MODIFIED_AT DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. MARKET 테이블 (마켓)
CREATE TABLE IF NOT EXISTS MARKET (
    MARKET_ID BIGINT AUTO_INCREMENT PRIMARY KEY,
    SELLER_ID BIGINT NOT NULL,
    MARKET_NAME VARCHAR(255) NOT NULL UNIQUE,
    CS_NUMBER VARCHAR(255) NOT NULL,
    MARKET_IMAGE_URL VARCHAR(512),
    MARKET_IMAGE_STATUS VARCHAR(20) DEFAULT 'APPROVED',
    MARKET_DESCRIPTION VARCHAR(1000),
    MARKET_URL VARCHAR(512),
    MAIN_CATEGORY VARCHAR(100),
    SNS_LINK_1 VARCHAR(512),
    SNS_LINK_2 VARCHAR(512),
    SNS_LINK_3 VARCHAR(512),
    FOREIGN KEY (SELLER_ID) REFERENCES SELLER(SELLER_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. CATEGORY 테이블 (카테고리)
CREATE TABLE IF NOT EXISTS category (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(255) NOT NULL,
    `order` INT,
    icon_url VARCHAR(2048),
    FOREIGN KEY (parent_id) REFERENCES category(category_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. product 테이블 (상품)
CREATE TABLE IF NOT EXISTS product (
    product_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    market_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    seller_product_code VARCHAR(100),
    thumbnail_url VARCHAR(2048),
    regular_price INT NOT NULL,
    sale_price INT NOT NULL,
    purchase_price INT,
    is_display BOOLEAN NOT NULL DEFAULT TRUE,
    is_out_of_stock_forced BOOLEAN NOT NULL DEFAULT FALSE,
    is_recommended BOOLEAN NOT NULL DEFAULT FALSE,
    product_notice JSON,
    description TEXT,
    tags JSON,
    delivery_type VARCHAR(100),
    delivery_fee INT,
    delivery_free_threshold INT,
    delivery_estimated_days INT,
    created_at TIMESTAMP(6) NOT NULL,
    product_number VARCHAR(50) UNIQUE,
    FOREIGN KEY (market_id) REFERENCES MARKET(MARKET_ID) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES category(category_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. product_image 테이블 (상품 이미지)
CREATE TABLE IF NOT EXISTS product_image (
    image_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    `order` INT NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. product_option_group 테이블 (상품 옵션 그룹)
CREATE TABLE IF NOT EXISTS product_option_group (
    option_group_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. product_option 테이블 (상품 옵션)
CREATE TABLE IF NOT EXISTS product_option (
    option_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_group_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    FOREIGN KEY (option_group_id) REFERENCES product_option_group(option_group_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. product_variant 테이블 (상품 변형)
CREATE TABLE IF NOT EXISTS product_variant (
    variant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(255),
    regular_price INT NOT NULL,
    sale_price INT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    is_representative BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (product_id) REFERENCES product(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. variant_option_map 테이블 (변형-옵션 매핑, ManyToMany 조인 테이블)
CREATE TABLE IF NOT EXISTS variant_option_map (
    variant_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    PRIMARY KEY (variant_id, option_id),
    FOREIGN KEY (variant_id) REFERENCES product_variant(variant_id) ON DELETE CASCADE,
    FOREIGN KEY (option_id) REFERENCES product_option(option_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. recent_search 테이블 (최근 검색)
CREATE TABLE IF NOT EXISTS recent_search (
    id BINARY(16) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    term VARCHAR(255) NOT NULL,
    searched_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES USERS(USER_ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. USER_REFRESH_TOKEN 테이블 (사용자 리프레시 토큰)
CREATE TABLE IF NOT EXISTS USER_REFRESH_TOKEN (
    REFRESH_TOKEN_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY,
    USER_ID VARCHAR(64) NOT NULL UNIQUE,
    REFRESH_TOKEN VARCHAR(256) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. ADMIN_REFRESH_TOKEN 테이블 (판매자 리프레시 토큰)
CREATE TABLE IF NOT EXISTS ADMIN_REFRESH_TOKEN (
    REFRESH_TOKEN_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY,
    ADMIN_EMAIL VARCHAR(512) NOT NULL UNIQUE,
    REFRESH_TOKEN VARCHAR(256) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 인덱스 생성 (성능 최적화)
CREATE INDEX idx_users_email ON USERS(EMAIL);
CREATE INDEX idx_users_username ON USERS(USERNAME);
CREATE INDEX idx_seller_email ON SELLER(EMAIL);
CREATE INDEX idx_market_seller_id ON MARKET(SELLER_ID);
CREATE INDEX idx_product_market_id ON product(market_id);
CREATE INDEX idx_product_category_id ON product(category_id);
CREATE INDEX idx_product_image_product_id ON product_image(product_id);
CREATE INDEX idx_product_option_group_product_id ON product_option_group(product_id);
CREATE INDEX idx_product_option_option_group_id ON product_option(option_group_id);
CREATE INDEX idx_product_variant_product_id ON product_variant(product_id);
CREATE INDEX idx_recent_search_user_id ON recent_search(user_id);

