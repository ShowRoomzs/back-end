-- 1. market_follow 테이블 생성
CREATE TABLE market_follow (
    follow_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    market_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (follow_id)
) ENGINE=InnoDB;

-- 2. 유니크 제약조건 (중복 팔로우 방지)
ALTER TABLE market_follow 
   ADD CONSTRAINT UK_MARKET_FOLLOW UNIQUE (user_id, market_id);

-- 3. 외래키 제약조건 (마켓 테이블 연결)
ALTER TABLE market_follow 
   ADD CONSTRAINT FK_MARKET_FOLLOW_MARKET 
   FOREIGN KEY (market_id) 
   REFERENCES market (market_id);

-- 4. 외래키 제약조건 (유저 테이블 연결)
ALTER TABLE market_follow 
   ADD CONSTRAINT FK_MARKET_FOLLOW_USER 
   FOREIGN KEY (user_id) 
   REFERENCES users (user_id);