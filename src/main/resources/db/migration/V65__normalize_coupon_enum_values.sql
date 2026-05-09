-- Normalize legacy coupon enum values after V64 schema rename.
UPDATE coupon
SET discount_unit = 'PERCENT'
WHERE discount_unit = 'PERCENTAGE';

UPDATE coupon
SET discount_unit = 'AMOUNT'
WHERE discount_unit = 'FIXED_AMOUNT';

UPDATE coupon
SET status = 'WAITING'
WHERE status = 'SCHEDULED';
