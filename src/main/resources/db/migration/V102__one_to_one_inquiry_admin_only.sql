-- §17 1:1 문의 개편
-- 1) 1:1 문의는 마켓으로 전달되지 않고 어드민으로만 접수된다 → 마켓 문의 뷰에서 제외
-- 2) 문의 유형을 FAQ 카테고리와 동일한 5종으로 재편하고 소분류(category)를 제거한다
-- 3) 답변 처리자(운영자) 컬럼 추가

-- 1) 마켓 문의 뷰 = 상품 문의 전용
CREATE OR REPLACE VIEW market_inquiry_view AS
SELECT
    CONCAT('PRODUCT:', pi.product_inquiry_id) AS inquiry_key,
    pi.product_inquiry_id AS inquiry_id,
    'PRODUCT' AS source,
    CASE
        WHEN pi.type = 'PRODUCT_INQUIRY' THEN 'PRODUCT'
        WHEN pi.type = 'SIZE_INQUIRY' THEN 'SIZE'
        WHEN pi.type = 'STOCK_INQUIRY' THEN 'STOCK'
    END AS filter_type,
    pi.content AS content,
    COALESCE(u.name, u.nickname) AS customer_name,
    p.name AS product_name,
    p.market_id AS market_id,
    pi.created_at AS created_at,
    pi.status AS status
FROM product_inquiry pi
JOIN users u ON pi.user_id = u.user_id
JOIN product p ON pi.product_id = p.product_id;

-- 2) 문의 유형 5종 재편 (배송 · 취소/교환/반품 · 주문·결제 · 서비스 · 계정)
ALTER TABLE one_to_one_inquiry
    MODIFY COLUMN type VARCHAR(50) NOT NULL;

UPDATE one_to_one_inquiry SET type = 'CANCEL_EXCHANGE_RETURN' WHERE type = 'CANCEL_REFUND_EXCHANGE';
UPDATE one_to_one_inquiry SET type = 'ACCOUNT' WHERE type = 'USER_INFO';
-- 폐기된 상품확인(PRODUCT_CHECK): 불량/하자·AS는 취소/교환/반품, 나머지는 서비스로 이관
UPDATE one_to_one_inquiry SET type = 'CANCEL_EXCHANGE_RETURN' WHERE type = 'PRODUCT_CHECK' AND category IN ('DEFECT', 'AS');
UPDATE one_to_one_inquiry SET type = 'SERVICE' WHERE type = 'PRODUCT_CHECK';

ALTER TABLE one_to_one_inquiry
    MODIFY COLUMN type ENUM('ACCOUNT', 'CANCEL_EXCHANGE_RETURN', 'DELIVERY', 'ORDER_PAYMENT', 'SERVICE') NOT NULL;

-- 소분류 제거 (유형은 단일 레벨 5종)
ALTER TABLE one_to_one_inquiry
    DROP COLUMN category;

-- 3) 답변 처리자 (운영자 seller_id) — 답변완료 건에만 값이 있다
ALTER TABLE one_to_one_inquiry
    ADD COLUMN answered_by BIGINT NULL;

-- 4) 마켓 답변 템플릿 카테고리도 상품 문의 3종으로 축소
--    (1:1 문의 유형으로 등록된 템플릿은 대응할 문의가 없어 PRODUCT로 이관)
ALTER TABLE answer_template
    MODIFY COLUMN category VARCHAR(50) NOT NULL;

UPDATE answer_template
SET category = 'PRODUCT'
WHERE category IN ('DELIVERY', 'ORDER_PAYMENT', 'CANCEL_REFUND_EXCHANGE', 'DEFECT_AS');

ALTER TABLE answer_template
    MODIFY COLUMN category ENUM('PRODUCT', 'SIZE', 'STOCK') NOT NULL;
