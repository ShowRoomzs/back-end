-- 탈퇴 반려 사유 enum 정리(55 계획서 M7 · ui-admin-15 rev.7 · ui-admin-18 rev.2 M3) — 데이터만 옮긴다. 스키마 변경 없음.
--
-- reject_reason 은 enum 이름을 그대로 저장한다(VARCHAR(40)). 코드에서 값을 지우면 옛 값이 남은 행을 읽을 때 엔티티 로딩이 실패하므로
-- 같은 배포에서 옮긴다. 처리 이력 detail 은 당시 문장 스냅샷이라 그대로 둔다.
--
-- 브랜드: 반품·교환 처리 미완료 · 미배송 주문 잔존 → 미종결 주문 잔존(조건 enum 과 같은 이름)
UPDATE brand_withdrawal_request
   SET reject_reason = 'UNCLOSED_ORDERS'
 WHERE reject_reason IN ('CLAIM_UNRESOLVED', 'UNSHIPPED_ORDERS');

-- 인플루언서: 미배송 주문 처리 미완료 → 끝나지 않은 공구 남음(조건 ①이 미종결 주문을 포함한다 · D17)
UPDATE creator_withdrawal_request
   SET reject_reason = 'GROUP_BUY_REMAINING'
 WHERE reject_reason = 'UNDELIVERED_ORDER';
