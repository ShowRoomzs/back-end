-- 49 스튜디오 성과 관리 설계서 1-5 — 하위주문의 게시물 귀속(성과 「게시물 → 주문」).
-- 결제 완료 시점에 한 번만 채운다(§24-7 24시간 라스트 터치 · 그 공구 인플루언서의 게시물) · FK 없음.
ALTER TABLE `order_delivery_group`
    ADD COLUMN `attributed_post_id` BIGINT NULL COMMENT '결제 완료 시점의 라스트 터치 게시물(24h) · FK 없음',
    ADD KEY `idx_odg_attributed_post` (`attributed_post_id`);
