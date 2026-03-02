-- review 테이블 컬럼 추가
ALTER TABLE `review`
    ADD COLUMN `is_personal_info_agreed` BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN `like_count` INT NOT NULL DEFAULT 0;

-- review_like 테이블 신규 생성
CREATE TABLE `review_like` (
    `review_like_id` BIGINT NOT NULL AUTO_INCREMENT,
    `review_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`review_like_id`),
    UNIQUE KEY `uk_review_like_review_user` (`review_id`, `user_id`),
    CONSTRAINT `fk_review_like_review` FOREIGN KEY (`review_id`) REFERENCES `review` (`review_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_like_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB;
