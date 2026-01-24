-- 1. [권장] 세이프 업데이트 모드 일시 해제
SET SQL_SAFE_UPDATES = 0;

-- 2. 새로운 FK 컬럼 추가
ALTER TABLE MARKET ADD COLUMN MAIN_CATEGORY_ID BIGINT;

-- 3. 데이터 이관
UPDATE MARKET m
JOIN category c ON m.MAIN_CATEGORY = c.category_id
SET m.MAIN_CATEGORY_ID = c.category_id;

-- 4. 기존 컬럼 삭제
ALTER TABLE MARKET DROP COLUMN MAIN_CATEGORY;

-- 5. 외래키(Foreign Key) 제약조건 추가
ALTER TABLE MARKET 
ADD CONSTRAINT FK_MARKET_MAIN_CATEGORY 
FOREIGN KEY (MAIN_CATEGORY_ID) REFERENCES category (category_id);

-- 6. [선택] 세이프 업데이트 모드 원상 복구 (세션이 종료되므로 필수는 아니지만 관례상 작성)
SET SQL_SAFE_UPDATES = 1;