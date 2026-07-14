/*
  shopType = SHOWROOM(= CREATOR) 계정이 보유한 market 및 하위 데이터 정리

  전제(현재 스키마 기준):
  - market.seller_id -> seller.seller_id
  - SHOWROOM 여부는 seller.role_type = 'CREATOR' 로 판단
  - market 삭제 전 FK로 묶인 하위 테이블들을 선삭제
*/

/* market_follow */
DELETE mf
FROM market_follow mf
JOIN market m ON m.market_id = mf.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

/* market_sns (FK가 ON DELETE CASCADE지만, 명시적으로 정리) */
DELETE ms
FROM market_sns ms
JOIN market m ON m.market_id = ms.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

/*
  post 계열 주의:
  - V69 마이그레이션에서 post.market_id -> post.creator_id 로 변경됨
  - 즉, post는 더 이상 market과 FK로 연결되지 않음
  - 이번 마이그레이션의 목적은 "SHOWROOM 계정의 market 엔티티 삭제"이므로 post 데이터는 대상에서 제외함
*/

/* product_announcement 계열 */
DELETE pat
FROM product_announcement_target pat
JOIN product_announcement pa ON pa.id = pat.announcement_id
JOIN market m ON m.market_id = pa.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE pa
FROM product_announcement pa
JOIN market m ON m.market_id = pa.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

/* product 계열 */
DELETE pih
FROM product_inspection_history pih
JOIN product pr ON pr.product_id = pih.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE pinq
FROM product_inquiry pinq
JOIN product pr ON pr.product_id = pinq.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE cp
FROM coupon_product cp
JOIN product pr ON pr.product_id = cp.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE vmap
FROM variant_option_map vmap
JOIN product_variant pv ON pv.variant_id = vmap.variant_id
JOIN product pr ON pr.product_id = pv.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE pv
FROM product_variant pv
JOIN product pr ON pr.product_id = pv.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE po
FROM product_option po
JOIN product_option_group pog ON pog.option_group_id = po.option_group_id
JOIN product pr ON pr.product_id = pog.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE pog
FROM product_option_group pog
JOIN product pr ON pr.product_id = pog.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE pimg
FROM product_image pimg
JOIN product pr ON pr.product_id = pimg.product_id
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

DELETE pr
FROM product pr
JOIN market m ON m.market_id = pr.market_id
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

/* 마지막으로 market 삭제 */
DELETE m
FROM market m
JOIN seller s ON s.seller_id = m.seller_id
WHERE s.role_type = 'CREATOR';

