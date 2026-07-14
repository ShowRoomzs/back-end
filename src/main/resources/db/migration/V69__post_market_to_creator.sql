-- 1. 기존 외래 키 제약 조건 해제
ALTER TABLE post
    DROP FOREIGN KEY FKl8xkjvcym3fq9lervw5tm6do9;

-- 2. 자식 테이블 데이터 전체 삭제 (외래 키 참조 무결성 위반 방지)
-- post 테이블을 참조하고 있는 모든 자식 테이블의 데이터를 먼저 비웁니다.
DELETE FROM post_images;
DELETE FROM post_wishlist;
DELETE FROM post_product;

-- 3. 부모 테이블 데이터 전체 삭제
-- 자식 데이터가 모두 삭제되었으므로 post 테이블의 데이터도 안전하게 전체 삭제합니다.
DELETE FROM post;

-- 4. 컬럼명 변경 (market_id -> creator_id)
-- 데이터가 비어있는 상태이므로 아무런 충돌 없이 컬럼 속성이 변경됩니다.
ALTER TABLE post
    CHANGE COLUMN market_id creator_id BIGINT NOT NULL;

-- 5. 새로운 외래 키 제약 조건 추가
-- 데이터가 존재하지 않으므로 참조 무결성 검사를 무사히 통과하고 제약 조건이 생성됩니다.
ALTER TABLE post
    ADD CONSTRAINT FK_post_creator_id
    FOREIGN KEY (creator_id)
    REFERENCES creator (creator_id);