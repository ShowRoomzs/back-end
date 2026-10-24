-- 54_어드민_일반게시물_모니터링_BE_설계서.md 2-1 — 사진 「신고 지목」 표시.
--
-- 운영자 수동 표시다(§54-3 — C18 신고 화면에 사진 지정 칸이 없다 · ⓗ). 사진 단위 조치가 아니라
-- 읽기 보조 표식이고, 노출 중지는 여전히 게시물 단위다(C11).

ALTER TABLE `post_image`
    ADD COLUMN `report_flagged_at` DATETIME(6) NULL COMMENT '운영자가 신고 지목을 켠 시각 — NULL이면 지목 없음',
    ADD COLUMN `report_flagged_by` BIGINT      NULL COMMENT '지목을 켠 운영자 id';
