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
JOIN product p ON pi.product_id = p.product_id

UNION ALL

SELECT
    CONCAT('ONE_TO_ONE:', o2o.inquiry_id, ':', market_order.market_id) AS inquiry_key,
    o2o.inquiry_id AS inquiry_id,
    'ONE_TO_ONE' AS source,
    CASE
        WHEN o2o.type = 'DELIVERY' THEN 'DELIVERY'
        WHEN o2o.type = 'ORDER_PAYMENT' THEN 'ORDER_PAYMENT'
        WHEN o2o.type = 'CANCEL_REFUND_EXCHANGE' THEN 'CANCEL_REFUND_EXCHANGE'
        WHEN o2o.category IN ('DEFECT', 'AS') THEN 'DEFECT_AS'
    END AS filter_type,
    o2o.content AS content,
    COALESCE(u.name, u.nickname) AS customer_name,
    representative_product.name AS product_name,
    market_order.market_id AS market_id,
    o2o.created_at AS created_at,
    o2o.status AS status
FROM one_to_one_inquiry o2o
JOIN users u ON o2o.user_id = u.user_id
JOIN (
    SELECT
        op.order_id,
        p.market_id,
        MIN(p.product_id) AS representative_product_id
    FROM order_product op
    JOIN product_variant pv ON op.variant_id = pv.variant_id
    JOIN product p ON pv.product_id = p.product_id
    GROUP BY op.order_id, p.market_id
) market_order ON market_order.order_id = o2o.order_id
JOIN product representative_product
    ON representative_product.product_id = market_order.representative_product_id
WHERE o2o.type IN ('DELIVERY', 'ORDER_PAYMENT', 'CANCEL_REFUND_EXCHANGE')
   OR o2o.category IN ('DEFECT', 'AS');
