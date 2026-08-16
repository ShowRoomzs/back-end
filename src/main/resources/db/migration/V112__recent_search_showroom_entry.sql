-- C14 쇼룸 검색 — 최근 검색은 인스타 형식의 세로 리스트로, 쇼룸과 검색어가 한 목록에 섞인다.
--
-- 기존 recent_search는 검색어(term) 한 종류만 담았다. 디자인의 최근 검색 행은 두 종류다.
--   · 검색어(TERM)   — 회색 원 안 돋보기 + 텍스트, 탭하면 그 단어로 재검색
--   · 쇼룸(SHOWROOM) — 아바타 + 이름 + 아이디(@handle), 탭하면 바로 C4 쇼룸으로
-- 쇼룸 행은 아바타·이름·핸들을 조회 시점의 쇼룸에서 그대로 읽어야 하므로(이름을 바꾸면 목록도 따라 바뀐다)
-- 문자열이 아니라 creator_id 참조로 저장한다. term은 쇼룸 행에서도 채워 두어(저장 시점의 쇼룸명)
-- 기존 컬럼 제약(NOT NULL)을 그대로 두고, 표시에는 쓰지 않는다.
ALTER TABLE recent_search
    ADD COLUMN `entry_type` VARCHAR(20) NOT NULL DEFAULT 'TERM' COMMENT 'TERM: 검색어, SHOWROOM: 쇼룸',
    ADD COLUMN `creator_id` BIGINT NULL COMMENT 'SHOWROOM 항목이 가리키는 쇼룸 — TERM이면 NULL';

ALTER TABLE recent_search
    ADD CONSTRAINT `fk_recent_search_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`);

-- 최근 검색 목록은 항상 "내 것을 최신순으로"만 읽는다.
CREATE INDEX `idx_recent_search_user_created` ON recent_search (`user_id`, `created_at`);
