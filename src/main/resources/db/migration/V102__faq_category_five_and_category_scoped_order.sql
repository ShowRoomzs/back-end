-- FAQ 기획 §19 반영
-- 1) 카테고리를 5종으로 고정 (배송 · 취소/교환/반품 · 주문·결제 · 서비스 · 계정, "기타" 없음)
-- 2) 노출 순서(display_order)를 카테고리 안에서만 유효한 값으로 재부여

-- 1) 폐지 카테고리 재매핑
--    PRODUCT_AS(상품/AS문의) → CANCEL_EXCHANGE_REFUND(취소/교환/반품)
--    USAGE_GUIDE(이용 안내)  → SERVICE(서비스)
--    MEMBER_INFO(회원 정보)  → ACCOUNT(계정)
UPDATE `faq`
SET `category` = CASE `category`
    WHEN 'PRODUCT_AS'  THEN 'CANCEL_EXCHANGE_REFUND'
    WHEN 'USAGE_GUIDE' THEN 'SERVICE'
    WHEN 'MEMBER_INFO' THEN 'ACCOUNT'
    ELSE `category`
END
WHERE `category` IN ('PRODUCT_AS', 'USAGE_GUIDE', 'MEMBER_INFO');

-- 2) 카테고리별로 1부터 다시 채번 (기존 순서 유지, 카테고리 통합 순서 폐지)
UPDATE `faq` f
JOIN (
    SELECT `faq_id`,
           ROW_NUMBER() OVER (
               PARTITION BY `category`
               ORDER BY `display_order` ASC, `faq_id` ASC
           ) AS new_display_order
    FROM `faq`
) ordered ON ordered.faq_id = f.faq_id
SET f.display_order = ordered.new_display_order;

-- 3) 인덱스를 (카테고리, 노출 순서) 복합으로 교체
ALTER TABLE `faq`
    DROP INDEX `idx_faq_display_order`;

CREATE INDEX `idx_faq_category_display_order` ON `faq` (`category`, `display_order`);
