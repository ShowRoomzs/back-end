-- ============================================================================================
-- 결제 테스트 시드 — 로컬 전용. 운영 DB 에 절대 실행하지 않는다.
--
-- 만드는 것: 브랜드(판매자·마켓) 1 · 크리에이터 1 · 상품 1(옵션 2) · 체결 계약 1 · 진행 중 공구 1 · 소비자 1(기본 배송지)
-- 결과: 소비자로 로그인해 바로 구매 → 주문 생성 → 결제창 → complete 까지 돌릴 수 있다.
--
-- 가격(결제 계획서 · 가격 계획서) — 소비자 가격은 계약 옵션에서 나온다.
--   옵션 「기본」     정가 34,000 → 판매가 27,200 (공구가)
--   옵션 「2개 세트」 정가 60,000 → 판매가 53,200 (공구가 27,200 + 옵션가 60,000−34,000)
--   배송비 3,000 · 50,000원 이상 무료. → 「기본」 1개 주문 = 30,200원 결제
--
-- 로그인: POST /v1/user/auth/local/login  {"id": "paytest", "password": "Test1234!"}
-- 브랜드(파트너센터) 로그인: paytest-brand@showroomz.test / Test1234!
--
-- 한 번만 실행한다. 다시 넣으려면 맨 아래 「정리」 블록을 먼저 실행한다.
-- ============================================================================================

START TRANSACTION;

SET @now      = NOW(6);
SET @pw       = '$2a$10$b8iuKQFi04SF6KxE0ByWM.WoE60ENM6KZGOlWNcv6Tldh.gFoJcoe';  -- Test1234! (BCrypt)
SET @start_at = DATE_SUB(@now, INTERVAL 1 DAY);
SET @end_at   = DATE_ADD(@now, INTERVAL 30 DAY);

-- 1. 브랜드 — 판매자 + 마켓(배송비 설정 포함) ---------------------------------------------------
INSERT INTO SELLER (created_at, modified_at, name, password, email, phone_number, role_type, status,
                    company_name, representative_name, business_type, is_new_member)
VALUES (@now, @now, '결제테스트 담당', @pw, 'paytest-brand@showroomz.test', '010-1111-2222', 'SELLER', 'APPROVED',
        '주식회사 결제테스트랩', '김대표', '일반과세자', 0);
SET @seller_id = LAST_INSERT_ID();

INSERT INTO MARKET (seller_id, market_name, cs_number, status, default_delivery_fee, free_shipping_threshold,
                    shipping_recipient_name, shipping_contact, shipping_address, shipping_detail_address, shipping_lead_days)
VALUES (@seller_id, '결제테스트랩', '02-1234-5678', 'ACTIVE', 3000, 50000,
        '결제테스트 담당', '010-1111-2222', '서울특별시 강남구 테헤란로 123', '1층 물류센터', 2);
SET @market_id = LAST_INSERT_ID();

-- 2. 상품 · 옵션 2개 --------------------------------------------------------------------------
INSERT INTO category (name, `order`) VALUES ('결제테스트 뷰티', 999);
SET @category_id = LAST_INSERT_ID();

INSERT INTO product (market_id, category_id, name, regular_price, sale_price, thumbnail_url, description,
                     display_status, group_buy_status, is_out_of_stock_forced, is_recommended, created_at, modified_at,
                     product_number)
VALUES (@market_id, @category_id, '결제테스트 시카 앰플 30ml', 34000, 34000,
        'https://placehold.co/600x600?text=PAYTEST', '결제 테스트용 상품',
        'DISPLAY', 'IN_PROGRESS', b'0', b'0', @now, @now, 'SRZ-PAYTEST-001');
SET @product_id = LAST_INSERT_ID();

INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative)
VALUES (@product_id, '기본', 34000, 34000, 100, b'1');
SET @variant_basic = LAST_INSERT_ID();

INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative)
VALUES (@product_id, '2개 세트', 60000, 60000, 100, b'0');
SET @variant_set = LAST_INSERT_ID();

-- 3. 크리에이터(쇼룸) — 공구의 상대방 -------------------------------------------------------------
INSERT INTO users (created_at, modified_at, username, nickname, password, email, email_verified_yn, provider_type,
                   role_type, status, name, phone_number, age_agree, service_agree, privacy_agree, marketing_agree,
                   follow_post_push_agree)
VALUES (@now, @now, 'paytest-creator', '결제테스트쇼룸', @pw, 'paytest-creator@showroomz.test', 'Y', 'LOCAL',
        'CREATOR', 'NORMAL', '이크리', '010-3333-4444', b'1', b'1', b'1', b'0', b'1');
SET @creator_user_id = LAST_INSERT_ID();

INSERT INTO creator (user_id, follower_count, business_email, channel_url, sns_type, account_id, showroom_name,
                     is_new_member, created_at, modified_at)
VALUES (@creator_user_id, 12000, 'paytest-creator-biz@showroomz.test', 'https://instagram.com/paytest', 'INSTAGRAM',
        'paytest', '결제테스트쇼룸', 0, @now, @now);
SET @creator_id = LAST_INSERT_ID();

-- 4. 체결 계약 · 계약 상품 · 계약 옵션 -------------------------------------------------------------
INSERT INTO contract (contract_number, market_id, creator_id, title, status, group_buy_start_at, group_buy_end_at,
                      fixed_fee_amount, content_feed_count, content_reels_count, content_story_count,
                      review_requested_at, review_approved_at, brand_signed_at, creator_signed_at, concluded_at,
                      version, created_at, modified_at)
VALUES ('CTR-PAYTEST-001', @market_id, @creator_id, '결제 테스트 공구', 'CONCLUDED', @start_at, @end_at,
        0, 1, 0, 0,
        DATE_SUB(@now, INTERVAL 5 DAY), DATE_SUB(@now, INTERVAL 4 DAY), DATE_SUB(@now, INTERVAL 3 DAY),
        DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 2 DAY),
        0, @now, @now);
SET @contract_id = LAST_INSERT_ID();

INSERT INTO contract_item (contract_id, product_id, product_name, regular_price, group_buy_price, reward_rate, sort_order)
VALUES (@contract_id, @product_id, '결제테스트 시카 앰플 30ml', 34000, 27200, 12.0, 0);
SET @contract_item_id = LAST_INSERT_ID();

INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, min_quantity, sort_order)
VALUES (@contract_item_id, @variant_basic, '기본', 34000, 50, 0),
       (@contract_item_id, @variant_set, '2개 세트', 60000, 20, 1);

-- 5. 진행 중 공구 -------------------------------------------------------------------------------
INSERT INTO group_buy (group_buy_number, contract_id, market_id, creator_id, status, start_at, end_at,
                       stock_confirmed_at, stock_confirmed_by, ready_at, opened_at, version, created_at, modified_at)
VALUES ('GB-PAYTEST-001', @contract_id, @market_id, @creator_id, 'IN_PROGRESS', @start_at, @end_at,
        DATE_SUB(@now, INTERVAL 2 DAY), @seller_id, DATE_SUB(@now, INTERVAL 2 DAY), @start_at, 0, @now, @now);
SET @group_buy_id = LAST_INSERT_ID();

UPDATE contract SET group_buy_id = @group_buy_id WHERE contract_id = @contract_id;

-- 6. 소비자 · 기본 배송지 --------------------------------------------------------------------------
INSERT INTO users (created_at, modified_at, username, nickname, password, email, email_verified_yn, provider_type,
                   role_type, status, name, phone_number, age_agree, service_agree, privacy_agree, marketing_agree,
                   follow_post_push_agree)
VALUES (@now, @now, 'paytest', '결제테스터', @pw, 'paytest@showroomz.test', 'Y', 'LOCAL',
        'USER', 'NORMAL', '김수민', '010-1234-5678', b'1', b'1', b'1', b'0', b'1');
SET @consumer_id = LAST_INSERT_ID();

INSERT INTO DELIVERY_ADDRESS (user_id, recipient_name, zip_code, address, detail_address, phone_number, memo,
                              is_default, created_at, modified_at)
VALUES (@consumer_id, '김수민', '06234', '서울 강남구 테헤란로 000', '쇼룸타워 12층 1203호', '010-1234-5678',
        '문 앞에 놓아주세요', b'1', @now, @now);

COMMIT;

-- 7. 결과 — 주문 요청에 넣을 값 ------------------------------------------------------------------
SELECT @consumer_id AS consumer_user_id, @group_buy_id AS group_buy_id,
       @variant_basic AS variant_basic_id, @variant_set AS variant_set_id, @product_id AS product_id;

-- 주문 생성 요청 예시(POST /v1/user/orders) — 위 결과의 id 로 바꾼다.
-- {
--   "idempotencyKey": "<새 UUID>",
--   "direct": { "variantId": <variant_basic_id>, "quantity": 1, "groupBuyId": <group_buy_id> },
--   "payment": { "method": "CARD", "cardIssuer": "SHINHAN" },
--   "expectedTotalAmount": 30200
-- }


-- ============================================================================================
-- 정리 — 시드와 그 뒤에 생긴 주문·결제를 지운다. 필요할 때만 주석을 풀어 실행한다.
-- ============================================================================================
-- SET FOREIGN_KEY_CHECKS = 0;
-- DELETE pc FROM payment_cancel pc JOIN payment p ON p.payment_id = pc.payment_id JOIN orders o ON o.order_id = p.order_id JOIN users u ON u.user_id = o.user_id WHERE u.username = 'paytest';
-- DELETE p  FROM payment p  JOIN orders o ON o.order_id = p.order_id JOIN users u ON u.user_id = o.user_id WHERE u.username = 'paytest';
-- DELETE op FROM order_product op JOIN orders o ON o.order_id = op.order_id JOIN users u ON u.user_id = o.user_id WHERE u.username = 'paytest';
-- DELETE g  FROM order_delivery_group g JOIN orders o ON o.order_id = g.order_id JOIN users u ON u.user_id = o.user_id WHERE u.username = 'paytest';
-- DELETE o  FROM orders o JOIN users u ON u.user_id = o.user_id WHERE u.username = 'paytest';
-- DELETE c  FROM cart c JOIN users u ON u.user_id = c.user_id WHERE u.username = 'paytest';
-- DELETE d  FROM DELIVERY_ADDRESS d JOIN users u ON u.user_id = d.user_id WHERE u.username = 'paytest';
-- DELETE FROM group_buy WHERE group_buy_number = 'GB-PAYTEST-001';
-- DELETE cio FROM contract_item_option cio JOIN contract_item ci ON ci.contract_item_id = cio.contract_item_id JOIN contract c ON c.contract_id = ci.contract_id WHERE c.contract_number = 'CTR-PAYTEST-001';
-- DELETE ci FROM contract_item ci JOIN contract c ON c.contract_id = ci.contract_id WHERE c.contract_number = 'CTR-PAYTEST-001';
-- DELETE FROM contract WHERE contract_number = 'CTR-PAYTEST-001';
-- DELETE cr FROM creator cr JOIN users u ON u.user_id = cr.user_id WHERE u.username = 'paytest-creator';
-- DELETE FROM users WHERE username IN ('paytest', 'paytest-creator');
-- DELETE v FROM product_variant v JOIN product p ON p.product_id = v.product_id WHERE p.product_number = 'SRZ-PAYTEST-001';
-- DELETE FROM product WHERE product_number = 'SRZ-PAYTEST-001';
-- DELETE FROM category WHERE name = '결제테스트 뷰티';
-- DELETE m FROM MARKET m JOIN SELLER s ON s.seller_id = m.seller_id WHERE s.email = 'paytest-brand@showroomz.test';
-- DELETE FROM SELLER WHERE email = 'paytest-brand@showroomz.test';
-- SET FOREIGN_KEY_CHECKS = 1;
