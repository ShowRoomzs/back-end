-- 클레임이 기존 테이블에 요구하는 것(35 설계서 1-10). 백필 없음 — 전부 기본값으로 선다.

-- 수량 부분 반품(2개 중 1개)을 항목 분할 없이 기록한다. 정산·판매 집계의 유효 수량 = quantity − returned_quantity.
-- 전량 반품된 항목은 status = 'RETURNED' 로 내린다(VARCHAR(30) 이라 DDL 없음).
ALTER TABLE `order_product`
    ADD COLUMN `returned_quantity` INT NOT NULL DEFAULT 0 COMMENT '반품 검수 통과 수량' AFTER `quantity`;

-- 교환 재발송 도착 시 구매확정 7일 재시작. delivered_at 을 덮지 않는다 — 최초 배송완료 시각은 사실이다.
ALTER TABLE `order_delivery_group`
    ADD COLUMN `confirm_restart_at` DATETIME(6) NULL COMMENT '구매확정 기산점 재시작 — 교환 재발송 도착' AFTER `delivered_at`;
