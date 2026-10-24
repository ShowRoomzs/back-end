-- 파트너 온보딩 §6 · 기본정보 §15-10 — 제주 추가비를 도서산간 추가비에서 분리한다(06 설계서 1-2 · 1-3).
-- remote_area_surcharge 는 그대로 두고 의미만 「제주를 뺀 도서산간」으로 좁힌다.
ALTER TABLE market
    ADD COLUMN jeju_surcharge INT NOT NULL DEFAULT 0 COMMENT '제주 추가비(원) — 미입력 0';

-- 기존 한 칸(도서산간)은 제주를 포함한 값이었다 — 제주에도 같은 값을 넣어 소비자 표시가 바뀌지 않게 한다.
UPDATE market SET jeju_surcharge = remote_area_surcharge;
