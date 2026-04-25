ALTER TABLE faq
    ADD COLUMN display_order INT NOT NULL DEFAULT 0;

SET @row_num := 0;

UPDATE faq f
JOIN (
    SELECT faq_id, (@row_num := @row_num + 1) AS new_display_order
    FROM faq
    ORDER BY created_at ASC, faq_id ASC
) ordered ON ordered.faq_id = f.faq_id
SET f.display_order = ordered.new_display_order;

CREATE INDEX idx_faq_display_order ON faq(display_order);
