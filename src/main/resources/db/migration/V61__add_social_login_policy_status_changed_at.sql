ALTER TABLE social_login_policy
    ADD COLUMN status_changed_at DATETIME(6) NULL COMMENT '소셜 로그인 활성/비활성 마지막 변경 시각';
