-- 관리자 상품 공지사항 (전역 Notice 엔티티와 구분: ProductAnnouncement)
CREATE TABLE product_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    exposure_type VARCHAR(20) NOT NULL,
    is_display_period_set BIT NOT NULL DEFAULT 0,
    display_start_date DATETIME(6) NULL,
    display_end_date DATETIME(6) NULL,
    is_popup BIT NOT NULL DEFAULT 0,
    display_status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_announcement_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    announcement_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_announcement_target (announcement_id, product_id),
    CONSTRAINT fk_pat_announcement FOREIGN KEY (announcement_id) REFERENCES product_announcement (id) ON DELETE CASCADE,
    CONSTRAINT fk_pat_product FOREIGN KEY (product_id) REFERENCES product (product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pa_category ON product_announcement (category);
CREATE INDEX idx_pa_display_status ON product_announcement (display_status);
CREATE INDEX idx_pa_created_at ON product_announcement (created_at);
CREATE INDEX idx_pa_exposure_type ON product_announcement (exposure_type);
