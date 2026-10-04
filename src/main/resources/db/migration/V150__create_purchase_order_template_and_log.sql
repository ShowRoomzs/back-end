-- 발주서 컬럼 구성(마켓당 1행 · 기본정보 관리와 공유)과 다운로드 이력(34 설계서 1-8 · §34-13 #14 선반영).
CREATE TABLE `market_purchase_order_template` (
    `template_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `market_id`   BIGINT       NOT NULL,
    `columns`     VARCHAR(500) NOT NULL COMMENT 'PurchaseOrderColumn 코드 CSV — 저장 순서 = 엑셀 좌→우 열 순서',
    `updated_by`  BIGINT       NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    `modified_at` DATETIME(6)  NOT NULL,
    PRIMARY KEY (`template_id`),
    CONSTRAINT `uk_market_purchase_order_template` UNIQUE (`market_id`),
    CONSTRAINT `fk_market_purchase_order_template_market` FOREIGN KEY (`market_id`) REFERENCES `MARKET` (`MARKET_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 발주서 반출 이력 — 엑셀로 개인정보가 나간다. 기록만 하고 조회 화면은 어드민 몫이다.
CREATE TABLE `purchase_order_download_log` (
    `download_log_id`      BIGINT       NOT NULL AUTO_INCREMENT,
    `market_id`            BIGINT       NOT NULL,
    `seller_id`            BIGINT       NOT NULL,
    `delivery_group_count` INT          NOT NULL,
    `columns`              VARCHAR(500) NOT NULL COMMENT '어떤 컬럼으로 — 개인정보 포함 여부가 컬럼에 있다',
    `prepare_started`      BIT(1)       NOT NULL DEFAULT 0 COMMENT '다운로드와 함께 준비 시작 처리 여부',
    `downloaded_at`        DATETIME(6)  NOT NULL,
    PRIMARY KEY (`download_log_id`),
    KEY `idx_purchase_order_download_log_market` (`market_id`, `downloaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
