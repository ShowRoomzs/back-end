-- 어드민 목록 미리보기의 「운영팀: 」 접두 판정(36 설계 1-2). 미리보기 문자열에는 접두를 박지 않는다 — 같은 스레드를 상대도 읽는다.
ALTER TABLE `message_thread`
    ADD COLUMN `last_message_sender_type` VARCHAR(20) NULL COMMENT 'SELLER, CREATOR, ADMIN' AFTER `last_message_preview`;

UPDATE `message_thread` t
    JOIN (SELECT `thread_id`, MAX(`message_id`) AS `last_id` FROM `message` GROUP BY `thread_id`) l ON l.`thread_id` = t.`thread_id`
    JOIN `message` m ON m.`message_id` = l.`last_id`
SET t.`last_message_sender_type` = m.`sender_type`;
