-- 포트원 파트너 정산(Platform) 연동 — 44_포트원_파트너정산_연동_BE_설계서.md 3절.
-- 수취자(브랜드 · 인플루언서)의 포트원 파트너 연결과, 지급 행의 포트원 정산건 · 지급 식별자.

ALTER TABLE seller
    ADD COLUMN portone_partner_id           VARCHAR(64)  NULL COMMENT '포트원 Platform 파트너 id — brand-{market_id}(테스트 모드 t- 접두)',
    ADD COLUMN portone_partner_status       VARCHAR(16)  NULL COMMENT 'PENDING, APPROVED, REJECTED — 마지막 조회값',
    ADD COLUMN portone_partner_synced_at    DATETIME(6)  NULL COMMENT '마지막 생성·갱신 성공 시각',
    ADD COLUMN portone_partner_account_hash VARCHAR(64)  NULL COMMENT '포트원에 보낸 계좌의 sha256(bank|number|holder) — 변경 감지';

ALTER TABLE creator
    ADD COLUMN portone_partner_id           VARCHAR(64)  NULL COMMENT 'creator-{creator_id}',
    ADD COLUMN portone_partner_status       VARCHAR(16)  NULL,
    ADD COLUMN portone_partner_synced_at    DATETIME(6)  NULL,
    ADD COLUMN portone_partner_account_hash VARCHAR(64)  NULL;

ALTER TABLE settlement_payout
    ADD COLUMN pg_transfer_id VARCHAR(64) NULL COMMENT '포트원 수기 정산건 id — {settlement_number}-{payee}-{attempt}',
    ADD COLUMN pg_payout_id   VARCHAR(64) NULL COMMENT '포트원 지급 id(결과 조회 뒤 · pg_reference 와 같은 값)',
    ADD KEY idx_settlement_payout_pg_transfer (pg_transfer_id);
