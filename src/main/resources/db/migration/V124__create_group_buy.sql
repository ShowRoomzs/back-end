-- §29~§33 공구 관리 — 공구 본체.
-- 공구는 파트너센터의 것이 아니라 파트너·스튜디오·어드민 3서피스가 공유하는 하나의 객체다(설계서 0-1).
-- 파트너 API만 먼저 열지만 컬럼은 어드민 판정·스튜디오 액션이 쓸 것까지 전부 판다.
--
-- 계약 조건(공구명·상품·고정 지급비·콘텐츠 의무)은 복사하지 않고 참조한다(설계서 0-3) —
-- 참조 대상인 계약이 CONCLUDED 이후 불변이기 때문이다. 공구가 소유하는 값은 기간 하나다.
CREATE TABLE `group_buy` (
    `group_buy_id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `group_buy_number`             VARCHAR(30)   NOT NULL COMMENT 'GB-YYYYMMDD-NNN — 생성일(=체결일) 기준(설계서 1-10)',
    `contract_id`                  BIGINT        NOT NULL COMMENT '계약 1건 = 공구 1건의 이쪽 편 보증',
    `market_id`                    BIGINT        NOT NULL COMMENT '계약에서 복사 — 목록 인덱스용 비정규화(불변)',
    `creator_id`                   BIGINT        NOT NULL COMMENT '계약에서 복사 — 목록 인덱스용 비정규화(불변)',
    `status`                       VARCHAR(32)   NOT NULL COMMENT 'PREPARING, READY, IN_PROGRESS, SUSPENSION_SCHEDULED, ENDED, SETTLED, SUSPENDED',

    `start_at`                     DATETIME(6)   NOT NULL COMMENT '계약에서 복사 · 불변',
    `end_at`                       DATETIME(6)   NOT NULL COMMENT '현재 종료 예정 — 연장 수락 시에만 바뀐다',
    `ended_at`                     DATETIME(6)   NULL COMMENT '실제 종결 시각(종료·조기 마감·중단 공통)',
    `close_type`                   VARCHAR(24)   NULL COMMENT 'COMPLETED, EARLY_CLOSED, SUSPENDED — §29-2 종결 3종',
    `closing_change_request_id`    BIGINT        NULL COMMENT '종결을 만든 요청 — B7 「내 요청 사유」',
    `closing_admin_suspension_id`  BIGINT        NULL COMMENT '종결을 만든 직권 중단 — B7a 「운영자 근거」',

    `stock_confirmed_at`           DATETIME(6)   NULL COMMENT '게이트 ① 최소 물량 확보 확인 — 제25조 제재 판정의 증거',
    `stock_confirmed_by`           BIGINT        NULL COMMENT '확인한 셀러 id',
    `ready_at`                     DATETIME(6)   NULL COMMENT '게이트 3개 충족 시각',
    `opened_at`                    DATETIME(6)   NULL COMMENT '스케줄러가 실제로 연 시각',

    `fulfillment_due_at`           DATETIME(6)   NULL COMMENT '이행 확인 기한 — 종료 전이 때 확정(설계서 1-9)',
    `fulfillment_resolved_at`      DATETIME(6)   NULL COMMENT '미이행 스레드 양측 동의 종결 시각',
    `fulfillment_resolution_note`  VARCHAR(1000) NULL COMMENT '합의 결과 기록 — 당사자 합의이지 운영자 판정이 아니다',
    `settled_at`                   DATETIME(6)   NULL COMMENT '정산 모듈의 이체 완료 통보 시각',

    `version`                      BIGINT        NOT NULL DEFAULT 0,
    `created_at`                   DATETIME(6)   NULL,
    `modified_at`                  DATETIME(6)   NULL,

    PRIMARY KEY (`group_buy_id`),
    CONSTRAINT `uk_group_buy_number` UNIQUE (`group_buy_number`),
    CONSTRAINT `uk_group_buy_contract` UNIQUE (`contract_id`),

    KEY `idx_group_buy_market_status_start` (`market_id`, `status`, `start_at`),
    KEY `idx_group_buy_market_created` (`market_id`, `created_at`),
    KEY `idx_group_buy_creator_status_start` (`creator_id`, `status`, `start_at`),
    KEY `idx_group_buy_status_start` (`status`, `start_at`),
    KEY `idx_group_buy_status_end` (`status`, `end_at`),
    KEY `idx_group_buy_status_ended` (`status`, `ended_at`),

    CONSTRAINT `fk_group_buy_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract` (`contract_id`),
    CONSTRAINT `fk_group_buy_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`),
    CONSTRAINT `fk_group_buy_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`),

    CONSTRAINT `ck_group_buy_period` CHECK (`start_at` < `end_at`),
    -- 상태 ↔ close_type 결합 CHECK는 걸지 않는다. 전이는 조건부 UPDATE(status만)와 엔티티 변경 감지
    -- (부수 필드)가 같은 트랜잭션의 두 문장으로 나뉘는데, MySQL CHECK는 문장 단위라 첫 문장에서 걸린다.
    CONSTRAINT `ck_group_buy_close_type_value` CHECK (
        `close_type` IS NULL OR `close_type` IN ('COMPLETED', 'EARLY_CLOSED', 'SUSPENDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
