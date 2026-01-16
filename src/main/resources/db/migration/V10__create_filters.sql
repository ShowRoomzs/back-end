CREATE TABLE filter (
    filter_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filter_key VARCHAR(100) NOT NULL UNIQUE,
    label VARCHAR(255) NOT NULL,
    filter_type VARCHAR(20) NOT NULL,
    condition_type VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE filter_value (
    filter_value_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filter_id BIGINT NOT NULL,
    value VARCHAR(255) NOT NULL,
    label VARCHAR(255) NOT NULL,
    extra VARCHAR(255),
    CONSTRAINT fk_filter_value_filter FOREIGN KEY (filter_id)
        REFERENCES filter(filter_id) ON DELETE CASCADE
);

CREATE TABLE category_filter (
    category_filter_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    filter_id BIGINT NOT NULL,
    CONSTRAINT fk_category_filter_category FOREIGN KEY (category_id)
        REFERENCES category(category_id) ON DELETE CASCADE,
    CONSTRAINT fk_category_filter_filter FOREIGN KEY (filter_id)
        REFERENCES filter(filter_id) ON DELETE CASCADE
);

CREATE INDEX idx_filter_value_filter_id ON filter_value(filter_id);
CREATE INDEX idx_category_filter_category_id ON category_filter(category_id);
CREATE INDEX idx_category_filter_filter_id ON category_filter(filter_id);
