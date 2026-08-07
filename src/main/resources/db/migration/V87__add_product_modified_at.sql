ALTER TABLE `product`
    ADD COLUMN `modified_at` DATETIME(6) NULL COMMENT '수정일' AFTER `created_at`;

UPDATE `product`
SET `modified_at` = `created_at`
WHERE `modified_at` IS NULL;
