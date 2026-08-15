-- §22-1 쇼룸 관리 — 소비자에게 공개되는 쇼룸 프로필 필드.
-- 공개(#8 쇼룸 관리)와 비공개(#9 기본정보 관리)를 가르는 유일한 기준은 "소비자가 보느냐"다.
--
-- profile_image_url — 소비자 앱 계정(users.profile_image_url)과 공유하지 않는다.
--   쇼룸은 판매 채널의 간판이고 앱 계정은 개인 소비 계정이라, 같은 값을 강제하면 어느 한쪽이
--   반드시 부적절해진다. 동일 인물이 두 이름·두 이미지를 갖는 것은 의도된 결과다(구버전 "앱 계정 공유" 규칙 폐기).
-- showroom_address — 가입 시 쇼룸명 기준으로 자동 생성되며, 이후 쇼룸명을 바꿔도 따라 바뀌지 않는다.
--   링크가 바뀌면 인스타그램 프로필·스토리에 이미 뿌려둔 링크가 전부 죽기 때문이다.
-- instagram_url — 소비자 노출용 공개 필드. 기본정보 관리(#9)의 활동 채널(계약 이행 확인용 비공개)과
--   값이 같더라도 같은 필드가 아니다. 가입 온보딩의 채널 주소를 기본값으로 가져오되 독립 수정한다.
ALTER TABLE `creator`
    ADD COLUMN `profile_image_url` VARCHAR(1024) NULL COMMENT '쇼룸 프로필 이미지 — 앱 계정과 별개(§22-1)' AFTER `showroom_name`,
    ADD COLUMN `showroom_address` VARCHAR(64) NULL COMMENT '쇼룸 주소 핸들 — 자동 생성·수정 불가(§22-1)' AFTER `profile_image_url`,
    ADD COLUMN `introduction` VARCHAR(50) NULL COMMENT '쇼룸 소개글 — 최대 50자' AFTER `showroom_address`,
    ADD COLUMN `instagram_url` VARCHAR(512) NULL COMMENT '인스타그램 URL — 소비자 노출(§22-1)' AFTER `introduction`;

-- 인스타그램 채널로 가입한 기존 크리에이터는 온보딩 채널 주소를 기본값으로 가져온다.
-- 다른 SNS(틱톡·유튜브·X)로 가입한 쪽은 인스타그램 URL이 아니므로 비워 둔다.
UPDATE `creator`
   SET `instagram_url` = `channel_url`
 WHERE `instagram_url` IS NULL
   AND `sns_type` = 'INSTAGRAM'
   AND `channel_url` IS NOT NULL;

-- 기존 크리에이터의 쇼룸 주소 백필.
-- 쇼룸명은 한글이 대부분이라 SQL만으로는 핸들로 옮길 수 없다. creator_id 자체가 유니크하므로
-- 충돌 없이 결정적으로 채운다(신규 가입은 애플리케이션의 ShowroomAddressGenerator가 쇼룸명에서 만든다).
UPDATE `creator`
   SET `showroom_address` = CONCAT('sr', `creator_id`)
 WHERE `showroom_address` IS NULL;

-- NULL은 서로 다른 값으로 취급되므로 미발급 행은 몇 건이든 공존하고, 발급된 주소만 유니크로 묶인다.
CREATE UNIQUE INDEX `uk_creator_showroom_address` ON `creator` (`showroom_address`);
