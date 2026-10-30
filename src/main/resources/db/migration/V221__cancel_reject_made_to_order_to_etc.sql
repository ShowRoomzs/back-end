-- 취소 거부 사유 「주문 제작·맞춤 상품」(MADE_TO_ORDER) 폐기(1021 기-33 · 58 계획 6-4).
-- 저장된 행은 「기타」로 옮기고, 상세가 비어 있으면 원래 라벨을 상세로 남긴다 — 소비자에게 보인 사유가 사라지지 않게.
-- enum 값 삭제(CancelRejectReason.MADE_TO_ORDER)와 같은 배포에 나가야 한다 — 먼저 지우면 이 행들을 읽지 못한다.
UPDATE order_cancel_request
   SET reject_reason_code = 'ETC',
       reject_reason      = COALESCE(NULLIF(TRIM(reject_reason), ''), '주문 제작·맞춤 상품')
 WHERE reject_reason_code = 'MADE_TO_ORDER';

ALTER TABLE order_cancel_request
    MODIFY COLUMN reject_reason_code VARCHAR(30) NULL COMMENT 'ALREADY_PACKED, PICKED_UP, ETC';
