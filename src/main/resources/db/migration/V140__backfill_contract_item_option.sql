-- 기존 계약 항목마다 상품의 현재 옵션 전량을 옵션 행으로 채운다(옵션 계획서 2-4).
-- 상품 하나의 옛 최소 물량을 옵션 여럿에 나눌 근거가 없으므로:
--   옵션 1개                     → 옛 값 그대로
--   옵션 여럿 · 편집 가능한 계약  → NULL (브랜드가 다시 입력한다. 검토 요청 시 검증이 잡는다)
--   옵션 여럿 · 그 외 상태        → 대표 옵션(없으면 variant_id 최솟값)에 옛 값 전부, 나머지 0
-- 옵션 정가는 계약 시점 값을 복원할 수 없어 현재 값으로 스냅샷한다.
INSERT INTO `contract_item_option`
    (`contract_item_id`, `variant_id`, `variant_name`, `regular_price`, `min_quantity`, `sort_order`)
SELECT ci.`contract_item_id`,
       v.`variant_id`,
       v.`name`,
       v.`regular_price`,
       CASE
           WHEN vc.`cnt` = 1 THEN ci.`min_quantity`
           WHEN c.`status` IN ('DRAFT', 'REVIEW_REJECTED') THEN NULL
           WHEN v.`variant_id` = vc.`rep_id` THEN ci.`min_quantity`
           WHEN ci.`min_quantity` IS NULL THEN NULL
           ELSE 0
       END,
       ROW_NUMBER() OVER (PARTITION BY ci.`contract_item_id` ORDER BY v.`variant_id`) - 1
  FROM `contract_item` ci
  JOIN `contract` c ON c.`contract_id` = ci.`contract_id`
  JOIN `product_variant` v ON v.`product_id` = ci.`product_id`
  JOIN (SELECT `product_id`,
               COUNT(*) AS `cnt`,
               COALESCE(MIN(CASE WHEN `is_representative` THEN `variant_id` END), MIN(`variant_id`)) AS `rep_id`
          FROM `product_variant`
         GROUP BY `product_id`) vc ON vc.`product_id` = ci.`product_id`;
