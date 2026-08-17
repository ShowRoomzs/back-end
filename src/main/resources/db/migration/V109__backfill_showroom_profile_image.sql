-- §22-1 앱 계정 분리 — 분리 시점의 겉모습은 그대로 두기 위한 일회성 복사.
--
-- 그동안 소비자 화면(게시물·팔로잉 목록)은 쇼룸 아바타로 앱 계정 프로필 이미지를 그려 왔다.
-- 이제 쇼룸 프로필 이미지가 별도 컬럼으로 갈라졌으므로, 값을 옮겨 놓지 않으면 기존 인플루언서의
-- 아바타가 한꺼번에 비어 버린다. 여기서 한 번 복사한 뒤로 두 값은 서로를 따라가지 않는다.
UPDATE `creator` c
  JOIN `users` u ON u.`user_id` = c.`user_id`
   SET c.`profile_image_url` = u.`profile_image_url`
 WHERE c.`profile_image_url` IS NULL
   AND u.`profile_image_url` IS NOT NULL;
