-- 공구 3자 스레드(30-1 1절) — 이슈 스레드 · 미이행 스레드는 같은 쌍의 두 번째 스레드다.
-- connection_id UNIQUE(쌍당 스레드 1개)를 thread_kind = CONNECTION에만 남긴다. 3자 스레드도 그 쌍의 PAIR 연결을
-- connection_id로 가지므로 접근 판정 · 목록 · 안 읽은 수가 연결 기준 그대로 동작한다.
-- 기존 행은 기본값으로 전부 CONNECTION이 된다 — 백필이 필요 없다.
ALTER TABLE `message_thread`
    ADD COLUMN `thread_kind` VARCHAR(32) NOT NULL DEFAULT 'CONNECTION'
        COMMENT 'CONNECTION, GROUP_BUY_ISSUE, GROUP_BUY_FULFILLMENT' AFTER `connection_id`,
    ADD COLUMN `subject_id` BIGINT NULL COMMENT '공구 id — CONNECTION이면 NULL' AFTER `thread_kind`;

-- 생성 컬럼 UNIQUE로 쌍당 1개 제약을 CONNECTION에만 건다(group_buy_issue.open_group_buy_id와 같은 방식 · 엔티티 미매핑).
ALTER TABLE `message_thread`
    ADD COLUMN `connection_thread_key` BIGINT
        GENERATED ALWAYS AS (IF(`thread_kind` = 'CONNECTION', `connection_id`, NULL)) STORED,
    ADD CONSTRAINT `uk_message_thread_connection_kind` UNIQUE (`connection_thread_key`),
    ADD KEY `idx_message_thread_kind_subject` (`thread_kind`, `subject_id`);

-- FK(fk_message_thread_connection)가 쓰던 UNIQUE 인덱스를 지우기 전에 일반 인덱스를 먼저 둔다.
ALTER TABLE `message_thread` ADD KEY `idx_message_thread_connection` (`connection_id`);
ALTER TABLE `message_thread` DROP INDEX `uk_message_thread_connection`;
