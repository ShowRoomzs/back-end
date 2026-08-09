-- §15-2 브랜드 사이트 링크. 기존 MARKET_URL(MarketService.createMarket이 자동 생성하는 쇼룸 주소)과는
-- 별개의 필드다 — 브랜드가 직접 입력하는 외부 사이트 링크만 담는다.
ALTER TABLE market
    ADD COLUMN brand_site_url VARCHAR(512) NULL;
