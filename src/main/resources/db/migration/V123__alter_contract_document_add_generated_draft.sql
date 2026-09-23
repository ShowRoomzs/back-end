-- VARCHAR already supports GENERATED_DRAFT. Keep upload time separate from the submission cache key.
ALTER TABLE contract_document
    MODIFY COLUMN document_type VARCHAR(32) NOT NULL COMMENT 'GENERATED_DRAFT, SIGNED_PDF, AUDIT_TRAIL',
    ADD COLUMN source_review_requested_at DATETIME(6) NULL;
-- Rejection detail is up to 1000 characters, in addition to the reason code.
ALTER TABLE contract_history MODIFY COLUMN detail VARCHAR(2000) NULL;
-- Queue indexes already exist in V116 and V119; no duplicate V124 index is needed.
