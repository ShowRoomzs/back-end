-- 취소 요청 응답 기한 1영업일 · 자동 승인 · 거부 사유 드롭다운(1009 기획 수정본 3절 · 거래 관리 결정 8 · 15).
--
-- 브랜드가 요청 + 1영업일(주말·공휴일 제외)의 끝까지 승인·거부하지 않으면 시스템이 승인하고 PG 가 즉시 환불한다.
-- 기한은 요청 시점 스냅샷이다. 거부 사유는 드롭다운 코드 + 상세(기타면 필수)로 받는다 — 기존 reject_reason 은 상세로 쓴다.
ALTER TABLE order_cancel_request
    ADD COLUMN respond_due_at     DATETIME(6) NULL COMMENT '응답 기한 — 요청 + 1영업일의 끝. 지나면 자동 승인' AFTER requested_at,
    ADD COLUMN auto_approved      BIT(1)      NOT NULL DEFAULT b'0' COMMENT '응답 기한 경과로 시스템이 승인' AFTER respond_due_at,
    ADD COLUMN reject_reason_code VARCHAR(30) NULL COMMENT 'ALREADY_PACKED, PICKED_UP, MADE_TO_ORDER, ETC' AFTER reject_reason;

-- 기존 행 — 요청 + 1일로 채운다(운영 데이터는 테스트 주문뿐이다). 검토 중이면 다음 배치 회차에 자동 승인된다.
UPDATE order_cancel_request SET respond_due_at = TIMESTAMPADD(DAY, 1, requested_at) WHERE respond_due_at IS NULL;

ALTER TABLE order_cancel_request
    MODIFY COLUMN respond_due_at DATETIME(6) NOT NULL COMMENT '응답 기한 — 요청 + 1영업일의 끝. 지나면 자동 승인';

CREATE INDEX idx_order_cancel_request_respond_due ON order_cancel_request (status, respond_due_at);
