-- 1. [권장] 세이프 업데이트 모드 일시 해제
SET SQL_SAFE_UPDATES = 0;

-- 2. 새로운 FK 컬럼 추가
ALTER TABLE market ADD COLUMN main_category_id BIGINT;

-- 3. 데이터 이관
UPDATE market m
JOIN category c ON m.main_category = c.category_id
SET m.main_category_id = c.category_id;

-- 4. 기존 컬럼 삭제
ALTER TABLE market DROP COLUMN main_category;

-- 5. 외래키(Foreign Key) 제약조건 추가
ALTER TABLE market 
ADD CONSTRAINT FK_MARKET_MAIN_CATEGORY 
FOREIGN KEY (main_category_id) REFERENCES category (category_id);

-- 6. [선택] 세이프 업데이트 모드 원상 복구
SET SQL_SAFE_UPDATES = 1;