-- 어드민 변경 요청 rev.7 — 인플루언서 사업자 구분 변경 편입(§16-2a · 16 설계서 2-1 · 50 설계서 2-4 · 구현 계획서 S5 · 2-2 정본표).
-- 테이블 이름은 그대로 둔다 — 이제 브랜드 + 인플루언서 요청을 함께 든다. 기존 행은 requester_type 기본값 BRAND 로 채워진다(백필 없음).
ALTER TABLE brand_change_request
    ADD COLUMN requester_type      VARCHAR(16)  NOT NULL DEFAULT 'BRAND' COMMENT 'BRAND, CREATOR' AFTER request_code,
    ADD COLUMN creator_id          BIGINT       NULL COMMENT 'CREATOR 요청만' AFTER market_id,
    ADD COLUMN resident_number_enc VARCHAR(255) NULL COMMENT 'TO_INDIVIDUAL 주민등록번호 암호문 — 승인 이관 · 반려 · 취소 즉시 NULL',
    ADD COLUMN version             BIGINT       NOT NULL DEFAULT 0 COMMENT '승인 · 반려 동시 처리 차단',
    MODIFY COLUMN market_id        BIGINT       NULL COMMENT 'BRAND 요청만',
    MODIFY COLUMN type             VARCHAR(20)  NOT NULL COMMENT 'BUSINESS_INFO, SETTLEMENT_ACCOUNT, TO_BUSINESS, TO_INDIVIDUAL',
    MODIFY COLUMN requester_name   VARCHAR(64)  NULL COMMENT '브랜드 담당자 이름 스냅샷 — CREATOR 요청은 NULL(본인)';

-- 열린 요청은 인플루언서당 1건(방향 무관). 브랜드의 uk_brand_change_request_pending 은 market_id 가 NULL 이면 묶이지 않으므로 따로 둔다.
-- 생성 컬럼은 CASE 로 쓴다(V96 — Flyway MySQL 파서가 IF( 를 조건 구문으로 오인한다).
ALTER TABLE brand_change_request
    ADD COLUMN creator_pending_key BIGINT GENERATED ALWAYS AS (CASE WHEN `status` = 'PENDING' THEN `creator_id` ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_brand_change_request_creator_pending (creator_pending_key),
    ADD KEY idx_brand_change_request_creator (creator_id, requested_at),
    ADD CONSTRAINT fk_brand_change_request_creator FOREIGN KEY (creator_id) REFERENCES creator (creator_id),
    ADD CONSTRAINT ck_brand_change_request_requester CHECK (
        (requester_type = 'BRAND'   AND market_id  IS NOT NULL AND creator_id IS NULL) OR
        (requester_type = 'CREATOR' AND creator_id IS NOT NULL AND market_id  IS NULL));
