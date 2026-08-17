-- C15 설정 — 알림 설정 축소 · 동의 이력 · 탈퇴 사유 개편
--
-- 1) 알림 설정은 화면에 남은 두 개(팔로우 쇼룸 새 게시물 · 광고성 정보 수신)만 유지한다.
--    주문·배송·문의 답변 등 거래 알림은 끌 수 없으므로 아예 설정 항목을 두지 않는다.
--    광고성 정보 수신은 가입 시 [선택] 동의(users.marketing_agree)와 같은 값을 쓴다 —
--    설정 화면에 별도 컬럼을 만들면 두 값이 갈라져 어느 쪽이 실제 동의인지 알 수 없게 된다.
-- 2) 동의·철회 일시는 user_consent_history에 남긴다(광고성 정보 수신, 본인확인 재인증).
-- 3) 탈퇴 사유는 C15-3의 6개 항목으로 바꾸고, "선택하지 않아도 탈퇴할 수 있어요"에 맞춰 NULL을 허용한다.

-- 1) 알림 설정 --------------------------------------------------------------
ALTER TABLE users
    DROP COLUMN sms_agree,
    DROP COLUMN night_push_agree,
    DROP COLUMN market_push_agree,
    CHANGE COLUMN showroom_push_agree follow_post_push_agree BIT(1) NOT NULL DEFAULT 1
        COMMENT '팔로우한 쇼룸의 새 공구·게시물 알림 (기본값: ON)',
    ADD COLUMN marketing_agree_changed_at DATETIME(6) NULL
        COMMENT '광고성 정보 수신 동의/철회를 마지막으로 바꾼 시각 — 철회 통지 근거';

-- 2) 동의 이력 --------------------------------------------------------------
CREATE TABLE `user_consent_history` (
    `user_consent_history_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`                 BIGINT      NOT NULL,
    `consent_type`            VARCHAR(40) NOT NULL COMMENT 'MARKETING, IDENTITY_VERIFICATION',
    `agreed`                  BIT(1)      NOT NULL COMMENT '1=동의, 0=철회',
    `created_at`              DATETIME(6) NULL,
    `modified_at`             DATETIME(6) NULL,
    PRIMARY KEY (`user_consent_history_id`),
    KEY `idx_user_consent_history_user` (`user_id`, `consent_type`, `created_at`),
    CONSTRAINT `fk_user_consent_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 회원의 마케팅 동의 시점은 알 수 없으므로 이력을 소급 생성하지 않는다.
-- (동의 일시를 임의로 만들어 넣으면 철회 통지 근거로 쓸 수 없다)

-- 3) 탈퇴 사유 --------------------------------------------------------------
-- 새 값을 먼저 넣을 수 있게 ENUM을 확장한 뒤 기존 값을 옮기고, 옛 값을 제거한다.
ALTER TABLE withdrawal_history
    MODIFY COLUMN reason ENUM(
        'DIFFICULT_SEARCH','ETC','INCONVENIENT_USE',
        'NO_GROUP_BUY','TOO_MANY_NOTIFICATIONS','INCONVENIENT_APP','PRIVACY_CONCERN','REJOIN_OTHER_ACCOUNT'
    ) NULL;

-- INCONVENIENT_USE("앱 사용이 불편해요") -> INCONVENIENT_APP("앱이 사용하기 불편해요") : 같은 뜻
-- DIFFICULT_SEARCH("상품 탐색이 어려워요") -> NO_GROUP_BUY("원하는 공구가 없어요") : 새 항목 중 가장 가까운 값
UPDATE withdrawal_history SET reason = 'INCONVENIENT_APP' WHERE reason = 'INCONVENIENT_USE';
UPDATE withdrawal_history SET reason = 'NO_GROUP_BUY'     WHERE reason = 'DIFFICULT_SEARCH';

ALTER TABLE withdrawal_history
    MODIFY COLUMN reason ENUM(
        'NO_GROUP_BUY','TOO_MANY_NOTIFICATIONS','INCONVENIENT_APP','PRIVACY_CONCERN','REJOIN_OTHER_ACCOUNT','ETC'
    ) NULL COMMENT 'C15-3 탈퇴 이유 — 선택하지 않고도 탈퇴할 수 있으므로 NULL 허용';
