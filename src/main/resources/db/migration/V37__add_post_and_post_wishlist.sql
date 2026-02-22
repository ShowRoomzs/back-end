-- post 테이블 생성
CREATE TABLE post (
    post_id BIGINT NOT NULL AUTO_INCREMENT,
    market_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    image_url VARCHAR(512),
    view_count BIGINT NOT NULL DEFAULT 0,
    is_display TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) DEFAULT NULL,
    modified_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_market FOREIGN KEY (market_id) REFERENCES market (market_id)
) ENGINE=InnoDB;

-- post_wishlist 테이블 생성
CREATE TABLE post_wishlist (
    post_wishlist_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (post_wishlist_id),
    UNIQUE KEY post_wishlist_uk (user_id, post_id),
    CONSTRAINT fk_post_wishlist_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_post_wishlist_post FOREIGN KEY (post_id) REFERENCES post (post_id)
) ENGINE=InnoDB;

-- 인덱스 생성 (조회 성능 최적화)
CREATE INDEX idx_post_market_id ON post (market_id);
CREATE INDEX idx_post_created_at ON post (created_at);
CREATE INDEX idx_post_wishlist_post_id ON post_wishlist (post_id);
