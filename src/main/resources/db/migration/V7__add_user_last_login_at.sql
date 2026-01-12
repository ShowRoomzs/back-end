-- 4. last_login_at 컬럼 추가 (테이블명 users 소문자 통일)
ALTER TABLE users ADD COLUMN last_login_at DATETIME;

-- 5. 기존 데이터 업데이트 (테이블명 users 소문자 통일, 컬럼명도 소문자 권장)
UPDATE users SET last_login_at = created_at WHERE last_login_at IS NULL;