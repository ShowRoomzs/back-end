-- 토스페이먼츠 지급대행(Payouts v2) 전환 — 44_토스페이먼츠_지급대행_연동_BE_설계서.md 3절.
-- 포트원 실호출 0회 상태라 V183 열에 데이터가 없다 — 이름만 PG 중립으로 바꾸고 토스에 필요한 열을 더한다.

-- 3-1. 수취자 ↔ PG 셀러 연결
ALTER TABLE seller
    RENAME COLUMN portone_partner_id           TO pg_partner_id,
    RENAME COLUMN portone_partner_status       TO pg_partner_status,
    RENAME COLUMN portone_partner_synced_at    TO pg_partner_synced_at,
    RENAME COLUMN portone_partner_account_hash TO pg_partner_account_hash;
ALTER TABLE seller
    MODIFY COLUMN pg_partner_id     VARCHAR(64) NULL COMMENT '토스 발급 셀러 id(≤35)',
    MODIFY COLUMN pg_partner_status VARCHAR(32) NULL COMMENT 'APPROVAL_REQUIRED, PARTIALLY_APPROVED, KYC_REQUIRED, APPROVED — 토스 원시값',
    ADD COLUMN pg_partner_ref VARCHAR(20) NULL COMMENT 'refSellerId — brand-{market_id}(테스트 t- 접두) · 7~20자',
    ADD COLUMN pg_partner_env VARCHAR(8)  NULL COMMENT 'TEST | LIVE — 현재 모드와 다르면 연결 없음으로 본다',
    ADD KEY idx_seller_pg_partner (pg_partner_id);

ALTER TABLE creator
    RENAME COLUMN portone_partner_id           TO pg_partner_id,
    RENAME COLUMN portone_partner_status       TO pg_partner_status,
    RENAME COLUMN portone_partner_synced_at    TO pg_partner_synced_at,
    RENAME COLUMN portone_partner_account_hash TO pg_partner_account_hash;
ALTER TABLE creator
    MODIFY COLUMN pg_partner_id     VARCHAR(64) NULL COMMENT '토스 발급 셀러 id(≤35)',
    MODIFY COLUMN pg_partner_status VARCHAR(32) NULL COMMENT 'APPROVAL_REQUIRED, PARTIALLY_APPROVED, KYC_REQUIRED, APPROVED — 토스 원시값',
    ADD COLUMN pg_partner_ref VARCHAR(20) NULL COMMENT 'refSellerId — creator-{creator_id}',
    ADD COLUMN pg_partner_env VARCHAR(8)  NULL,
    ADD KEY idx_creator_pg_partner (pg_partner_id);

-- 3-2. 지급 행 — 열은 그대로, 뜻이 바뀐다
ALTER TABLE settlement_payout
    MODIFY COLUMN pg_transfer_id VARCHAR(64) NULL COMMENT 'refPayoutId — {settlement_number}-{payee}-{attempt}(우리 발급 · 멱등 키 원본)',
    MODIFY COLUMN pg_payout_id   VARCHAR(64) NULL COMMENT '토스 지급 id — 요청 응답 즉시 기록 · pg_reference 와 같은 값',
    ADD COLUMN pg_schedule_type VARCHAR(10) NULL COMMENT 'EXPRESS | SCHEDULED',
    ADD COLUMN pg_payout_date   DATE        NULL COMMENT '토스 payoutDate — SCHEDULED 면 수취자 화면 「{날짜} 지급 예정」',
    ADD KEY idx_settlement_payout_pg_payout (pg_payout_id);

-- 3-3. 웹훅 수신 로그 — eventId 멱등
CREATE TABLE settlement_payout_webhook_event (
    event_id    VARCHAR(64)  NOT NULL COMMENT '토스 eventId — 멱등 키',
    event_type  VARCHAR(32)  NOT NULL COMMENT 'payout.changed | seller.changed',
    entity_id   VARCHAR(64)  NULL     COMMENT 'entityBody.id — 지급 id | 셀러 id',
    received_at DATETIME(6)  NOT NULL,
    result      VARCHAR(16)  NOT NULL COMMENT 'APPLIED | IGNORED | FAILED',
    body        TEXT         NULL     COMMENT '원문(웹훅은 평문)',
    PRIMARY KEY (event_id),
    KEY idx_payout_webhook_entity (entity_id)
) COMMENT '토스 지급대행 웹훅 수신 — 결제의 payment_webhook_event 와 같은 꼴';
