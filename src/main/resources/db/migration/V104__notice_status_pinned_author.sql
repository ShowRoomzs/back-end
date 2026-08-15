-- 공지 기획 §20 반영
-- 1) 노출 여부(is_visible) 불리언 → 상태(status) 2종: 게시(PUBLISHED) / 게시 종료(ENDED)
-- 2) 중요(is_pinned) 분류, 작성자(author_id), 게시 종료 일시(ended_at) 신설
-- 3) 목록 정렬(중요 고정 상단 + 등록일 최신순)용 인덱스 추가

ALTER TABLE `notice`
    ADD COLUMN `status`    VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' AFTER `content`,
    ADD COLUMN `is_pinned` BIT(1)      NOT NULL DEFAULT b'0'        AFTER `status`,
    ADD COLUMN `author_id` BIGINT      NULL                         AFTER `is_pinned`,
    ADD COLUMN `ended_at`  DATETIME(6) NULL                         AFTER `author_id`;

-- 기존 비공개(is_visible = false) 공지는 게시 종료로 넘긴다 — 임시저장 상태를 두지 않기 때문이다
UPDATE `notice`
SET `status` = CASE WHEN `is_visible` = b'1' THEN 'PUBLISHED' ELSE 'ENDED' END;

-- 종료 일시는 남아 있지 않으므로 마지막 수정 시각으로 채운다
UPDATE `notice`
SET `ended_at` = `modified_at`
WHERE `status` = 'ENDED';

ALTER TABLE `notice`
    DROP COLUMN `is_visible`;

CREATE INDEX `idx_notice_status_pinned_created` ON `notice` (`status`, `is_pinned`, `created_at`);
