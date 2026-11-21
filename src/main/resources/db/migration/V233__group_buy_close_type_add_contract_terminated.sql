-- 공구 종결 유형에 CONTRACT_TERMINATED(계약 해지, 1021 기-05)를 허용한다.
--
-- 1021 에서 GroupBuyCloseType 에 값을 추가하면서 V124 의 값 CHECK 를 넓히지 않아,
-- 오픈 전 공구가 계약 해지로 종결될 때(GroupBuyTerminator.terminatedByContract) UPDATE 가 CHECK 에 걸렸다.
-- MySQL 은 CHECK 를 고칠 수 없어 지우고 다시 건다.
ALTER TABLE `group_buy` DROP CHECK `ck_group_buy_close_type_value`;

ALTER TABLE `group_buy`
    MODIFY COLUMN `close_type` VARCHAR(24) NULL COMMENT 'COMPLETED, EARLY_CLOSED, SUSPENDED, CONTRACT_TERMINATED — §29-2 종결 3종 + 계약 해지(1021 기-05)';

ALTER TABLE `group_buy`
    ADD CONSTRAINT `ck_group_buy_close_type_value` CHECK (
        `close_type` IS NULL OR `close_type` IN ('COMPLETED', 'EARLY_CLOSED', 'SUSPENDED', 'CONTRACT_TERMINATED'));
