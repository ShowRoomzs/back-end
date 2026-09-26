-- 이력을 만든 사실 행의 id — 요청 · 통지 · 확인(31 설계 6-2).
-- 스튜디오는 브랜드 요청의 메모를 내리지 않는다. detail 문자열을 잘라 「사유 라벨만」 만들면 detail 문형이 바뀌는 날
-- 메모가 조용히 샌다 — 원천 행의 reason_code에서 라벨을 다시 만든다.
ALTER TABLE `group_buy_history`
    ADD COLUMN `ref_id` BIGINT NULL COMMENT '요청·통지·확인 행 id' AFTER `detail`;
