-- §15-5 로그인 이메일 변경 1개월 롤링 제한 판정용
ALTER TABLE seller
    ADD COLUMN email_changed_at DATETIME(6) NULL;
