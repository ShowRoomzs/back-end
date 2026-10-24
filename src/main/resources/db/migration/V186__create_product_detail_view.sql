-- 49 스튜디오 성과 관리 설계서 1-4 — 상품 상세 조회 원천 로그.
--
-- 성과 화면 두 곳(상품별 「상세 조회」 열 · 공구 게시물 「상품 상세 이동」)이 이 로그 하나를 읽는다.
-- 행 1건 = 30분 세션 1회(쇼룸 방문 · 게시물 노출과 같은 규칙 · 적재 시점에 거른다).
-- group_buy_id 는 조회 시점에 이 상품이 붙어 있던 판매 중 공구 — 없으면 NULL(공구 밖 조회 · 성과 집계 밖).
-- attributed_post_id 는 §24-7 라스트 터치(24시간) — FK 를 걸지 않는다(게시물 파기와 무관한 통계 태그).
CREATE TABLE `product_detail_view` (
    `view_id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `product_id`         BIGINT      NOT NULL,
    `group_buy_id`       BIGINT      NULL COMMENT '조회 시점의 판매 중 공구 — 없으면 NULL',
    `creator_id`         BIGINT      NULL COMMENT '그 공구의 인플루언서 — 집계 조인 제거용 비정규화 · FK 없음',
    `user_id`            BIGINT      NULL COMMENT '로그인 조회만',
    `viewer_key`         VARCHAR(64) NOT NULL COMMENT 'showroom_visit.visitor_key 와 같은 규칙',
    `source`             VARCHAR(20) NOT NULL COMMENT 'SHOWROOM, POST, DIRECT_LINK, OTHER',
    `attributed_post_id` BIGINT      NULL COMMENT '라스트 터치 게시물(24h) · FK 없음',
    `viewed_at`          DATETIME(6) NOT NULL,
    PRIMARY KEY (`view_id`),
    KEY `idx_pdv_group_buy_product` (`group_buy_id`, `product_id`, `viewed_at`),
    KEY `idx_pdv_session` (`product_id`, `viewer_key`, `viewed_at`),
    KEY `idx_pdv_attribution` (`attributed_post_id`, `viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 상세 조회 로그';
