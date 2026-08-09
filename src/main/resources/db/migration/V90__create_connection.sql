-- 연결·소통(§13/§14) — 브랜드-인플루언서 쌍(PAIR) + 운영자 고정 채널(OPERATOR_MARKET/OPERATOR_CREATOR)을
-- 동일 모델로 취급한다. TYPE으로 세 가지 쌍을 구분한다.
--
-- MARKET_ID_KEY / CREATOR_ID_KEY 는 COALESCE 로 NULL 을 0 으로 치환한 생성 컬럼이다.
-- MySQL/InnoDB 는 UNIQUE 인덱스에서 NULL 을 서로 다른 값으로 취급하므로,
-- CREATOR_ID 가 항상 NULL 인 OPERATOR_MARKET 행이 같은 MARKET_ID 로 중복 생성되는 것을
-- 원본 컬럼의 UNIQUE 제약만으로는 막을 수 없다. 생성 컬럼을 매개로 우회한다.
CREATE TABLE `connection` (
    `connection_id`     BIGINT NOT NULL AUTO_INCREMENT,
    `type`              VARCHAR(20) NOT NULL COMMENT 'PAIR, OPERATOR_MARKET, OPERATOR_CREATOR',
    `market_id`         BIGINT NULL COMMENT 'PAIR·OPERATOR_MARKET 에서만 값 존재',
    `creator_id`        BIGINT NULL COMMENT 'PAIR·OPERATOR_CREATOR 에서만 값 존재',
    `market_id_key`     BIGINT GENERATED ALWAYS AS (COALESCE(`market_id`, 0)) STORED,
    `creator_id_key`    BIGINT GENERATED ALWAYS AS (COALESCE(`creator_id`, 0)) STORED,
    `status`            VARCHAR(20) NOT NULL COMMENT 'REQUESTED, CONNECTED, REJECTED, DISCONNECTED',
    `requested_at`      DATETIME(6) NOT NULL,
    `responded_at`      DATETIME(6) NULL,
    `disconnected_at`   DATETIME(6) NULL,
    `created_at`        DATETIME(6) NOT NULL,
    `modified_at`       DATETIME(6) NOT NULL,
    PRIMARY KEY (`connection_id`),
    UNIQUE KEY `uk_connection_type_market_creator` (`type`, `market_id_key`, `creator_id_key`),
    KEY `idx_connection_creator_status` (`creator_id`, `status`),
    KEY `idx_connection_market_status` (`market_id`, `status`),
    CONSTRAINT `fk_connection_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`),
    CONSTRAINT `fk_connection_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
