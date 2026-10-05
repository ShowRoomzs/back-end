-- 자동 이행 이력의 detail이 enum 원문(SELLER · CREATOR)으로 저장되던 것을 라벨로 보정한다(30-1 5절).
-- 답하지 않은 측을 적는다 — 행위자는 SYSTEM 그대로다. 다시 실행해도 결과가 같다.
UPDATE `group_buy_history`
SET `detail` = CASE `detail`
        WHEN 'SELLER'  THEN '브랜드 무응답으로 자동 이행'
        WHEN 'CREATOR' THEN '인플루언서 무응답으로 자동 이행'
    END
WHERE `event_type` = 'FULFILLMENT_AUTO_CONFIRMED'
  AND `detail` IN ('SELLER', 'CREATOR');
