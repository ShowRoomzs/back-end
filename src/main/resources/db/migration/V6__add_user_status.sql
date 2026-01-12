-- 1. status 컬럼 추가
ALTER TABLE users ADD COLUMN status VARCHAR(20);

-- 2. 기존 유저의 status 업데이트 (WHERE 절 추가로 Safe Update 회피)
-- id > 0 과 같이 항상 참인 조건을 넣어주는 것이 안전합니다.
UPDATE users SET status = 'NORMAL' WHERE user_id > 0;

-- 3. status 컬럼에 NOT NULL 제약조건 추가
ALTER TABLE users MODIFY COLUMN status VARCHAR(20) NOT NULL;

