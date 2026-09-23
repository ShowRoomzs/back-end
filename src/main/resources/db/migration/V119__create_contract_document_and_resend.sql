-- 체결 문서 2종(§25-3 #3). 어드민이 모두싸인에서 받아 업로드한다.
-- 교체·삭제 API를 만들지 않는다(§28-6 "서명 원본이 바뀌면 계약의 증거가 사라진다").
-- 유니크 제약이 실수로 인한 중복 업로드까지 함께 막는다.
CREATE TABLE `contract_document` (
    `contract_document_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `contract_id`          BIGINT        NOT NULL,
    `document_type`        VARCHAR(32)   NOT NULL COMMENT 'SIGNED_PDF, AUDIT_TRAIL',
    `s3_key`               VARCHAR(512)  NULL,
    `file_url`             VARCHAR(2048) NULL,
    `original_name`        VARCHAR(255)  NULL,
    `size_bytes`           BIGINT        NULL,
    `content_type`         VARCHAR(100)  NULL,
    `uploaded_by`          BIGINT        NULL COMMENT '어드민',
    `uploaded_at`          DATETIME(6)   NOT NULL,
    PRIMARY KEY (`contract_document_id`),
    CONSTRAINT `uk_contract_document_type` UNIQUE (`contract_id`, `document_type`),
    CONSTRAINT `fk_contract_document_contract` FOREIGN KEY (`contract_id`)
        REFERENCES `contract` (`contract_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [서명 안내 다시 받기]. 요청은 발송이 아니다 — 이 행이 생겨도 계약 상태는 변하지 않는다.
-- 실제 재발송은 어드민이 모두싸인에서 한다.
CREATE TABLE `contract_resend_request` (
    `contract_resend_request_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `contract_id`                BIGINT      NOT NULL,
    `requester_type`             VARCHAR(16) NOT NULL COMMENT 'SELLER, CREATOR',
    `requester_id`               BIGINT      NULL,
    `requested_at`               DATETIME(6) NOT NULL,
    `handled_at`                 DATETIME(6) NULL,
    `handled_by`                 BIGINT      NULL,
    PRIMARY KEY (`contract_resend_request_id`),
    KEY `idx_contract_resend_handled_requested` (`handled_at`, `requested_at`),
    KEY `idx_contract_resend_contract` (`contract_id`, `handled_at`),
    CONSTRAINT `fk_contract_resend_contract` FOREIGN KEY (`contract_id`)
        REFERENCES `contract` (`contract_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
