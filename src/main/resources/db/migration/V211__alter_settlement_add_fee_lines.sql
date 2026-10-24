-- 1021 설계서 6-1 — 정산 분해 줄: 「+ 반품 시 차감한 최초 배송비」(기-18) · 「± 운임 정산」(기-93).
-- 위치는 리워드 부가세 다음 · 브랜드 수취액 바로 위. 리워드 기준(확정 거래액)에는 영향이 없다.
ALTER TABLE `settlement`
    ADD COLUMN `return_deduction_fee_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '+ 반품 시 차감한 최초 배송비',
    ADD COLUMN `return_deduction_count` INT NOT NULL DEFAULT 0,
    ADD COLUMN `freight_adjustment_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '± 운임 정산 — 부호 있음',
    ADD COLUMN `freight_adjustment_count` INT NOT NULL DEFAULT 0;
