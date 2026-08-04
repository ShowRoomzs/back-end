-- 반려 시 팔로워 수·업무용 이메일 파기 가능하도록 nullable 변경
-- 본인인증 정보(이름·생년월일·연락처)는 creator_application에 저장 (반려 시 이름·생년월일 파기, 연락처 해시)
ALTER TABLE creator_application
    MODIFY COLUMN follower_count INT NULL COMMENT '팔로워 수 (반려 시 파기)',
    MODIFY COLUMN business_email VARCHAR(512) NULL COMMENT '업무용 이메일 (반려 시 파기)',
    ADD COLUMN real_name VARCHAR(64) NULL COMMENT '실명 (반려 시 파기)' AFTER business_email,
    ADD COLUMN birthday VARCHAR(10) NULL COMMENT '생년월일 YYYY-MM-DD (반려 시 파기)' AFTER real_name,
    ADD COLUMN phone_number VARCHAR(128) NULL COMMENT '신청 연락처 (반려 시 일방향 해시)' AFTER birthday;

-- 승인 시 Creator로 복사할 본인인증 정보
ALTER TABLE creator
    ADD COLUMN real_name VARCHAR(64) NULL COMMENT '실명' AFTER business_email,
    ADD COLUMN birthday VARCHAR(10) NULL COMMENT '생년월일 YYYY-MM-DD' AFTER real_name,
    ADD COLUMN phone_number VARCHAR(20) NULL COMMENT '연락처' AFTER birthday;
