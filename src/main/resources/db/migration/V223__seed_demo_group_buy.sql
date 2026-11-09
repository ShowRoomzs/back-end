-- 데모 공구 시드 (2026-11-09) — 실제 흐름(입점 → 상품 등록 → 연결 → 계약 체결 → 공구 생성 · 오픈)을 거친 결과 상태를 그대로 넣는다.
--
--   브랜드      루엔 스킨랩       seller  luen.skinlab@example.com   (파트너센터 로그인)
--   인플루언서  하린의 데일리 뷰티  users   harin_daily                (앱 LOCAL 로그인 · 스튜디오)
--   구매자      쇼룸즈테스터       users   srz_buyer                  (앱 LOCAL 로그인 · 기본 배송지 있음)
--   비밀번호는 셋 다 같다. 평문은 저장소에 두지 않는다(BCrypt 해시만).
--
--   상품 3종(옵션형 2 · 단품 1) → 계약 1건(CONCLUDED) → 공구 1건(IN_PROGRESS) + 승인 · 노출 중인 공구 게시물.
--   공구 기간은 이 마이그레이션이 도는 날 기준 「한국 날짜 어제 00:00 ~ +28일 23:59:59」(30일 · 계약 상한)이다.
--
-- 고정 id 를 쓰지 않는다 — 운영 DB 에는 기존 데이터가 있어 모든 FK 는 방금 넣은 행의 LAST_INSERT_ID() 로 잇는다.
-- 시각은 서버 세션 시간대를 따른다(운영 UTC · 로컬 KST). 공구 · 계약 번호는 실제 발급기와 같은 시퀀스 테이블에서 받는다.
-- 이메일 · 아이디 · 브랜드명 · 쇼룸명 중 하나라도 이미 있으면 아무것도 넣지 않는다(배포를 멈추지 않는다).
-- 카테고리 「스킨케어」가 없을 때만 「뷰티 > 스킨케어」를 만든다.
-- 이미지는 picsum.photos 자리표시 이미지다. 계약 문서(서명본 PDF · 감사 추적)는 실제 파일이 없어 넣지 않는다.

DROP PROCEDURE IF EXISTS v223_seed_demo_group_buy;

DELIMITER $$

CREATE PROCEDURE v223_seed_demo_group_buy()
seed: BEGIN
    DECLARE v_password VARCHAR(128) DEFAULT '$2a$10$a29vQ9LOHoRA8hJVHBpZVub5FikR2B3VQeLmGLbxpQJiF6eChErFe';
    DECLARE v_brand_email VARCHAR(100) DEFAULT 'luen.skinlab@example.com';
    DECLARE v_brand_name VARCHAR(100) DEFAULT '루엔 스킨랩';
    DECLARE v_showroom_name VARCHAR(100) DEFAULT '하린의 데일리 뷰티';
    DECLARE v_alphabet VARCHAR(32) DEFAULT '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

    DECLARE v_shift INT;
    DECLARE v_now, v_start, v_end DATETIME(6);
    DECLARE v_t_product, v_t_conn, v_t_contract, v_t_review_req, v_t_review_ok, v_t_brand_sign,
            v_t_creator_sign, v_t_conclude, v_t_stock, v_t_post_submit, v_t_post_approve DATETIME(6);
    DECLARE v_today, v_seq_date DATE;
    DECLARE v_seq INT;

    DECLARE v_admin BIGINT;
    DECLARE v_admin_name, v_admin_email VARCHAR(512);
    DECLARE v_cat, v_cat_root, v_seller, v_market, v_creator_user, v_creator, v_buyer, v_conn BIGINT;
    DECLARE v_p1, v_p2, v_p3, v_g, v_o1, v_o2, v_v1, v_v2 BIGINT;
    DECLARE v_contract, v_item, v_gb, v_post BIGINT;
    DECLARE v_contract_no, v_gb_no, v_conn_code VARCHAR(30);

    -- 0. 이미 있으면 건너뛴다 ------------------------------------------------------------------------------------
    IF EXISTS (SELECT 1 FROM seller WHERE email = v_brand_email)
       OR EXISTS (SELECT 1 FROM market WHERE market_name = v_brand_name)
       OR EXISTS (SELECT 1 FROM users WHERE username IN ('harin_daily', 'srz_buyer')
                                         OR email IN ('harin.daily@example.com', 'srz.buyer@example.com')
                                         OR nickname IN ('하린', '쇼룸즈테스터'))
       OR EXISTS (SELECT 1 FROM creator WHERE showroom_name = v_showroom_name OR showroom_address = 'harin_daily') THEN
        LEAVE seed;
    END IF;

    -- 1. 시각 ------------------------------------------------------------------------------------------------------
    -- v_shift = 세션 시각 − KST(분). 운영(UTC) −540 · 로컬(KST) 0. 「한국 날짜의 HH:MM」을 세션 시각으로 바꿀 때 더한다.
    SET v_shift = TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(), NOW()) - 540;
    SET v_now = NOW(6);
    SET v_today = DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR);
    SET v_start = TIMESTAMP(v_today - INTERVAL 1 DAY) + INTERVAL v_shift MINUTE;
    SET v_end = TIMESTAMP(v_today + INTERVAL 28 DAY, '23:59:59') + INTERVAL v_shift MINUTE;

    SET v_t_product = v_now - INTERVAL 20 DAY;
    SET v_t_conn = v_now - INTERVAL 16 DAY;
    SET v_t_contract = v_now - INTERVAL 14 DAY;
    SET v_t_review_req = v_now - INTERVAL 13 DAY;          -- H5: 시작일이 검토 요청 + 7일 이후
    SET v_t_review_ok = v_now - INTERVAL 12 DAY;
    SET v_t_brand_sign = v_now - INTERVAL 11 DAY;
    SET v_t_creator_sign = v_now - INTERVAL 10 DAY - INTERVAL 3 HOUR;
    SET v_t_conclude = v_now - INTERVAL 10 DAY;
    SET v_t_stock = v_now - INTERVAL 8 DAY;
    SET v_t_post_submit = v_now - INTERVAL 7 DAY;
    SET v_t_post_approve = v_now - INTERVAL 6 DAY;

    SET v_admin = (SELECT seller_id FROM seller WHERE role_type = 'ADMIN' ORDER BY seller_id LIMIT 1);
    SET v_admin_name = (SELECT name FROM seller WHERE seller_id = v_admin);
    SET v_admin_email = (SELECT email FROM seller WHERE seller_id = v_admin);

    -- 2. 카테고리 ---------------------------------------------------------------------------------------------------
    SET v_cat = (SELECT category_id FROM category WHERE name = '스킨케어' ORDER BY category_id LIMIT 1);
    IF v_cat IS NULL THEN
        SET v_cat_root = (SELECT category_id FROM category WHERE name = '뷰티' AND parent_id IS NULL ORDER BY category_id LIMIT 1);
        IF v_cat_root IS NULL THEN
            INSERT INTO category (name, parent_id, `order`)
            SELECT '뷰티', NULL, COALESCE(MAX(`order`), 0) + 1 FROM category WHERE parent_id IS NULL;
            SET v_cat_root = LAST_INSERT_ID();
        END IF;
        INSERT INTO category (name, parent_id, `order`)
        SELECT '스킨케어', v_cat_root, COALESCE(MAX(`order`), 0) + 1 FROM category WHERE parent_id = v_cat_root;
        SET v_cat = LAST_INSERT_ID();
    END IF;

    -- 3. 브랜드(입점 승인 · 배송 설정 완료) --------------------------------------------------------------------------
    INSERT INTO seller (created_at, modified_at, email, password, name, phone_number, role_type, status,
                        business_type, representative_name, representative_contact, company_name, business_reg_number,
                        business_condition, business_address, detail_address, tax_email, mail_order_reg_num,
                        bank_name, account_holder, account_number,
                        agree_privacy_policy, agree_terms_of_service, agree_operation_policy,
                        processed_at, is_new_member, last_login_at)
    VALUES (v_t_product - INTERVAL 10 DAY, v_t_product, v_brand_email, v_password, '정유나', '010-4821-7730', 'SELLER', 'APPROVED',
            '법인 사업자', '정유나', '010-4821-7730', '주식회사 루엔코스메틱', '214-87-31952',
            '제조업, 도소매 / 화장품', '서울특별시 성동구 성수이로 77', '4층 401호', 'tax.luen@example.com', '2025-서울성동-1274',
            '국민은행', '주식회사 루엔코스메틱', '612401-04-118230',
            1, 1, 1,
            v_t_product - INTERVAL 8 DAY, 0, v_now - INTERVAL 1 DAY);
    SET v_seller = LAST_INSERT_ID();

    INSERT INTO seller_application (seller_id, market_name, cs_number, seller_name, seller_contact, business_type,
                                    representative_name, representative_contact, company_name, business_reg_number,
                                    business_condition, business_address, detail_address, tax_email, mail_order_reg_num,
                                    bank_name, account_holder, account_number,
                                    agree_privacy_policy, agree_terms_of_service, agree_operation_policy,
                                    status, processed_at, created_at, modified_at)
    VALUES (v_seller, v_brand_name, '02-6953-1024', '정유나', '010-4821-7730', '법인 사업자',
            '정유나', '010-4821-7730', '주식회사 루엔코스메틱', '214-87-31952',
            '제조업, 도소매 / 화장품', '서울특별시 성동구 성수이로 77', '4층 401호', 'tax.luen@example.com', '2025-서울성동-1274',
            '국민은행', '주식회사 루엔코스메틱', '612401-04-118230',
            1, 1, 1,
            'APPROVED', v_t_product - INTERVAL 8 DAY, v_t_product - INTERVAL 10 DAY, v_t_product - INTERVAL 8 DAY);

    INSERT INTO market (seller_id, market_name, cs_number, market_image_url, market_description, main_category_id, status,
                        shipping_recipient_name, shipping_contact, shipping_address, shipping_detail_address,
                        default_delivery_fee, free_shipping_threshold, jeju_surcharge, remote_area_surcharge, shipping_lead_days)
    VALUES (v_seller, v_brand_name, '02-6953-1024', 'https://picsum.photos/seed/srz-luen-logo/400/400',
            '예민한 피부를 위한 저자극 더마 스킨케어. 성분은 덜어내고, 진정과 장벽에 집중합니다.', v_cat, 'ACTIVE',
            '루엔 스킨랩 물류센터', '031-8041-2290', '경기도 김포시 고촌읍 아라육로 152', 'B동 2층 루엔 스킨랩',
            3000, 30000, 3000, 5000, 2);
    SET v_market = LAST_INSERT_ID();
    UPDATE market SET market_url = CONCAT('https://showroomz.co.kr/market/', v_market) WHERE market_id = v_market;

    INSERT INTO market_sns (market_id, sns_type, sns_url)
    VALUES (v_market, 'INSTAGRAM', 'https://www.instagram.com/showroomz_seed_luen');

    -- 4. 인플루언서(신청 승인 · 온보딩 완료) --------------------------------------------------------------------------
    INSERT INTO users (username, nickname, email, password, email_verified_yn, provider_type, role_type, status,
                       name, phone_number, birthday, gender, profile_image_url,
                       service_agree, privacy_agree, marketing_agree, age_agree, created_at, modified_at, last_login_at)
    VALUES ('harin_daily', '하린', 'harin.daily@example.com', v_password, 'Y', 'LOCAL', 'CREATOR', 'NORMAL',
            '한하린', '010-5530-2817', '1997-04-12', 'FEMALE', 'https://picsum.photos/seed/srz-harin-app/400/400',
            1, 1, 0, 1, v_t_conn - INTERVAL 30 DAY, v_t_conn - INTERVAL 20 DAY, v_now - INTERVAL 2 HOUR);
    SET v_creator_user = LAST_INSERT_ID();

    INSERT INTO creator_application (user_id, sns_type, channel_url, account_id, follower_count, business_email, real_name,
                                     birthday, phone_number, status, processed_at, processor_email, created_at, modified_at)
    VALUES (v_creator_user, 'INSTAGRAM', 'https://www.instagram.com/showroomz_seed_harin', 'showroomz_seed_harin', 48200,
            'harin.daily@example.com', '한하린', '1997-04-12', '010-5530-2817', 'APPROVED',
            v_t_conn - INTERVAL 22 DAY, v_admin_email, v_t_conn - INTERVAL 25 DAY, v_t_conn - INTERVAL 22 DAY);

    -- 연결코드는 실제 발급기와 같은 32자 알파벳 10자리를 무작위로 만든다(저장소에 고정 코드를 남기지 않는다).
    SET v_conn_code = CONCAT(
        SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1), SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1),
        SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1), SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1),
        SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1), SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1),
        SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1), SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1),
        SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1), SUBSTRING(v_alphabet, 1 + FLOOR(RAND() * 32), 1));

    INSERT INTO creator (user_id, follower_count, business_email, real_name, birthday, phone_number, channel_url, sns_type,
                         account_id, is_new_member, showroom_name, profile_image_url, showroom_address, introduction,
                         instagram_url, connection_code, connection_code_issued_at, status, created_at, modified_at)
    VALUES (v_creator_user, 48200, 'harin.daily@example.com', '한하린', '1997-04-12', '010-5530-2817',
            'https://www.instagram.com/showroomz_seed_harin', 'INSTAGRAM', 'showroomz_seed_harin', 0,
            v_showroom_name, 'https://picsum.photos/seed/srz-harin-profile/400/400', 'harin_daily',
            '민감 피부 9년 차, 직접 써 보고 남은 것만 소개해요', 'https://www.instagram.com/showroomz_seed_harin',
            v_conn_code, v_t_conn - INTERVAL 22 DAY, 'ACTIVE', v_t_conn - INTERVAL 22 DAY, v_t_conn - INTERVAL 21 DAY);
    SET v_creator = LAST_INSERT_ID();

    -- 5. 구매자 + 기본 배송지 ---------------------------------------------------------------------------------------
    INSERT INTO users (username, nickname, email, password, email_verified_yn, provider_type, role_type, status,
                       name, phone_number, service_agree, privacy_agree, marketing_agree, age_agree, created_at, modified_at)
    VALUES ('srz_buyer', '쇼룸즈테스터', 'srz.buyer@example.com', v_password, 'Y', 'LOCAL', 'USER', 'NORMAL',
            '김구매', '010-2384-9051', 1, 1, 0, 1, v_now - INTERVAL 30 DAY, v_now - INTERVAL 30 DAY);
    SET v_buyer = LAST_INSERT_ID();

    INSERT INTO delivery_address (user_id, recipient_name, phone_number, zip_code, address, detail_address, memo, is_default,
                                  created_at, modified_at)
    VALUES (v_buyer, '김구매', '010-2384-9051', '04790', '서울특별시 성동구 성수이로 77', '쇼룸즈 빌딩 5층', '문 앞에 놓아 주세요', 1,
            v_now - INTERVAL 30 DAY, v_now - INTERVAL 30 DAY);

    -- 6. 상품 3종 ---------------------------------------------------------------------------------------------------
    -- 6-1. 시카 리페어 앰플 — 옵션 「용량」 30ml / 50ml(+12,000)
    INSERT INTO product (market_id, category_id, product_number, seller_product_code, name, regular_price, sale_price,
                         thumbnail_url, description, product_notice, gender, display_status, group_buy_status,
                         is_out_of_stock_forced, is_recommended, created_at, modified_at)
    VALUES (v_market, v_cat, CONCAT('SRZ-', DATE_FORMAT(DATE(v_t_product - INTERVAL v_shift MINUTE), '%Y%m%d'), '-901'),
            'LUEN-AMP-CICA', '시카 리페어 앰플', 32000, 32000,
            'https://picsum.photos/seed/srz-cica-1/800/800',
            '<h3>붉어진 피부를 30초 만에 진정시키는 시카 앰플</h3><p>병풀추출물 72%와 판테놀 5%를 담아 외부 자극으로 예민해진 피부를 빠르게 진정시켜요. 끈적임 없이 흡수되어 아침 메이크업 전에도 부담 없어요.</p><img src="https://picsum.photos/seed/srz-cica-d1/860/1100" alt="시카 리페어 앰플 상세 1"><p>· 피부 자극 테스트 완료<br>· 20가지 유해 의심 성분 무첨가<br>· 비건 인증 포뮬러</p><img src="https://picsum.photos/seed/srz-cica-d2/860/1100" alt="시카 리페어 앰플 상세 2">',
            JSON_OBJECT('capacityWeight', '30ml / 50ml', 'mainSpecs', '모든 피부용(민감성 피부 추천)',
                        'expirationPeriod', '제조일로부터 30개월 · 개봉 후 12개월', 'usageMethod', '세안 후 토너 다음 단계에서 2~3방울을 덜어 얼굴 전체에 흡수시켜 주세요.',
                        'manufacturerSeller', '제조업자 (주)코스랩 / 책임판매업자 (주)루엔코스메틱', 'origin', '대한민국',
                        'ingredients', '병풀추출물, 정제수, 부틸렌글라이콜, 판테놀, 글리세린, 나이아신아마이드, 마데카소사이드, 알란토인, 1,2-헥산다이올',
                        'functionalCosmeticApproval', '해당 없음',
                        'precautions', '화장품 사용 시 또는 사용 후 직사광선에 의해 붉은 반점, 부어오름 또는 가려움증 등의 이상 증상이나 부작용이 있는 경우 전문의 등과 상담할 것',
                        'qualityAssurance', '공정거래위원회 고시 소비자분쟁해결기준에 따름', 'customerServicePhone', '02-6953-1024'),
            'UNISEX', 'DISPLAY', 'IN_PROGRESS', b'0', b'1', v_t_product, v_t_product);
    SET v_p1 = LAST_INSERT_ID();
    INSERT INTO product_image (product_id, url, `order`) VALUES
        (v_p1, 'https://picsum.photos/seed/srz-cica-1/800/800', 0),
        (v_p1, 'https://picsum.photos/seed/srz-cica-2/800/800', 1),
        (v_p1, 'https://picsum.photos/seed/srz-cica-3/800/800', 2);
    INSERT INTO product_option_group (product_id, name) VALUES (v_p1, '용량');
    SET v_g = LAST_INSERT_ID();
    INSERT INTO product_option (option_group_id, name, price) VALUES (v_g, '30ml', 0);
    SET v_o1 = LAST_INSERT_ID();
    INSERT INTO product_option (option_group_id, name, price) VALUES (v_g, '50ml', 12000);
    SET v_o2 = LAST_INSERT_ID();
    INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative) VALUES (v_p1, '30ml', 32000, 32000, 300, b'1');
    SET v_v1 = LAST_INSERT_ID();
    INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative) VALUES (v_p1, '50ml', 44000, 44000, 200, b'0');
    SET v_v2 = LAST_INSERT_ID();
    INSERT INTO variant_option_map (variant_id, option_id) VALUES (v_v1, v_o1), (v_v2, v_o2);

    -- 6-2. 히알루론 수분 크림 — 단품(옵션 없음)
    INSERT INTO product (market_id, category_id, product_number, seller_product_code, name, regular_price, sale_price,
                         thumbnail_url, description, product_notice, gender, display_status, group_buy_status,
                         is_out_of_stock_forced, is_recommended, created_at, modified_at)
    VALUES (v_market, v_cat, CONCAT('SRZ-', DATE_FORMAT(DATE(v_t_product - INTERVAL v_shift MINUTE), '%Y%m%d'), '-902'),
            'LUEN-CRM-HYAL', '히알루론 수분 크림 80ml', 36000, 36000,
            'https://picsum.photos/seed/srz-hyal-1/800/800',
            '<h3>속은 촉촉, 겉은 산뜻한 젤 크림</h3><p>8중 히알루론산이 피부 깊이 수분을 채우고, 세라마이드 NP가 수분 증발을 막아 하루 종일 당김 없이 편안해요.</p><img src="https://picsum.photos/seed/srz-hyal-d1/860/1100" alt="히알루론 수분 크림 상세 1"><p>· 피부 보습력 개선 인체적용시험 완료<br>· 산뜻한 젤 제형으로 지성 피부도 OK</p><img src="https://picsum.photos/seed/srz-hyal-d2/860/1100" alt="히알루론 수분 크림 상세 2">',
            JSON_OBJECT('capacityWeight', '80ml', 'mainSpecs', '모든 피부용',
                        'expirationPeriod', '제조일로부터 30개월 · 개봉 후 12개월', 'usageMethod', '스킨케어 마지막 단계에서 적당량을 덜어 얼굴 전체에 펴 바른 뒤 가볍게 두드려 흡수시켜 주세요.',
                        'manufacturerSeller', '제조업자 (주)코스랩 / 책임판매업자 (주)루엔코스메틱', 'origin', '대한민국',
                        'ingredients', '정제수, 글리세린, 프로판다이올, 소듐하이알루로네이트, 하이드롤라이즈드하이알루로닉애씨드, 세라마이드엔피, 판테놀, 베타인, 카보머',
                        'functionalCosmeticApproval', '해당 없음',
                        'precautions', '상처가 있는 부위 등에는 사용을 자제할 것. 어린이의 손이 닿지 않는 곳에 보관할 것',
                        'qualityAssurance', '공정거래위원회 고시 소비자분쟁해결기준에 따름', 'customerServicePhone', '02-6953-1024'),
            'UNISEX', 'DISPLAY', 'IN_PROGRESS', b'0', b'0', v_t_product + INTERVAL 1 HOUR, v_t_product + INTERVAL 1 HOUR);
    SET v_p2 = LAST_INSERT_ID();
    INSERT INTO product_image (product_id, url, `order`) VALUES
        (v_p2, 'https://picsum.photos/seed/srz-hyal-1/800/800', 0),
        (v_p2, 'https://picsum.photos/seed/srz-hyal-2/800/800', 1);
    INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative) VALUES (v_p2, NULL, 36000, 36000, 250, b'1');

    -- 6-3. 마일드 선크림 — 옵션 「구성」 단품 / 1+1(+19,000)
    INSERT INTO product (market_id, category_id, product_number, seller_product_code, name, regular_price, sale_price,
                         thumbnail_url, description, product_notice, gender, display_status, group_buy_status,
                         is_out_of_stock_forced, is_recommended, created_at, modified_at)
    VALUES (v_market, v_cat, CONCAT('SRZ-', DATE_FORMAT(DATE(v_t_product - INTERVAL v_shift MINUTE), '%Y%m%d'), '-903'),
            'LUEN-SUN-MILD', '마일드 무기자차 선크림 SPF50+ PA++++', 24000, 24000,
            'https://picsum.photos/seed/srz-sun-1/800/800',
            '<h3>백탁 없이 순하게, 데일리 무기자차</h3><p>논나노 징크옥사이드로 자외선을 반사하고, 톤업 없이 투명하게 마무리돼요. 눈시림 테스트를 마쳐 아이 주변까지 편하게 바를 수 있어요.</p><img src="https://picsum.photos/seed/srz-sun-d1/860/1100" alt="마일드 선크림 상세 1"><p>· 자외선 차단 · 미백 이중 기능성<br>· 눈시림 테스트 완료</p>',
            JSON_OBJECT('capacityWeight', '50ml', 'mainSpecs', '모든 피부용',
                        'expirationPeriod', '제조일로부터 24개월 · 개봉 후 6개월', 'usageMethod', '외출 15~30분 전 적당량을 얼굴과 목에 고르게 펴 발라 주세요. 2~3시간마다 덧발라 주세요.',
                        'manufacturerSeller', '제조업자 (주)코스랩 / 책임판매업자 (주)루엔코스메틱', 'origin', '대한민국',
                        'ingredients', '정제수, 징크옥사이드, 카프릴릭/카프릭트라이글리세라이드, 부틸렌글라이콜, 나이아신아마이드, 병풀추출물, 토코페롤',
                        'functionalCosmeticApproval', '자외선 차단 · 미백 기능성 화장품 식품의약품안전처 심사필',
                        'precautions', '눈에 들어갔을 때에는 즉시 씻어낼 것. 직사광선을 피해서 보관할 것',
                        'qualityAssurance', '공정거래위원회 고시 소비자분쟁해결기준에 따름', 'customerServicePhone', '02-6953-1024'),
            'UNISEX', 'DISPLAY', 'IN_PROGRESS', b'0', b'0', v_t_product + INTERVAL 2 HOUR, v_t_product + INTERVAL 2 HOUR);
    SET v_p3 = LAST_INSERT_ID();
    INSERT INTO product_image (product_id, url, `order`) VALUES
        (v_p3, 'https://picsum.photos/seed/srz-sun-1/800/800', 0),
        (v_p3, 'https://picsum.photos/seed/srz-sun-2/800/800', 1);
    INSERT INTO product_option_group (product_id, name) VALUES (v_p3, '구성');
    SET v_g = LAST_INSERT_ID();
    INSERT INTO product_option (option_group_id, name, price) VALUES (v_g, '단품', 0);
    SET v_o1 = LAST_INSERT_ID();
    INSERT INTO product_option (option_group_id, name, price) VALUES (v_g, '1+1', 19000);
    SET v_o2 = LAST_INSERT_ID();
    INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative) VALUES (v_p3, '단품', 24000, 24000, 400, b'1');
    SET v_v1 = LAST_INSERT_ID();
    INSERT INTO product_variant (product_id, name, regular_price, sale_price, stock, is_representative) VALUES (v_p3, '1+1', 43000, 43000, 150, b'0');
    SET v_v2 = LAST_INSERT_ID();
    INSERT INTO variant_option_map (variant_id, option_id) VALUES (v_v1, v_o1), (v_v2, v_o2);

    INSERT INTO product_processing_history (product_id, history_type, new_display_status, created_at)
    SELECT product_id, 'PRODUCT_CREATED', 'DISPLAY', created_at FROM product WHERE product_id IN (v_p1, v_p2, v_p3);

    -- 7. 브랜드 ↔ 인플루언서 연결 -----------------------------------------------------------------------------------
    INSERT INTO connection (type, market_id, creator_id, status, requested_at, responded_at, created_at, modified_at)
    VALUES ('PAIR', v_market, v_creator, 'CONNECTED', v_t_conn, v_t_conn + INTERVAL 5 HOUR, v_t_conn, v_t_conn + INTERVAL 5 HOUR);
    SET v_conn = LAST_INSERT_ID();

    -- 8. 계약(체결 완료) ---------------------------------------------------------------------------------------------
    -- 계약번호는 검토 요청일(한국 날짜)에 발급된다 — ContractNumberGenerator 와 같은 시퀀스.
    SET v_seq_date = DATE(v_t_review_req - INTERVAL v_shift MINUTE);
    INSERT INTO contract_number_sequence (seq_date, last_seq) VALUES (v_seq_date, 1) ON DUPLICATE KEY UPDATE last_seq = last_seq + 1;
    SET v_seq = (SELECT last_seq FROM contract_number_sequence WHERE seq_date = v_seq_date);
    SET v_contract_no = CONCAT('CTR-', DATE_FORMAT(v_seq_date, '%Y%m%d'), '-', LPAD(v_seq, 3, '0'));

    INSERT INTO contract (contract_number, market_id, creator_id, connection_id, title, status,
                          group_buy_start_at, group_buy_end_at,
                          fixed_fee_amount, fixed_fee_trigger, fixed_fee_notice_agreed_at,
                          content_feed_count, content_reels_count, content_story_count, content_due_date,
                          secondary_use_allowed, secondary_use_period_type, secondary_use_months, brand_pre_review,
                          note, clause_version_id, warning_flags,
                          review_requested_at, review_approved_at, signature_requested_at, signature_deadline_at,
                          brand_signed_at, creator_signed_at, signature_as_of, creator_viewed_at,
                          established_at, concluded_at, last_saved_at, version, created_at, modified_at)
    VALUES (v_contract_no, v_market, v_creator, v_conn, '루엔 스킨랩 × 하린 진정 케어 공구', 'CONCLUDED',
            v_start, v_end,
            300000, 'POST_REGISTERED', v_t_review_req,
            1, 1, 2, v_today + INTERVAL 3 DAY,
            b'1', 'FIXED', 12, b'0',
            '피드 1건은 시카 앰플 단독 컷으로 부탁드립니다. 릴스는 세안 후 루틴 형식이면 좋겠습니다.',
            (SELECT contract_clause_version_id FROM contract_clause_version WHERE status = 'EFFECTIVE'
              ORDER BY effective_date DESC, contract_clause_version_id DESC LIMIT 1),
            'W3',
            v_t_review_req, v_t_review_ok, v_t_review_ok, v_t_review_ok + INTERVAL 7 DAY,
            v_t_brand_sign, v_t_creator_sign, v_t_creator_sign, v_t_review_ok + INTERVAL 2 HOUR,
            v_t_creator_sign, v_t_conclude, v_t_review_req, 0, v_t_contract, v_t_conclude);
    SET v_contract = LAST_INSERT_ID();

    -- 계약 항목 · 옵션 스냅샷 — 옵션 정가는 현재 variant 정가, 공구가는 상품 정가의 70%(10원 단위).
    INSERT INTO contract_item (contract_id, product_id, product_name, regular_price, group_buy_price, reward_rate, sort_order)
    VALUES (v_contract, v_p1, '시카 리페어 앰플', 32000, 22400, 15.0, 0);
    SET v_item = LAST_INSERT_ID();
    INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, min_quantity, sort_order)
    SELECT v_item, variant_id, name, regular_price, IF(is_representative, 100, 50), IF(is_representative, 0, 1)
      FROM product_variant WHERE product_id = v_p1;

    INSERT INTO contract_item (contract_id, product_id, product_name, regular_price, group_buy_price, reward_rate, sort_order)
    VALUES (v_contract, v_p2, '히알루론 수분 크림 80ml', 36000, 25200, 15.0, 1);
    SET v_item = LAST_INSERT_ID();
    INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, min_quantity, sort_order)
    SELECT v_item, variant_id, name, regular_price, 80, 0 FROM product_variant WHERE product_id = v_p2;

    INSERT INTO contract_item (contract_id, product_id, product_name, regular_price, group_buy_price, reward_rate, sort_order)
    VALUES (v_contract, v_p3, '마일드 무기자차 선크림 SPF50+ PA++++', 24000, 16800, 12.0, 2);
    SET v_item = LAST_INSERT_ID();
    INSERT INTO contract_item_option (contract_item_id, variant_id, variant_name, regular_price, min_quantity, sort_order)
    SELECT v_item, variant_id, name, regular_price, IF(is_representative, 100, 30), IF(is_representative, 0, 1)
      FROM product_variant WHERE product_id = v_p3;

    -- 9. 공구(진행 중) — 번호는 체결일(한국 날짜)에 GroupBuyNumberGenerator 와 같은 시퀀스로 받는다 ------------------------
    SET v_seq_date = DATE(v_t_conclude - INTERVAL v_shift MINUTE);
    INSERT INTO group_buy_number_sequence (seq_date, last_seq) VALUES (v_seq_date, 1) ON DUPLICATE KEY UPDATE last_seq = last_seq + 1;
    SET v_seq = (SELECT last_seq FROM group_buy_number_sequence WHERE seq_date = v_seq_date);
    SET v_gb_no = CONCAT('GB-', DATE_FORMAT(v_seq_date, '%Y%m%d'), '-', LPAD(v_seq, 3, '0'));

    INSERT INTO group_buy (group_buy_number, contract_id, market_id, creator_id, status, start_at, end_at,
                           stock_confirmed_at, stock_confirmed_by, ready_at, opened_at, version, created_at, modified_at)
    VALUES (v_gb_no, v_contract, v_market, v_creator, 'IN_PROGRESS', v_start, v_end,
            v_t_stock, v_seller, v_t_post_approve, v_start, 0, v_t_conclude, v_start);
    SET v_gb = LAST_INSERT_ID();
    UPDATE contract SET group_buy_id = v_gb WHERE contract_id = v_contract;

    -- 10. 공구 게시물(제출 · 운영자 승인 · 노출 중) ---------------------------------------------------------------------
    INSERT INTO post (creator_id, post_type, status, content, aspect_ratio, published_at, created_at, modified_at)
    VALUES (v_creator, 'GROUP_BUY', 'PUBLISHED',
            '환절기만 되면 볼이 빨갛게 올라오는 분들 손 들어 주세요!\n\n저도 매년 이맘때 고생하는데 올해는 루엔 시카 앰플로 버티고 있어요. 세안하고 토너 다음에 2~3방울이면 열감이 금방 가라앉고, 끈적임이 없어서 아침에도 부담 없어요.\n\n수분 크림은 젤 타입이라 지성인 남편도 같이 쓰는 중이고, 선크림은 무기자차인데 백탁이 거의 없어서 놀랐어요.\n\n이번 공구는 앰플 · 크림 · 선크림 세 가지 모두 정가보다 30% 저렴하게 준비했어요. 3만 원 이상이면 무료배송이에요!',
            0.8000, v_start, v_t_stock, v_start);
    SET v_post = LAST_INSERT_ID();
    INSERT INTO post_image (post_id, sort_order, image_url, original_url, width, height, file_size, created_at) VALUES
        (v_post, 0, 'https://picsum.photos/seed/srz-post-1/800/1000', 'https://picsum.photos/seed/srz-post-1/1600/2000', 800, 1000, 184320, v_t_stock),
        (v_post, 1, 'https://picsum.photos/seed/srz-post-2/800/1000', 'https://picsum.photos/seed/srz-post-2/1600/2000', 800, 1000, 176128, v_t_stock),
        (v_post, 2, 'https://picsum.photos/seed/srz-post-3/800/1000', 'https://picsum.photos/seed/srz-post-3/1600/2000', 800, 1000, 192512, v_t_stock);

    INSERT INTO group_buy_post (post_id, group_buy_id, title, review_status, submitted_at, reviewed_at, reviewed_by,
                                last_edited_at, created_at, modified_at)
    VALUES (v_post, v_gb, '환절기 붉은기 잡는 진정 루틴 3종 공구', 'APPROVED', v_t_post_submit, v_t_post_approve, v_admin,
            v_t_post_submit, v_t_stock, v_t_post_approve);
    INSERT INTO group_buy_post_revision (post_id, revision_no, kind, title, content, created_at, created_by)
    SELECT v_post, 1, 'SUBMITTED', '환절기 붉은기 잡는 진정 루틴 3종 공구', content, v_t_post_submit, v_creator FROM post WHERE post_id = v_post;

    -- 11. 이력 -------------------------------------------------------------------------------------------------------
    INSERT INTO contract_history (contract_id, event_type, actor_type, actor_id, actor_display_name, detail, occurred_at) VALUES
        (v_contract, 'CREATED',            'SELLER',  v_market,  v_brand_name,    NULL,    v_t_contract),
        (v_contract, 'REVIEW_REQUESTED',   'SELLER',  v_market,  v_brand_name,    NULL,    v_t_review_req),
        (v_contract, 'REVIEW_APPROVED',    'ADMIN',   v_admin,   v_admin_name,    NULL,    v_t_review_ok),
        (v_contract, 'SIGNATURE_SENT',     'ADMIN',   v_admin,   v_admin_name,    NULL,    v_t_review_ok),
        (v_contract, 'BRAND_SIGNED',       'SELLER',  v_market,  v_brand_name,    NULL,    v_t_brand_sign),
        (v_contract, 'CREATOR_SIGNED',     'CREATOR', v_creator, v_showroom_name, NULL,    v_t_creator_sign),
        (v_contract, 'CONCLUDED',          'ADMIN',   v_admin,   v_admin_name,    NULL,    v_t_conclude),
        (v_contract, 'GROUP_BUY_CREATED',  'SYSTEM',  NULL,      NULL,            v_gb_no, v_t_conclude);

    INSERT INTO group_buy_history (group_buy_id, event_type, actor_type, actor_id, actor_display_name, detail, occurred_at) VALUES
        (v_gb, 'CREATED',          'SYSTEM',  NULL,      NULL,            NULL, v_t_conclude),
        (v_gb, 'STOCK_CONFIRMED',  'SELLER',  v_market,  v_brand_name,    NULL, v_t_stock),
        (v_gb, 'POST_SUBMITTED',   'CREATOR', v_creator, v_showroom_name, NULL, v_t_post_submit),
        (v_gb, 'OPEN_APPROVED',    'ADMIN',   v_admin,   v_admin_name,    NULL, v_t_post_approve),
        (v_gb, 'OPENED',           'SYSTEM',  NULL,      NULL,            NULL, v_start);
END$$

DELIMITER ;

CALL v223_seed_demo_group_buy();

DROP PROCEDURE v223_seed_demo_group_buy;
