-- 상품 단위 최소 물량은 옵션 합계의 파생값이 됐다(옵션 계획서 2-3). 두 곳에 저장하면 어긋난다.
-- V140 백필이 옛 값을 옵션 행으로 옮긴 뒤에 지운다. CHECK가 컬럼을 참조하므로 먼저 지운다.
ALTER TABLE `contract_item` DROP CHECK `ck_contract_item_min_quantity`;
ALTER TABLE `contract_item` DROP COLUMN `min_quantity`;
