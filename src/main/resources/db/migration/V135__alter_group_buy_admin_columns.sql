-- 어드민 공구 관리(32 설계 1-7) — 판정의 근거를 남기는 컬럼 8개.
-- 30 설계 P1(V124~V131)이 이미 배포 브랜치에 올라가 원안 수정 대신 ALTER 하나로 모은다(V133 · V134와 같은 방식).

-- 3자 스레드 양측 동의 종결 시각(1-1). 정산 보류 해제(fulfillment_resolved_at)와 다른 사건이다 —
-- 합의는 연결·소통, 해제는 정산 관리가 각자 다른 시각에 한다. 보류 파생식은 resolved_at 기준을 유지한다.
ALTER TABLE `group_buy`
    ADD COLUMN `fulfillment_agreed_at` DATETIME(6) NULL COMMENT '이행 3자 스레드 양측 동의 종결' AFTER `fulfillment_due_at`;

-- 직권 중단(1-2) — 통지 시점 판본 · 판매 스냅샷 · 철회 사유 코드 · 집행 사유.
-- withdraw_reason → withdraw_detail: 사유 코드가 생겨 같은 컬럼이 코드와 문장을 겸하지 않게 한다.
-- status에 SUPERSEDED(긴급 집행으로 대체됨)가 더해진다 — VARCHAR라 스키마 변경은 없다.
ALTER TABLE `group_buy_admin_suspension`
    ADD COLUMN `notice_revision_no`          INT           NULL COMMENT '통지 시점 게시물 판본' AFTER `appeal_deadline_at`,
    ADD COLUMN `sales_order_count_at_notice` INT           NULL COMMENT '통지 후 증가분의 기준점 — 판매 포트가 비면 NULL' AFTER `notice_revision_no`,
    ADD COLUMN `sales_amount_at_notice`      BIGINT        NULL AFTER `sales_order_count_at_notice`,
    ADD COLUMN `withdraw_reason_code`        VARCHAR(32)   NULL COMMENT 'RECTIFIED, NOT_A_VIOLATION, NOT_BRAND_FAULT, ETC' AFTER `withdrawn_by`,
    ADD COLUMN `execution_note`              VARCHAR(1000) NULL COMMENT '집행 판정 사유 — 브랜드에 전달' AFTER `executed_by`,
    RENAME COLUMN `withdraw_reason` TO `withdraw_detail`;

-- 게시물 숨김·해제가 판단한 판본(1-3). 현재 또는 마지막 숨김 1건만 담는다 — 과거 숨김은 이력 ref_id로 되짚는다.
ALTER TABLE `group_buy_post`
    ADD COLUMN `hidden_revision_no`   INT NULL COMMENT '숨김 당시 판본' AFTER `hidden_reason_detail`,
    ADD COLUMN `unhidden_revision_no` INT NULL COMMENT '해제 판단에 쓴 판본' AFTER `unhidden_by`;
