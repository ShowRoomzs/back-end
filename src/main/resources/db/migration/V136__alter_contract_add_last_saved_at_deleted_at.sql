-- 브랜드의 마지막 임시저장 시각. modified_at은 어드민 반려·서명 반영에도 찍혀 「내가 언제 저장했나」로 쓸 수 없다.
-- 삭제 — 검토 반려 계약은 계약번호·어드민 반려 이력·제출본 PDF를 이미 가지고 있어 행을 지우지 않고 표시만 한다.
ALTER TABLE `contract`
    ADD COLUMN `last_saved_at` DATETIME(6) NULL COMMENT '브랜드 임시저장(PUT) 시각' AFTER `source_contract_id`,
    ADD COLUMN `deleted_at`    DATETIME(6) NULL COMMENT '브랜드 삭제 — 파트너센터·어드민에서 보이지 않는다' AFTER `last_saved_at`;
