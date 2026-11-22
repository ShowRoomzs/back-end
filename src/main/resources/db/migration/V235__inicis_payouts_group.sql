-- KG이니시스 지급대행(신버전 AML) 전환 — 60_KG이니시스_지급대행_전환_계획서.md 3절(계획서의 V233 · V234 는 다른 변경이 먼저 썼다).
-- 이니시스는 지급 1건을 우리 참조 id 로 식별하지 않는다 — (서브몰 idMall, 지급일 dtPay, 차수 clService) 로만 가르고 같은 키로 다시 올리면
-- 마지막 금액이 최종이다. 그래서 「수취자 · 지급일 = 지급 묶음 1개」를 도메인 개념으로 둔다(D-1).

-- 3-1. 수취자 ↔ PG 서브몰 연결 — PG 구분 · AML 상태 · 브랜드 대표자 생년월일
ALTER TABLE seller
    ADD COLUMN pg_partner_provider          VARCHAR(16)  NULL COMMENT 'TOSS | INICIS — 현재 어댑터와 다르면 연결 없음',
    ADD COLUMN pg_aml_status                VARCHAR(16)  NULL COMMENT '이니시스 amlstatus 원시값 — 지급 가능(pg_partner_status)과 별개 축',
    ADD COLUMN pg_aml_checked_at            DATETIME(6)  NULL,
    ADD COLUMN representative_birthdate_enc VARCHAR(255) NULL COMMENT '대표자 생년월일 yyyyMMdd 암호문 — 이니시스 서브몰 bthDt(필수)';
UPDATE seller SET pg_partner_provider = 'TOSS' WHERE pg_partner_id IS NOT NULL;

ALTER TABLE creator
    ADD COLUMN pg_partner_provider VARCHAR(16) NULL COMMENT 'TOSS | INICIS',
    ADD COLUMN pg_aml_status       VARCHAR(16) NULL COMMENT '이니시스 amlstatus 원시값',
    ADD COLUMN pg_aml_checked_at   DATETIME(6) NULL;
UPDATE creator SET pg_partner_provider = 'TOSS' WHERE pg_partner_id IS NOT NULL;

-- 3-2. 지급 묶음(D-1) — 같은 수취자 · 지급일 · 차수의 행을 합계 1건으로 PG 에 올린다
CREATE TABLE settlement_payout_group (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    provider         VARCHAR(16)  NOT NULL COMMENT 'INICIS | SIMULATED',
    payee            VARCHAR(16)  NOT NULL COMMENT 'BRAND | CREATOR — 플랫폼 몫은 묶지 않는다(내부 계정 · 즉시 PAID)',
    payee_ref_id     BIGINT       NOT NULL COMMENT 'BRAND = market_id · CREATOR = creator_id',
    pay_date         DATE         NOT NULL COMMENT 'dtPay(돈이 나가는 날)',
    schedule_type    VARCHAR(10)  NOT NULL COMMENT 'NEXT_DAY(clService 0) | SAME_DAY(2) | IMMEDIATE(시뮬레이터)',
    seq              INT          NOT NULL DEFAULT 0 COMMENT '같은 키에 닫힌 묶음이 있을 때 +1 — 이니시스에선 드물다(지급일이 과거) · SIMULATED 당일 재분배용',
    pg_partner_id    VARCHAR(64)  NULL     COMMENT 'idMall — SIMULATED 는 NULL',
    account_hash     VARCHAR(64)  NOT NULL COMMENT '묶음 계좌 지문 — 다른 계좌 행은 합류하지 못한다',
    intended_amount  BIGINT       NOT NULL COMMENT '마지막으로 올리려 한 합계 — 전송 전에 커밋',
    confirmed_amount BIGINT       NULL     COMMENT 'PG 가 받은 마지막 합계 — 결과 모름이면 이전 값 유지 · 송금 결과 대조 기준',
    status           VARCHAR(16)  NOT NULL COMMENT 'OPEN | DELETED | PAID | FAILED',
    result_code      VARCHAR(32)  NULL     COMMENT '동기 code 또는 clPay · AMOUNT_MISMATCH',
    remitted_on      DATE         NULL     COMMENT 'remitDtPay',
    last_request_id  VARCHAR(64)  NULL     COMMENT 'X-Request-ID — 이니시스 문의용',
    submitted_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payout_group (provider, payee, payee_ref_id, pay_date, schedule_type, seq),
    KEY idx_payout_group_status_date (status, pay_date)
) COMMENT '정산 지급 묶음 — 60 이니시스 전환 계획서 D-1';

-- 3-3. 수취자 행 — 지시한 PG · 속한 묶음. pg_transfer_id 인덱스(idx_settlement_payout_pg_transfer)는 V183 에 이미 있다.
ALTER TABLE settlement_payout
    ADD COLUMN pg_provider     VARCHAR(16) NULL COMMENT 'TOSS | INICIS — 지시한 PG(SIMULATED 는 NULL)',
    ADD COLUMN payout_group_id BIGINT      NULL COMMENT '속한 지급 묶음 — 묶음에서 빠지면 NULL',
    ADD KEY idx_settlement_payout_group (payout_group_id);
UPDATE settlement_payout SET pg_provider = 'TOSS' WHERE pg_transfer_id IS NOT NULL;
