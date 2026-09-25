-- 승인 후 마지막 수정 시각(31 설계 2-5). 임시저장·제출은 찍지 않는다 — 수정 내용은 group_buy_post_revision이 가진다.
ALTER TABLE `group_buy_post`
    ADD COLUMN `last_edited_at` DATETIME(6) NULL COMMENT '승인 후 마지막 수정' AFTER `unhidden_by`;
