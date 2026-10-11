-- 어드민 거래 관리 이력 detail 확장(500 → 1000).
--
-- 운영자 조치 이력은 서버 문구에 운영자 입력을 이어 붙인다 — 반려 이의 인용 「N원 · 근거(500자)」, 검수 무응답
-- 「N원 · 자동 알림 N회 무응답 · 근거(500자)」, 수동 완료 기록 「RFD-N · 수동 완료 기록 · 취소번호(100자) · 근거(500자)」.
-- 입력 한도를 꽉 채우면 500자를 넘어 조치 전체가 실패했다(43 테스트 상세 OB-A06). 근거를 잘라 저장하지 않도록 컬럼을 넓힌다.
-- VARCHAR 길이 접두가 이미 2바이트(> 255바이트)라 InnoDB 가 테이블 복사 없이 바꾼다.
ALTER TABLE order_claim_history
    MODIFY COLUMN detail VARCHAR(1000) NULL;

ALTER TABLE order_fulfillment_history
    MODIFY COLUMN detail VARCHAR(1000) NULL COMMENT '송장 수정 「구 → 신」 · 직권 취소 사유 · 운영자 조치 근거 등';
