CREATE TABLE social_login_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_type VARCHAR(50) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL
);

CREATE INDEX idx_social_login_policy_provider_type ON social_login_policy(provider_type);
