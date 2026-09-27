-- 게시 후 마지막 본문 수정 시각 — 소비자 게시물 상세의 modifiedAt(「마지막 수정 시각」)이 읽는다.
-- modified_at(auditing)은 좋아요·노출 수·노출 상태가 바뀔 때도 갱신돼 「수정됨」의 근거가 못 된다.
-- 게시 전 임시저장·제출은 찍지 않는다. 값이 없으면 응답은 published_at으로 대신한다.
ALTER TABLE `post`
    ADD COLUMN `edited_at` DATETIME(6) NULL COMMENT '게시 후 마지막 본문 수정' AFTER `published_at`;

-- 공구 게시물은 승인 후 수정 시각(group_buy_post.last_edited_at)이 이미 있다 — 게시(오픈) 뒤의 수정만 옮긴다.
-- 일반 게시물은 수정 이력이 없어 소급하지 않는다(NULL → 게시 시각).
UPDATE `post` p
    JOIN `group_buy_post` gp ON gp.`post_id` = p.`post_id`
SET p.`edited_at` = gp.`last_edited_at`
WHERE gp.`last_edited_at` IS NOT NULL
  AND p.`published_at` IS NOT NULL
  AND gp.`last_edited_at` > p.`published_at`;
