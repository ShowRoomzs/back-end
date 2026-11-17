-- 화장품 개봉 시 청약철회 제한 표시(수정요청 C · 전자상거래법 제17조②③).
--
-- 개봉 · 사용으로 가치가 현저히 줄어드는 화장품은 청약철회 제한 사실을 상품에 분명히 표시해야 제한할 수 있다.
-- 상품별 적용 여부(기본 적용)와 브랜드가 쓰는 안내 문구(없으면 기본 문구)를 둔다.
ALTER TABLE product
    ADD COLUMN opened_withdrawal_restricted BIT(1) NOT NULL DEFAULT b'1' COMMENT '개봉 · 사용 시 청약철회 제한 표시 — 기본 적용' AFTER hide_detail;

ALTER TABLE market
    ADD COLUMN withdrawal_restriction_notice VARCHAR(500) NULL COMMENT '청약철회 제한 안내 문구(브랜드) — 비우면 기본 문구' AFTER brand_site_url;
