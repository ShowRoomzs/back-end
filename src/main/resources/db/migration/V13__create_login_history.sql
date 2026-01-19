CREATE TABLE login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_ip VARCHAR(45),
    user_agent VARCHAR(512),
    country VARCHAR(100),
    city VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    login_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES USERS(USER_ID) ON DELETE CASCADE
);

CREATE INDEX idx_login_history_user_id ON login_history(user_id);
CREATE INDEX idx_login_history_login_at ON login_history(login_at);
