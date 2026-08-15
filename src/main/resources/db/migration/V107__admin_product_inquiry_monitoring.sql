-- §18 어드민 상품 문의 모니터링
-- 운영자의 삭제 사유(§18-5)와 반려 사유(§18-6)는 브랜드가 삭제를 "요청"할 때 고르는
-- delete_request_reason(§23-5)과는 다른 선택지를 쓴다. V105에서 자유 텍스트로 잡았던
-- 내부 사유·반려 사유 컬럼을 "코드 + 상세" 구조로 분리한다.

ALTER TABLE product_inquiry
    RENAME COLUMN `delete_reason` TO `delete_reason_detail`;

ALTER TABLE product_inquiry
    ADD COLUMN `delete_reason_type` VARCHAR(32) NULL
        COMMENT '삭제 사유(운영자, 내부 기록): ADVERTISEMENT, ABUSE, PRIVACY_EXPOSURE, ETC'
        AFTER `delete_reject_reason`;

ALTER TABLE product_inquiry
    RENAME COLUMN `delete_reject_reason` TO `delete_reject_reason_detail`;

ALTER TABLE product_inquiry
    ADD COLUMN `delete_reject_reason_type` VARCHAR(32) NULL
        COMMENT '반려 사유(운영자, 요청 브랜드에 전달): NOT_QUALIFYING, INSUFFICIENT_EVIDENCE, NORMAL_INQUIRY, ETC'
        AFTER `delete_reviewed_at`,
    ADD COLUMN `delete_processed_by` BIGINT NULL
        COMMENT '삭제 집행/반려를 처리한 운영자(seller_id)'
        AFTER `delete_reason_detail`;

-- 이력 이벤트의 행위자(운영자) ID — 두 번째 운영자가 다시 처리해도 과거 이력의 행위자가
-- 바뀌지 않도록 이벤트 시점의 행위자를 고정한다. CONSUMER·BRAND 이벤트는 채우지 않는다.
ALTER TABLE product_inquiry_history
    ADD COLUMN `actor_id` BIGINT NULL COMMENT '행위자 ID — OPERATOR 이벤트에서만 값이 있다';
