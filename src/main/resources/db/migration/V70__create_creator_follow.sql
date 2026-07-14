-- 1. creator_follow 테이블 생성
CREATE TABLE creator_follow (
    follow_id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    creator_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (follow_id)
) ENGINE=InnoDB;

-- 2. 유니크 제약조건 (중복 팔로우 방지)
ALTER TABLE creator_follow
   ADD CONSTRAINT UK_CREATOR_FOLLOW UNIQUE (user_id, creator_id);

-- 3. 외래키 제약조건 (크리에이터 테이블 연결)
ALTER TABLE creator_follow
   ADD CONSTRAINT FK_CREATOR_FOLLOW_CREATOR
   FOREIGN KEY (creator_id)
   REFERENCES creator (creator_id);

-- 4. 외래키 제약조건 (유저 테이블 연결)
ALTER TABLE creator_follow
   ADD CONSTRAINT FK_CREATOR_FOLLOW_USER
   FOREIGN KEY (user_id)
   REFERENCES users (user_id);

