-- §13-6: 연결코드는 쇼룸(크리에이터)별 고정(영구) 발급, 인플루언서가 원하면 재발급 가능.
ALTER TABLE `creator`
    ADD COLUMN `connection_code` VARCHAR(16) NULL COMMENT '연결코드 — 대문자+숫자, 혼동 문자(0/O/1/I) 제외' AFTER `showroom_name`,
    ADD COLUMN `connection_code_issued_at` DATETIME(6) NULL COMMENT '연결코드 (재)발급 시각' AFTER `connection_code`;

ALTER TABLE `creator`
    ADD UNIQUE KEY `uk_creator_connection_code` (`connection_code`);

-- 기존 크리에이터 백필 — creator_id 자체가 유니크하므로 충돌 없이 결정적으로 채운다.
-- (신규 발급/재발급은 애플리케이션에서 혼동 문자를 제외한 랜덤 코드로 생성한다.)
UPDATE `creator`
   SET `connection_code` = CONCAT('SZ', LPAD(`creator_id`, 8, '0')),
       `connection_code_issued_at` = NOW(6)
 WHERE `connection_code` IS NULL;
