-- 정산 증빙(44 어드민 정산관리 설계서 1-6 · 5절) — 문서 행 4종.
--
-- WITHHOLDING_RECEIPT(원천징수영수증 · 비사업자 · 시스템 생성) · CREATOR_TAX_INVOICE(인플루언서 → 플랫폼 정발행 · 인플루언서 입력
-- → 운영자 대조) · BRAND_TAX_INVOICE(플랫폼 → 브랜드 · 운영자 발행본 등록) · BRAND_TAX_INVOICE_CREDIT(차감 수정세금계산서).
-- 정산 1건에 유형별 1행 — 수정세금계산서만 차감 건마다 1행이다(clawback_id 로 구분). NULL 은 UNIQUE 에서 서로 다르게 취급되므로
-- 생성 컬럼 clawback_key(차감 없으면 0)로 묶는다(V96 pending_key 관례). 반려 → 재제출은 같은 행이다.
CREATE TABLE `settlement_tax_document` (
    `document_id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `settlement_id`           BIGINT        NOT NULL,
    `type`                    VARCHAR(32)   NOT NULL COMMENT 'WITHHOLDING_RECEIPT, CREATOR_TAX_INVOICE, BRAND_TAX_INVOICE, BRAND_TAX_INVOICE_CREDIT',
    `clawback_id`             BIGINT        NULL COMMENT '수정세금계산서의 차감 행(V182) — 그 외 NULL',
    `clawback_key`            BIGINT GENERATED ALWAYS AS (COALESCE(`clawback_id`, 0)) STORED,
    `status`                  VARCHAR(16)   NOT NULL COMMENT 'PENDING_INPUT, SUBMITTED, VERIFIED, REJECTED, PENDING_ISSUE, ISSUED, GENERATED',
    `supply_amount`           BIGINT        NOT NULL COMMENT '공급가 스냅샷',
    `vat_amount`              BIGINT        NOT NULL,
    `total_amount`            BIGINT        NOT NULL,
    `counterparty_name`       VARCHAR(100)  NULL COMMENT '공급받는자(브랜드 건) · 공급자(인플루언서 건) 스냅샷',
    `counterparty_reg_number` VARCHAR(20)   NULL,
    `counterparty_email`      VARCHAR(255)  NULL,
    `approval_number`         VARCHAR(26)   NULL COMMENT '국세청 승인번호 YYYYMMDD-NNNNNNNN-NNNNNNNN',
    `issued_date`             DATE          NULL,
    `due_date`                DATE          NULL COMMENT '브랜드 건 — 공급일이 속한 달의 다음 달 N일',
    `file_key`                VARCHAR(512)  NULL COMMENT 'S3 private — 원천징수영수증 · 브랜드 발행본 · 인플루언서 첨부',
    `file_name`               VARCHAR(255)  NULL,
    `submitted_at`            DATETIME(6)   NULL,
    `submitted_by`            BIGINT        NULL COMMENT '크리에이터 id',
    `verified_at`             DATETIME(6)   NULL,
    `verified_by`             BIGINT        NULL COMMENT '운영자 id',
    `reject_reason`           VARCHAR(32)   NULL COMMENT 'AMOUNT_MISMATCH, RECIPIENT_MISMATCH, NOT_FOUND',
    `rejected_at`             DATETIME(6)   NULL,
    `issued_by`               BIGINT        NULL COMMENT '운영자 id',
    `generated_at`            DATETIME(6)   NULL,
    `created_at`              DATETIME(6)   NOT NULL,
    PRIMARY KEY (`document_id`),
    UNIQUE KEY `uk_settlement_tax_document` (`settlement_id`, `type`, `clawback_key`),
    KEY `idx_settlement_tax_document_status` (`type`, `status`),
    KEY `idx_settlement_tax_document_approval` (`approval_number`),
    CONSTRAINT `fk_settlement_tax_document_settlement` FOREIGN KEY (`settlement_id`)
        REFERENCES `settlement` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 원천세 신고 자료 다운로드 기록(5-5) — 주민등록번호(고유식별정보) 반출 기록. PurchaseOrderDownloadLog 와 같은 꼴.
CREATE TABLE `settlement_withholding_report_log` (
    `log_id`        BIGINT      NOT NULL AUTO_INCREMENT,
    `operator_id`   BIGINT      NOT NULL,
    `report_month`  VARCHAR(7)  NOT NULL COMMENT 'YYYY-MM — 인플루언서 몫 지급 월',
    `row_count`     INT         NOT NULL,
    `downloaded_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`log_id`),
    KEY `idx_settlement_withholding_report_log_month` (`report_month`, `downloaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
