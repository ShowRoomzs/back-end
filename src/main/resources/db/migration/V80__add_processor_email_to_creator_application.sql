ALTER TABLE creator_application
    ADD COLUMN processor_email VARCHAR(512) NULL COMMENT '승인/반려 처리 운영자 이메일' AFTER processed_at;

ALTER TABLE creator_application_history
    ADD COLUMN processor_email VARCHAR(512) NULL COMMENT '처리 운영자 이메일' AFTER reason;
