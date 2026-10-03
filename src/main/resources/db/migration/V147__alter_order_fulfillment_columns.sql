-- 파트너센터 주문 관리(34 설계서 1-1 · 1-6) — 하위주문 이행 상태는 order_delivery_group 에 둔다.
-- 새 테이블을 만들지 않는다: 하위주문(공구·브랜드별 배송 단위)은 결제 모듈이 이미 이 테이블로 깔아 두었다.
ALTER TABLE `order_delivery_group`
    ADD COLUMN `sub_order_number`     VARCHAR(40)  NULL COMMENT '{order_number}-NN — PAID 전이 때 발급' AFTER `group_buy_number`,
    ADD COLUMN `fulfillment_status`   VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, NEW, PREPARING, SHIPPING, RETURNING, DELIVERED, CONFIRMED, CANCELLED',
    ADD COLUMN `ship_due_at`          DATETIME(6)  NULL COMMENT '발송기한 — paid_at + market.shipping_lead_days 스냅샷(기산은 결제완료)',
    ADD COLUMN `prepare_started_at`   DATETIME(6)  NULL COMMENT '발주확인일 — 되돌리기 없음',
    ADD COLUMN `prepare_started_by`   BIGINT       NULL,
    ADD COLUMN `carrier`              VARCHAR(30)  NULL COMMENT 'DeliveryCarrier 11종 — 자유 입력 없음',
    ADD COLUMN `tracking_number`      VARCHAR(50)  NULL COMMENT '정제 후(숫자만) 저장',
    ADD COLUMN `shipped_at`           DATETIME(6)  NULL COMMENT '송장 등록 확정 = 배송중 전환 = 발송기한 판정값. 송장 수정으로 바뀌지 않는다',
    ADD COLUMN `tracking_alert`       VARCHAR(30)  NULL COMMENT 'PICKUP_UNCONFIRMED(집화 확인 필요), STALLED(추적 정지) — 감시 배치가 쓰고 지운다',
    ADD COLUMN `last_tracking_at`     DATETIME(6)  NULL COMMENT '추적 이벤트 최종 갱신',
    ADD COLUMN `return_detected_at`   DATETIME(6)  NULL COMMENT '반송 코드 감지 — 사유는 저장하지 않는다(API가 코드·시각만 준다)',
    ADD COLUMN `return_completed_at`  DATETIME(6)  NULL COMMENT '반송 완료 입고 — 운영자 환불 큐 편입',
    ADD COLUMN `delivered_at`         DATETIME(6)  NULL COMMENT '구매확정·정산의 기준값',
    ADD COLUMN `delivered_source`     VARCHAR(16)  NULL COMMENT 'TRACKER(자동 확인), ADMIN(운영자 처리) — 출처 병기는 항상',
    ADD COLUMN `delivered_by`         BIGINT       NULL COMMENT 'ADMIN 처리자',
    ADD COLUMN `confirmed_at`         DATETIME(6)  NULL COMMENT '구매확정 — 배송완료 + 7일 자동',
    ADD COLUMN `cancelled_at`         DATETIME(6)  NULL,
    ADD COLUMN `cancel_type`          VARCHAR(30)  NULL COMMENT 'CONSUMER, REQUEST_APPROVED, SELLER_DIRECT — 취소 탭 「취소 사유」 열',
    ADD COLUMN `cancel_reason_code`   VARCHAR(30)  NULL COMMENT '직권 취소 사유 — SELLER_DIRECT 만 채운다',
    ADD COLUMN `cancel_reason_detail` VARCHAR(300) NULL COMMENT '소비자에게 그대로 전달되는 설명(약관 제18조②)',
    ADD COLUMN `status_at_cancel`     VARCHAR(30)  NULL COMMENT '취소 당시 이행 상태 — 신규 탭 직권 취소 허용 미결(#1)의 데이터 분리',
    ADD INDEX `idx_odg_market_status` (`market_id`, `fulfillment_status`),
    ADD INDEX `idx_odg_status_shipped` (`fulfillment_status`, `shipped_at`),
    ADD INDEX `idx_odg_status_delivered` (`fulfillment_status`, `delivered_at`),
    -- UNIQUE 가 아니다 — 택배사는 송장번호를 재사용한다. 전역 중복은 종결 전 상태만 서비스가 검사한다.
    ADD INDEX `idx_odg_carrier_tracking` (`carrier`, `tracking_number`);

-- 항목 취소 메타 — 상태 4종(OrderProductStatus)은 늘리지 않는다. 취소의 사실만 더한다.
ALTER TABLE `order_product`
    ADD COLUMN `cancelled_at` DATETIME(6) NULL,
    ADD COLUMN `cancel_type`  VARCHAR(30) NULL COMMENT '그룹과 같은 3종 — 부분 취소는 항목에만 남는다';
