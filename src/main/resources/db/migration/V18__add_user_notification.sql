ALTER TABLE users
ADD COLUMN sms_agree BIT(1) NOT NULL DEFAULT 0,          -- 문자 알림 (기본값: OFF)
ADD COLUMN night_push_agree BIT(1) NOT NULL DEFAULT 0,   -- 야간 알림 (기본값: OFF)
ADD COLUMN showroom_push_agree BIT(1) NOT NULL DEFAULT 1, -- 쇼룸 앱 알림 (기본값: ON)
ADD COLUMN market_push_agree BIT(1) NOT NULL DEFAULT 1;   -- 마켓 알림 (기본값: ON)