-- 브랜드 기본정보 변경 요청(§15/§16) — M1(사업자 정보)·M2(정산 계좌) 두 유형을 하나의 모델로 취급한다.
-- 레코드는 {항목, 현재값, 요청값} 배열 1개로 두 유형 모두 담는다(§15-6) — 유형별 테이블을 나누지 않는다.
--
-- PENDING_KEY는 IF(status='PENDING', 1, NULL)로 만든 생성 컬럼이다.
-- MySQL/InnoDB는 UNIQUE 인덱스에서 NULL을 서로 다른 값으로 취급하므로, PENDING이 아닌 행은
-- PENDING_KEY가 NULL이 되어 몇 건이든 쌓이고 PENDING인 행만 (market_id, type)당 1건으로 묶인다.
-- V90__create_connection.sql이 COALESCE 생성 컬럼으로 같은 함정을 우회한 것과 동일한 기법이다.
CREATE TABLE `brand_change_request` (
    `request_id`              BIGINT NOT NULL AUTO_INCREMENT,
    `request_code`            VARCHAR(20) NOT NULL,
    `market_id`               BIGINT NOT NULL,
    `type`                    VARCHAR(20) NOT NULL COMMENT 'BUSINESS_INFO, SETTLEMENT_ACCOUNT',
    `status`                  VARCHAR(20) NOT NULL COMMENT 'PENDING, APPROVED, REJECTED, CANCELED',
    `pending_key`             BIGINT GENERATED ALWAYS AS (IF(`status` = 'PENDING', 1, NULL)) STORED,
    `reason`                  VARCHAR(500) NULL,
    `requester_name`          VARCHAR(64) NOT NULL,
    `evidence_file_url`       VARCHAR(1024) NOT NULL,
    `evidence_file_name`      VARCHAR(255) NOT NULL,
    `evidence_file_size`      BIGINT NOT NULL,
    `requested_at`            DATETIME(6) NOT NULL,
    `processed_at`            DATETIME(6) NULL,
    `processed_by`            BIGINT NULL,
    `reject_reason`           VARCHAR(64) NULL,
    `reject_reason_detail`    VARCHAR(500) NULL,
    `result_acknowledged_at`  DATETIME(6) NULL,
    `created_at`              DATETIME(6) NOT NULL,
    `modified_at`             DATETIME(6) NOT NULL,
    PRIMARY KEY (`request_id`),
    UNIQUE KEY `uk_brand_change_request_code` (`request_code`),
    UNIQUE KEY `uk_brand_change_request_pending` (`market_id`, `type`, `pending_key`),
    KEY `idx_brand_change_request_status` (`status`, `requested_at`),
    CONSTRAINT `fk_brand_change_request_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `brand_change_request_item` (
    `item_id`           BIGINT NOT NULL AUTO_INCREMENT,
    `request_id`        BIGINT NOT NULL,
    `field_key`         VARCHAR(40) NOT NULL,
    `current_value`     VARCHAR(255) NULL,
    `requested_value`   VARCHAR(255) NULL,
    `sort_order`        INT NOT NULL,
    PRIMARY KEY (`item_id`),
    KEY `idx_brand_change_request_item_request` (`request_id`),
    CONSTRAINT `fk_brand_change_request_item_request` FOREIGN KEY (`request_id`) REFERENCES `brand_change_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
