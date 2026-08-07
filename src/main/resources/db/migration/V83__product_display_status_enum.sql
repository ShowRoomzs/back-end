-- 상품 진열 상태를 Boolean에서 4상태 enum으로 전환
-- DISPLAY(진열), HIDDEN(미진열), PENDING_REVIEW(재검토 대기), HIDE_REQUEST(미진열 요청)

ALTER TABLE `product`
    ADD COLUMN `display_status` VARCHAR(32) NOT NULL DEFAULT 'DISPLAY'
        COMMENT 'DISPLAY:진열, HIDDEN:미진열, PENDING_REVIEW:재검토 대기, HIDE_REQUEST:미진열 요청'
        AFTER `gender`;

ALTER TABLE `product`
    ADD COLUMN `previous_display_status` VARCHAR(32) DEFAULT NULL
        COMMENT '마켓 정지 전 진열 상태 백업'
        AFTER `display_status`;

UPDATE `product`
SET `display_status` = CASE
    WHEN `is_display` = 1 THEN 'DISPLAY'
    ELSE 'HIDDEN'
END;

UPDATE `product`
SET `previous_display_status` = CASE
    WHEN `previous_is_display` IS NULL THEN NULL
    WHEN `previous_is_display` = 1 THEN 'DISPLAY'
    ELSE 'HIDDEN'
END;

ALTER TABLE `product`
    DROP COLUMN `is_display`,
    DROP COLUMN `previous_is_display`;
