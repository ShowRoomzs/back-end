-- 택배 스캔 이력(앱 클레임 설계서 1-6) — 소비자 앱 배송 조회·회수 조회가 읽는다. 화면은 택배 API 를 부르지 않는다.
--
-- 송장이 키다 — 주문 송장 · 회수 송장 · 재발송 송장이 한 테이블을 쓴다(소유자 FK 없음).
-- seq 는 그 송장 이력의 시간순 번호(0부터)다. 추적 배치가 저장된 건수 뒤의 이력만 넣고, UNIQUE 가 중복을 막는다.
-- 송장이 정정돼도 구 송장 행은 지우지 않는다 — 읽는 쪽이 현재 송장으로만 조회한다.
CREATE TABLE `delivery_tracking_event` (
    `event_id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `carrier`         VARCHAR(30)  NOT NULL,
    `tracking_number` VARCHAR(50)  NOT NULL,
    `seq`             INT          NOT NULL,
    `occurred_at`     DATETIME(6)  NOT NULL,
    `location`        VARCHAR(100) NULL COMMENT '연동 업체 원문(where)',
    `description`     VARCHAR(100) NULL COMMENT '연동 업체 원문(kind) — 번역하지 않는다',
    `level`           TINYINT      NULL COMMENT '진행 단계 0~6',
    PRIMARY KEY (`event_id`),
    CONSTRAINT `uk_delivery_tracking_event` UNIQUE (`carrier`, `tracking_number`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
