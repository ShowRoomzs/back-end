/* 1. market_sns 테이블 생성 */
CREATE TABLE market_sns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    market_id BIGINT NOT NULL,
    sns_type VARCHAR(50) NOT NULL,
    sns_url VARCHAR(512) NOT NULL,
    
    /* 외래키 제약조건 */
    CONSTRAINT FK_MARKET_SNS_MARKET 
        FOREIGN KEY (market_id) REFERENCES market (market_id)
        ON DELETE CASCADE
);

/* 2. 기존 데이터 마이그레이션 (TYPE|URL 파싱) */
-- sns_link_1 데이터 이전
INSERT INTO market_sns (market_id, sns_type, sns_url)
SELECT 
    market_id, 
    SUBSTRING_INDEX(sns_link_1, '|', 1), -- 구분자 앞부분 (TYPE)
    SUBSTRING_INDEX(sns_link_1, '|', -1) -- 구분자 뒷부분 (URL)
FROM market 
WHERE sns_link_1 IS NOT NULL AND sns_link_1 != '';

-- sns_link_2 데이터 이전
INSERT INTO market_sns (market_id, sns_type, sns_url)
SELECT 
    market_id, 
    SUBSTRING_INDEX(sns_link_2, '|', 1), 
    SUBSTRING_INDEX(sns_link_2, '|', -1)
FROM market 
WHERE sns_link_2 IS NOT NULL AND sns_link_2 != '';

-- sns_link_3 데이터 이전
INSERT INTO market_sns (market_id, sns_type, sns_url)
SELECT 
    market_id, 
    SUBSTRING_INDEX(sns_link_3, '|', 1), 
    SUBSTRING_INDEX(sns_link_3, '|', -1)
FROM market 
WHERE sns_link_3 IS NOT NULL AND sns_link_3 != '';

/* 3. 기존 컬럼 삭제 */
ALTER TABLE market DROP COLUMN sns_link_1;
ALTER TABLE market DROP COLUMN sns_link_2;
ALTER TABLE market DROP COLUMN sns_link_3;