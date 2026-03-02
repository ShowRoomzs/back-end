-- 1:1 문의 테이블에 주문번호 저장 컬럼 추가
ALTER TABLE one_to_one_inquiry
    ADD COLUMN order_id BIGINT NULL;
