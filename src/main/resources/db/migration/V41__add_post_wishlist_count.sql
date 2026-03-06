ALTER TABLE post
    ADD COLUMN wishlist_count BIGINT NOT NULL DEFAULT 0 AFTER view_count;

