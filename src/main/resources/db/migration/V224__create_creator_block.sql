-- 소비자 → 쇼룸(크리에이터) 차단(App Store 심사 1.2 — 사용자 생성 콘텐츠 앱은 악용 사용자 차단 수단이 있어야 한다).
-- 1행 = 한 소비자가 한 쇼룸을 차단. 차단한 쇼룸은 그 소비자의 피드(전체 · 팔로잉 · 추천) · 쇼룸 목록 · 검색 · 자동완성에서 빠진다.
-- 상세(쇼룸 프로필 · 게시물 상세)는 열린다 — 차단 해제 버튼이 거기 있다. 차단하면 팔로우도 끊는다(서비스).
-- creator_follow(V70)와 같은 구성이다. 223 은 지운 데모 시드(38092f50)라 건너뛴다.
CREATE TABLE `creator_block` (
    `block_id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL,
    `creator_id` BIGINT      NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`block_id`),
    UNIQUE KEY `uk_creator_block` (`user_id`, `creator_id`),
    KEY `idx_creator_block_creator` (`creator_id`),
    CONSTRAINT `fk_creator_block_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
    CONSTRAINT `fk_creator_block_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
