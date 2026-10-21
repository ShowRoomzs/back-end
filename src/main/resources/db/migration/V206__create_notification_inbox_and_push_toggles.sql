-- 1017 설계서 1 · 2 — 알림함(소비자 · 파트너센터 · 쇼룸 스튜디오 공통) · 지연 푸시 · C15 광고성 토글 4개.

-- 1-1. 알림함 한 줄 — 수신자 3종이 같은 테이블을 쓴다(recipient_id 는 세 테이블을 가리켜 FK 를 걸지 않는다 · 보관 배치가 정리).
-- 문구는 발생 시점에 굳힌다. 이동 대상은 종류 · id 만(지금 상태의 상세로 간다).
CREATE TABLE `notification` (
    `notification_id` BIGINT       NOT NULL AUTO_INCREMENT,
    `recipient_type`  VARCHAR(16)  NOT NULL COMMENT 'USER · SELLER · CREATOR',
    `recipient_id`    BIGINT       NOT NULL COMMENT 'users.USER_ID · market.market_id · creator.creator_id',
    `type`            VARCHAR(60)  NOT NULL COMMENT 'NotificationType',
    `category`        VARCHAR(24)  NOT NULL COMMENT '화면 분류 태그 — 계약 · 공구 · 연결 · 정산 · 주문 …',
    `actor_type`      VARCHAR(16)  NOT NULL COMMENT 'OPERATOR · BRAND · CREATOR · SYSTEM · PLATFORM · SHOWROOM',
    `actor_name`      VARCHAR(100) NULL     COMMENT '상대 이름 스냅샷',
    `title`           VARCHAR(200) NOT NULL,
    `body`            VARCHAR(500) NULL,
    `emphasis`        VARCHAR(200) NULL     COMMENT 'C11 굵게 — 대상(상품명 · 쇼룸명 · 금액)',
    `target_type`     VARCHAR(40)  NULL,
    `target_id`       BIGINT       NULL,
    `target_sub`      VARCHAR(40)  NULL     COMMENT '탭 · 문의 종류 · 카드 message id',
    `image_kind`      VARCHAR(16)  NULL     COMMENT 'THUMB · AVATAR · ICON',
    `image_ref`       VARCHAR(500) NULL     COMMENT '이미지 URL 스냅샷 · AVATAR 는 creator_id',
    `advertising`     BIT(1)       NOT NULL DEFAULT 0,
    `todo_due_at`     DATETIME(6)  NULL     COMMENT 'C11 할 일 알림 기한',
    `dedup_key`       VARCHAR(120) NULL     COMMENT '같은 사건 중복 방지 · 하루 묶음 키',
    `digest_count`    INT          NOT NULL DEFAULT 1 COMMENT '하루 묶음 건수(「새 게시물 3개」)',
    `read_at`         DATETIME(6)  NULL,
    `created_at`      DATETIME(6)  NOT NULL,
    PRIMARY KEY (`notification_id`),
    UNIQUE KEY `uk_notification_dedup` (`dedup_key`),
    KEY `idx_notification_recipient` (`recipient_type`, `recipient_id`, `created_at`),
    KEY `idx_notification_unread` (`recipient_type`, `recipient_id`, `read_at`),
    KEY `idx_notification_retention` (`recipient_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-6. 지연 푸시 — 광고성만 탄다(야간이면 다음 08:00 · 하루 묶음 시각). 거래 알림은 커밋 직후 바로 보낸다.
CREATE TABLE `notification_push_outbox` (
    `outbox_id`       BIGINT        NOT NULL AUTO_INCREMENT,
    `notification_id` BIGINT        NOT NULL,
    `user_id`         BIGINT        NOT NULL,
    `send_after`      DATETIME(6)   NOT NULL,
    `title`           VARCHAR(200)  NOT NULL,
    `body`            VARCHAR(500)  NULL,
    `image_url`       VARCHAR(500)  NULL,
    `data_json`       VARCHAR(1000) NOT NULL COMMENT 'PushMessage.data — 앱 딥링크',
    `sent_at`         DATETIME(6)   NULL,
    `attempt`         INT           NOT NULL DEFAULT 0,
    `last_error`      VARCHAR(500)  NULL,
    PRIMARY KEY (`outbox_id`),
    UNIQUE KEY `uk_outbox_notification` (`notification_id`),
    KEY `idx_outbox_due` (`sent_at`, `send_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-0 ⑤ 소비자 종 배지 — C11 목록에 마지막으로 들어간 시각 이후 생성된 알림 수.
-- 2-1 C15 광고성 토글 — 새 공구 · 마감 임박 추가 · 켤 때의 동의 일시(「YYYY.MM.DD 수신 동의」). 혜택 · 이벤트는 marketing_agree 그대로.
ALTER TABLE `users`
    ADD COLUMN `notification_badge_seen_at`      DATETIME(6) NULL COMMENT 'C11 목록 진입 시각 — 이후 알림 수가 종 배지',
    ADD COLUMN `follow_post_push_agreed_at`      DATETIME(6) NULL COMMENT '팔로우 쇼룸 새 게시물 — 켤 때의 수신 동의 일시',
    ADD COLUMN `follow_group_buy_push_agree`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '팔로우 쇼룸 새 공구(광고성)',
    ADD COLUMN `follow_group_buy_push_agreed_at` DATETIME(6) NULL,
    ADD COLUMN `group_buy_ending_push_agree`     BIT(1)      NOT NULL DEFAULT 0 COMMENT '공구 마감 임박(광고성)',
    ADD COLUMN `group_buy_ending_push_agreed_at` DATETIME(6) NULL;

-- 2-2 백필 — 네 토글은 모두 광고성이다. 지금까지 동의 없이 켜져 있던 「팔로우 쇼룸 새 게시물」을 광고성 동의 값으로 맞추고,
-- 새 토글 둘도 같은 값으로 시작한다. 동의 일시는 광고성 동의를 바꾼 시각(없으면 가입 시각)이다.
UPDATE `users`
SET `follow_post_push_agree`          = COALESCE(`marketing_agree`, 0),
    `follow_group_buy_push_agree`     = COALESCE(`marketing_agree`, 0),
    `group_buy_ending_push_agree`     = COALESCE(`marketing_agree`, 0),
    `follow_post_push_agreed_at`      = CASE WHEN `marketing_agree` = 1 THEN COALESCE(`marketing_agree_changed_at`, `created_at`) END,
    `follow_group_buy_push_agreed_at` = CASE WHEN `marketing_agree` = 1 THEN COALESCE(`marketing_agree_changed_at`, `created_at`) END,
    `group_buy_ending_push_agreed_at` = CASE WHEN `marketing_agree` = 1 THEN COALESCE(`marketing_agree_changed_at`, `created_at`) END;
