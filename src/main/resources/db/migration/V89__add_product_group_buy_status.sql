-- 상품 공구 상태 컬럼 추가
-- PREPARING(준비중), READY(준비완료), IN_PROGRESS(진행중), NOT_CONNECTED(연결없음)

ALTER TABLE `product`
    ADD COLUMN `group_buy_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_CONNECTED'
        COMMENT 'PREPARING:준비중, READY:준비완료, IN_PROGRESS:진행중, NOT_CONNECTED:연결없음'
        AFTER `display_status`;
