-- 공구 게시물 리비전 — 승인받은 원문을 지키는 테이블(31 설계 2-6). append-only.
--
-- 승인 후 자유 수정(인플 제14조⑤)이 확정되면서 post.content · group_buy_post.title은 마지막 값만 남는다.
-- 운영자가 승인한 원문은 여기서만 읽을 수 있다 — 책임 귀속 · 숨김 해제 판단 · 직권 중단 사유 판정의 근거.
--   · 기록 대상은 제출(SUBMITTED)과 승인 후 수정(EDITED)뿐이다. 임시저장은 남기지 않는다.
--   · 「승인된 판」 컬럼을 두지 않는다 — reviewed_at 이전 마지막 SUBMITTED로 유일하게 정해진다.
--   · ON DELETE를 두지 않는다 — 공구 게시물은 삭제되지 않는다.
CREATE TABLE `group_buy_post_revision` (
    `revision_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `post_id`     BIGINT       NOT NULL,
    `revision_no` INT          NOT NULL COMMENT '게시물 안에서 1부터',
    `kind`        VARCHAR(16)  NOT NULL COMMENT 'SUBMITTED, EDITED',
    `title`       VARCHAR(100) NOT NULL,
    `content`     TEXT         NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    `created_by`  BIGINT       NOT NULL COMMENT '크리에이터 id',
    PRIMARY KEY (`revision_id`),
    CONSTRAINT `uk_group_buy_post_revision_no` UNIQUE (`post_id`, `revision_no`),
    KEY `idx_group_buy_post_revision_created` (`post_id`, `created_at`),
    CONSTRAINT `fk_group_buy_post_revision_post` FOREIGN KEY (`post_id`) REFERENCES `group_buy_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
