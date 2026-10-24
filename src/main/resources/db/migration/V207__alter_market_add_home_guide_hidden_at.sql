-- 1017 설계서 4-4 — 파트너센터 홈 첫 공구 가이드 [가이드 숨기기].
-- 가이드 단계 판정(브랜드 정보 · 상품 · 연결 · 계약 · 오픈 · 주문)은 서버가 매번 계산하고 숨김 시각만 저장한다.
ALTER TABLE market
    ADD COLUMN home_guide_hidden_at DATETIME(6) NULL COMMENT '파트너 홈 첫 공구 가이드를 숨긴 시각 — null 이면 표시(오픈 이력이 생기면 저절로 사라진다)';
