-- 공구 게시물 — 공통 뿌리 post + 1:1 확장(24 설계 §9-4 수정판 · 설계서 1-8).
--
-- 24 설계에서 바꾼 것
--   · title VARCHAR(200) → VARCHAR(100) + 앱 검증 40자(§31-2 상한 40자 · 근거 대기라 컬럼은 여유를 둔다)
--   · group_buy_id NULL → NOT NULL UNIQUE(공구 1건당 게시물 1개)
--   · group_buy_post_product 테이블을 만들지 않는다 — 게시물의 상품 = 계약 상품 전부(0-3).
--     따로 저장하면 계약과 다른 상품이 게시물에 붙는 경로가 생긴다.
--   · is_sponsored 컬럼을 만들지 않는다 — 대가관계 표시는 항상 자동 삽입(§31-2).
--
-- 게시물 8종 상태는 저장하지 않고 파생한다. 게시물이 스스로 가진 사실(작성·제출·심사·숨김)만 저장한다.
-- 숨김을 post.status = SUSPENDED로 표현하지 않는다 — 일반 게시물의 SUSPENDED는 이의 신청 기한이 지나면
-- 영구 삭제로 넘어간다(PostAppealDeadlineScheduler). 공구 게시물 원문은 분쟁 근거로 보관해야 한다.
CREATE TABLE `group_buy_post` (
    `post_id`               BIGINT        NOT NULL COMMENT 'post와 1:1 — 본문은 post.content',
    `group_buy_id`          BIGINT        NOT NULL,
    `title`                 VARCHAR(100)  NOT NULL COMMENT '앱 검증 40자',
    `review_status`         VARCHAR(16)   NOT NULL COMMENT 'DRAFT, PENDING, REJECTED, APPROVED',
    `submitted_at`          DATETIME(6)   NULL COMMENT '게이트 ② 인플루언서 제출',
    `reviewed_at`           DATETIME(6)   NULL COMMENT '게이트 ③ 운영자 심사',
    `reviewed_by`           BIGINT        NULL,
    `reject_reason_code`    VARCHAR(64)   NULL,
    `reject_reason_detail`  VARCHAR(1000) NULL,
    `hidden_at`             DATETIME(6)   NULL COMMENT '숨김(M5) — 되돌릴 수 있는 조치',
    `hidden_by`             BIGINT        NULL,
    `hidden_reason_code`    VARCHAR(64)   NULL,
    `hidden_reason_detail`  VARCHAR(1000) NULL,
    `unhidden_at`           DATETIME(6)   NULL COMMENT '해제는 운영자만',
    `unhidden_by`           BIGINT        NULL,
    `created_at`            DATETIME(6)   NULL,
    `modified_at`           DATETIME(6)   NULL,
    PRIMARY KEY (`post_id`),
    CONSTRAINT `uk_group_buy_post_group_buy` UNIQUE (`group_buy_id`),
    KEY `idx_group_buy_post_review_status` (`review_status`, `submitted_at`),
    CONSTRAINT `fk_group_buy_post_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
    CONSTRAINT `fk_group_buy_post_group_buy` FOREIGN KEY (`group_buy_id`) REFERENCES `group_buy` (`group_buy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
