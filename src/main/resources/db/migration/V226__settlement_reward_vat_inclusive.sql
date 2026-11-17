-- 리워드 부가세 포함(대표 결정 2026-11-10 · 수정요청 A-5 · 세무 확인 필요).
--
-- 리워드 = 부가세 포함 금액이다. 브랜드가 리워드 + 리워드 부가세 10% 를 따로 부담하던 것을 없앤다. 사업자 인플루언서는 리워드 안에
-- 부가세가 들어 있다(공급가 = 리워드 ÷ 1.1). 정산 행마다 생성 시점의 규칙을 스냅샷으로 적는다 — 기존 행(0)은 예전 산식으로 재계산 ·
-- 클로백 역산된다. 새 정산은 설정값(settlement.reward-vat-inclusive · 기본 true)을 적는다.
ALTER TABLE settlement
    ADD COLUMN reward_vat_inclusive BIT(1) NOT NULL DEFAULT b'0'
        COMMENT '리워드 부가세 포함 — 1이면 리워드에 부가세가 들어 있다(브랜드 별도 부담 없음 · 사업자 공급가 = 리워드 ÷ (1 + 부가세율))'
        AFTER reward_vat_amount;
