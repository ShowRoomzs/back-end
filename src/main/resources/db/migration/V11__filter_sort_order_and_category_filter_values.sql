ALTER TABLE filter
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0;

ALTER TABLE filter_value
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE category_filter_value (
    category_filter_value_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_filter_id BIGINT NOT NULL,
    filter_value_id BIGINT NOT NULL,
    CONSTRAINT fk_category_filter_value_category_filter FOREIGN KEY (category_filter_id)
        REFERENCES category_filter(category_filter_id) ON DELETE CASCADE,
    CONSTRAINT fk_category_filter_value_filter_value FOREIGN KEY (filter_value_id)
        REFERENCES filter_value(filter_value_id) ON DELETE CASCADE
);

CREATE INDEX idx_category_filter_value_category_filter_id ON category_filter_value(category_filter_id);
CREATE INDEX idx_category_filter_value_filter_value_id ON category_filter_value(filter_value_id);
