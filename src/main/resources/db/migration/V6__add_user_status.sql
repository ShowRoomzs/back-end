-- 1. status 컬럼 추가 (처음에는 NULL 허용으로 생성)
ALTER TABLE users ADD COLUMN status VARCHAR(20);

-- 2. 기존 유저의 status를 모두 'NORMAL'로 업데이트
UPDATE users SET status = 'NORMAL';

-- 3. status 컬럼에 NOT NULL 제약조건 추가 (모든 데이터가 채워진 후 실행)
-- MySQL / MariaDB 문법
ALTER TABLE users MODIFY COLUMN status VARCHAR(20) NOT NULL;
