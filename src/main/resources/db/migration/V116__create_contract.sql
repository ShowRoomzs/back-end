-- §25~§28 계약 관리 — 계약 본체.
-- 파트너센터(§26) API만 먼저 열지만 컬럼은 3서피스(어드민 §28 · 스튜디오 §27)가 쓸 것까지 전부 판다.
-- 계약은 상태 전이 이력이 곧 법적 근거인 객체라, 나중에 ALTER로 붙이면 그 사이 계약들의
-- 검토·서명 시각이 NULL로 남아 분쟁에서 쓸 수 없게 된다(설계서 0-1).
--
-- 작성중 계약은 필수값이 전부 비어 있는 행이므로 DB 제약은 「형식」만 건다(설계서 0-3).
-- 「필수」 판정은 전부 애플리케이션이, 그것도 검토 요청 전이 시점에 한다.
CREATE TABLE `contract` (
    `contract_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `contract_number`             VARCHAR(30)  NULL COMMENT 'CTR-YYYYMMDD-NNN — 검토 요청 시점에 부여(설계서 1-7)',
    `market_id`                   BIGINT       NOT NULL COMMENT '브랜드',
    `creator_id`                  BIGINT       NULL COMMENT '계약 상대 — 작성중엔 미선택 가능',
    `connection_id`               BIGINT       NULL COMMENT '작성 시점의 연결 근거 · 「스레드 열기」 딥링크의 출처',
    `title`                       VARCHAR(40)  NULL COMMENT '공구명 · 2~40자(전이 시 검증)',
    `status`                      VARCHAR(32)  NOT NULL COMMENT 'DRAFT, REVIEW_PENDING, REVIEW_REJECTED, SIGNING, CONCLUSION_PENDING, CONCLUDED, DECLINED, EXPIRED, CANCELED',

    `group_buy_start_at`          DATETIME(6)  NULL COMMENT '공구 시작 일시',
    `group_buy_end_at`            DATETIME(6)  NULL COMMENT '공구 종료 일시',

    `fixed_fee_amount`            INT          NULL COMMENT '고정 지급비(원)',
    `fixed_fee_trigger`           VARCHAR(32)  NULL COMMENT 'POST_REGISTERED, GROUP_BUY_ENDED, SETTLEMENT_COMPLETED',
    `fixed_fee_notice_agreed_at`  DATETIME(6)  NULL COMMENT '고지 확인 체크 시각 — boolean이 아니라 시각으로 받는다',
    `fixed_fee_paid_at`           DATETIME(6)  NULL COMMENT '브랜드의 [지급 완료 기록](B5a)',

    `content_feed_count`          INT          NULL COMMENT '피드 게시물 수',
    `content_reels_count`         INT          NULL COMMENT '릴스 수',
    `content_story_count`         INT          NULL COMMENT '스토리 수',
    `content_due_date`            DATE         NULL COMMENT '게시 완료 기한',

    `secondary_use_allowed`       BIT(1)       NULL COMMENT '2차 활용 허용 · 기본 true',
    `secondary_use_period_type`   VARCHAR(16)  NULL COMMENT 'FIXED, UNLIMITED',
    `secondary_use_months`        INT          NULL COMMENT 'FIXED일 때만 · 기본 12',
    `brand_pre_review`            BIT(1)       NULL COMMENT '브랜드 사전 검수 · 기본 false',
    `note`                        VARCHAR(500) NULL COMMENT '비고',

    `clause_version_id`           BIGINT       NULL COMMENT '표준 조항 스냅샷 — 검토 요청 시 고정(설계서 1-6)',
    `warning_flags`               VARCHAR(128) NULL COMMENT '통과된 경고 W1~W6 CSV(설계서 2-3)',

    `review_requested_at`         DATETIME(6)  NULL COMMENT '§25-5-2 D+7 판정의 기준일',
    `review_approved_at`          DATETIME(6)  NULL COMMENT '어드민 입력',
    `review_rejected_at`          DATETIME(6)  NULL,
    `reject_reason_code`          VARCHAR(64)  NULL COMMENT '반려 사유 제목(정형)',
    `reject_reason_detail`        VARCHAR(1000) NULL COMMENT '반려 사유 상세 — §28-4 2단 필수',

    `signature_requested_at`      DATETIME(6)  NULL COMMENT '어드민이 옮겨 적는 발송 일시',
    `signature_deadline_at`       DATETIME(6)  NULL COMMENT '어드민이 옮겨 적는 서명 기한 · 만료 판단의 유일한 기준(§25-3)',
    `brand_signed_at`             DATETIME(6)  NULL COMMENT '어드민 체크 · 체결 전까지 해제 가능',
    `creator_signed_at`           DATETIME(6)  NULL COMMENT '어드민 체크 · 체결 전까지 해제 가능',
    `signature_as_of`             DATETIME(6)  NULL COMMENT '기준 시각 — 화면 「N 기준」',
    `creator_viewed_at`           DATETIME(6)  NULL COMMENT '스튜디오 최초 상세 진입 시각 · B7 「계약서 열람 기록 없음」 근거',

    `concluded_at`                DATETIME(6)  NULL COMMENT '체결 일시',
    `closed_at`                   DATETIME(6)  NULL COMMENT '거절·만료·취소 공통',
    `close_actor_type`            VARCHAR(16)  NULL COMMENT 'SELLER(취소), CREATOR(거절), ADMIN(만료)',
    `close_reason_code`           VARCHAR(64)  NULL COMMENT '만료는 NULL',
    `close_reason_memo`           VARCHAR(1000) NULL COMMENT '상대에게 전달되는 메모',

    `group_buy_id`                BIGINT       NULL COMMENT '생성된 공구 · 계약 1건 = 공구 1건(설계서 1-8)',
    `source_contract_id`          BIGINT       NULL COMMENT '재작성 출처(§26-5)',

    `version`                     BIGINT       NOT NULL DEFAULT 0 COMMENT '@Version 낙관적 락(설계서 3-4)',
    `created_at`                  DATETIME(6)  NULL,
    `modified_at`                 DATETIME(6)  NULL,

    PRIMARY KEY (`contract_id`),
    CONSTRAINT `uk_contract_number` UNIQUE (`contract_number`),
    -- NULL 다수는 MySQL이 통과시키므로 1:1의 반대편 보증일 뿐이다.
    -- 중복 생성 차단은 설계서 1-8의 조건부 UPDATE가 한다.
    CONSTRAINT `uk_contract_group_buy` UNIQUE (`group_buy_id`),

    KEY `idx_contract_market_status_created` (`market_id`, `status`, `created_at`),
    KEY `idx_contract_market_status_start` (`market_id`, `status`, `group_buy_start_at`),
    KEY `idx_contract_creator_status_deadline` (`creator_id`, `status`, `signature_deadline_at`),
    KEY `idx_contract_status_review_requested` (`status`, `review_requested_at`),
    KEY `idx_contract_status_signature_deadline` (`status`, `signature_deadline_at`),

    CONSTRAINT `fk_contract_market` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`),
    CONSTRAINT `fk_contract_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`),
    CONSTRAINT `fk_contract_connection` FOREIGN KEY (`connection_id`) REFERENCES `connection` (`connection_id`),
    CONSTRAINT `fk_contract_source` FOREIGN KEY (`source_contract_id`) REFERENCES `contract` (`contract_id`),

    CONSTRAINT `ck_contract_fixed_fee_amount` CHECK (`fixed_fee_amount` IS NULL OR (`fixed_fee_amount` >= 0 AND `fixed_fee_amount` <= 10000000)),
    CONSTRAINT `ck_contract_content_counts` CHECK (
        (`content_feed_count`  IS NULL OR `content_feed_count`  >= 0) AND
        (`content_reels_count` IS NULL OR `content_reels_count` >= 0) AND
        (`content_story_count` IS NULL OR `content_story_count` >= 0)),
    CONSTRAINT `ck_contract_secondary_use_months` CHECK (`secondary_use_months` IS NULL OR `secondary_use_months` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
