-- 상품 공지: 소유 마켓(판매자) — 판매자 API에서만 조회/수정
ALTER TABLE product_announcement
    ADD COLUMN market_id BIGINT NOT NULL;

CREATE INDEX idx_pa_market_id ON product_announcement (market_id);

ALTER TABLE product_announcement
    ADD CONSTRAINT fk_product_announcement_market
    FOREIGN KEY (market_id) REFERENCES market (market_id);
