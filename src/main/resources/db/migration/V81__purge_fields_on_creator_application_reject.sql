-- 반려 시 팔로워 수·업무용 이메일 파기 가능하도록 nullable 변경
ALTER TABLE CREATOR_APPLICATION
    MODIFY COLUMN FOLLOWER_COUNT INT NULL COMMENT '팔로워 수 (반려 시 파기)',
    MODIFY COLUMN BUSINESS_EMAIL VARCHAR(512) NULL COMMENT '업무용 이메일 (반려 시 파기)';

-- 연락처 일방향 해시(SHA-256 hex 64자) 저장을 위해 길이 확장
ALTER TABLE USERS
    MODIFY COLUMN PHONE_NUMBER VARCHAR(128) NULL COMMENT '연락처 (크리에이터 신청 반려 시 일방향 해시)';
