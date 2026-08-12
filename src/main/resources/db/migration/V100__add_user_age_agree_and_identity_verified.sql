-- C0-1 회원가입: [필수] "만 14세 이상입니다" 동의 항목 추가
-- 기존 회원은 동의 이력이 없으므로 기본값(0) 유지 (동의 사실을 임의로 소급 기록하지 않음)
-- C0-2 본인인증(PASS) 완료 시각. PASS 연동 전까지는 가입 시 임시 인증 데이터로 채운다.
ALTER TABLE users
    ADD COLUMN age_agree BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN identity_verified_at DATETIME NULL;
