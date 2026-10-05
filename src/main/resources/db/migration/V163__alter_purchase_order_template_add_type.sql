-- 재발송 목록(반품·교환 관리 E1)이 발주서의 템플릿·반출 로그를 같이 쓴다(35 설계서 1-10).
-- 「컬럼 구성을 기본값으로 저장」이라는 같은 개념이고, 재발송 목록도 수취인 개인정보 반출이다.

-- 템플릿 종류 — PURCHASE_ORDER(발주서) · CLAIM_RESHIP(재발송 목록). 마켓당 종류별 1행.
-- 새 UK 를 먼저 만들고 옛 UK 를 지운다 — market_id FK 가 옛 UK 인덱스에 기대고 있어, 순서가 반대면 DROP 이 거절된다.
ALTER TABLE `market_purchase_order_template`
    ADD COLUMN `template_type` VARCHAR(20) NOT NULL DEFAULT 'PURCHASE_ORDER' AFTER `market_id`,
    ADD CONSTRAINT `uk_market_purchase_order_template_type` UNIQUE (`market_id`, `template_type`);

ALTER TABLE `market_purchase_order_template`
    DROP INDEX `uk_market_purchase_order_template`;

-- 반출 로그 종류 — 기존 행은 전부 발주서다.
ALTER TABLE `purchase_order_download_log`
    ADD COLUMN `kind` VARCHAR(20) NOT NULL DEFAULT 'PURCHASE_ORDER' COMMENT 'PURCHASE_ORDER · CLAIM_RESHIP' AFTER `seller_id`;
