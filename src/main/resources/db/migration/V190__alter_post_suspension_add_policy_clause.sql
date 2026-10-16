-- 54_어드민_일반게시물_모니터링_BE_설계서.md 2-2 — 근거 규정 코드 · 조치 종결 처리자.
--
-- policy_clause_code: 근거 규정을 코드(PostPolicyClause)로 받는다. 기존 policy_ref에는 선택 시점의
--   조항 문구를 함께 굳힌다 — 운영정책 문서가 확정되어 조항 번호가 바뀌어도 과거 조치의 근거는 그대로 남는다.
-- resolved_by: 운영자가 조치를 거둔 경우(조치 취소 · 재게시)의 처리자. 이의 승인 · 반려는 post_appeal.reviewed_by가,
--   기한 만료는 시스템이, 본인 삭제는 작성자가 주체라 NULL로 둔다.

ALTER TABLE `post_suspension`
    ADD COLUMN `policy_clause_code` VARCHAR(40) NULL COMMENT 'PostPolicyClause — ARTICLE_3_2 / ARTICLE_3_4 / ARTICLE_4_1 / ARTICLE_4_3 / ARTICLE_5',
    ADD COLUMN `resolved_by`        BIGINT      NULL COMMENT '조치를 종결한 운영자 — CANCELED_BY_ADMIN일 때만';
