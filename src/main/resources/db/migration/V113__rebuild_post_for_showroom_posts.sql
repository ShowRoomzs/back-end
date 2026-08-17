-- §24 쇼룸 포스트(일반 게시물) — post 계열 전면 재구축.
--
-- 기존 post 데이터는 전량 폐기한다. ALTER·백필로 끌고 가지 않고 DROP 후 최종형으로 재생성하므로
-- 이 마이그레이션은 되돌릴 수 없다(Flyway는 롤백하지 않는다). 운영 적용 시각을 FE 교체와 맞춘다.
--
-- 선례 — V69__post_market_to_creator.sql이 이미 `DELETE FROM post` 전량 삭제 후 스키마를 바꿨다.
-- 이번은 그 연장선이고, 다른 점은 삭제가 아니라 재생성이라 최종형이 한 번에 나온다는 것이다.
--
-- 설계 축 — 공통 뿌리 post 1개 + 타입별 확장 테이블. 공구 게시물이 들어올 때
-- group_buy_post(1:1 확장)만 새로 만들면 되도록 post_type 판별자를 지금부터 둔다.
-- 좋아요·제재·이의신청·노출·알림은 두 타입에 똑같이 걸리는 횡단 테이블이라 post_id 단일 FK로 붙는다.

-- ---------------------------------------------------------------------------
-- 0. 폐기 — 자식부터(FK 역순). 부모 테이블(product·users·creator)은 건드리지 않는다.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS `post_wishlist`;
DROP TABLE IF EXISTS `post_product`;
DROP TABLE IF EXISTS `post_images`;
DROP TABLE IF EXISTS `post`;

-- ---------------------------------------------------------------------------
-- 1. post — 공통 뿌리
--
-- title이 없다. 일반 게시물은 제목이 없고(§24-3), 공구 게시물의 제목은 확장 테이블에서
-- NOT NULL로 만든다. 뿌리에 nullable로 두면 "일반인데 제목이 들어온" 데이터를 DB가 막지 못한다.
--
-- status는 노출 축만 담당한다. 공구의 D-3·품절·마감은 상품·공구 데이터에서 파생되는 값이지
-- 게시물의 상태가 아니다 — 섞으면 공구 도입 시 상태 enum이 폭발한다.
-- ---------------------------------------------------------------------------
CREATE TABLE `post` (
  `post_id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `creator_id`       BIGINT       NOT NULL,
  `post_type`        VARCHAR(20)  NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL / GROUP_BUY',
  `status`           VARCHAR(20)  NOT NULL COMMENT 'DRAFT/PUBLISHED/SUSPENDED/UNDER_REVIEW/DELETED',
  `content`          TEXT         NULL COMMENT '본문 — 선택 · 2,000자(서버 검증)',
  `aspect_ratio`     DECIMAL(6,4) NULL COMMENT '첫 사진 기준 가로/세로. 1.9100(1.91:1) ~ 0.8000(4:5)',
  `impression_count` BIGINT       NOT NULL DEFAULT 0 COMMENT '노출 — 기획 용어. 구 view_count',
  `like_count`       BIGINT       NOT NULL DEFAULT 0 COMMENT '좋아요 — 구 wishlist_count',
  `published_at`     DATETIME(6)  NULL COMMENT '작성중→게시중 전환 시각. 재게시해도 갱신하지 않는다',
  `deleted_at`       DATETIME(6)  NULL,
  `delete_reason`    VARCHAR(32)  NULL COMMENT 'SELF / APPEAL_REJECTED / APPEAL_EXPIRED / ADMIN',
  `purge_at`         DATETIME(6)  NULL COMMENT '비공개 보관 만료 — 이후 파기 배치가 물리 삭제',
  `created_at`       DATETIME(6)  NOT NULL,
  `modified_at`      DATETIME(6)  NULL,
  PRIMARY KEY (`post_id`),
  KEY `idx_post_creator_status_published` (`creator_id`, `status`, `published_at`),
  KEY `idx_post_purge` (`purge_at`),
  CONSTRAINT `fk_post_creator` FOREIGN KEY (`creator_id`) REFERENCES `creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 2. post_image — 사진 최대 20장 · 순서 · 대표 = 첫 장(§24-2)
--
-- original_url을 따로 두는 이유 — §24-6이 "반려 통지 후 유예 기간 동안 본인만 사진 원본을
-- 내려받을 수 있게" 하라고 요구한다. 크롭본만 갖고 있으면 이 요구를 만족할 수 없다.
--
-- sort_order UNIQUE의 함정 — 재배열 시 (1,2,3) → (2,1,3) 중간 상태에서 충돌이 난다.
-- 서비스는 전체 DELETE 후 재INSERT로 처리한다(장수 20 이하라 비용 무시 가능).
-- 최대 20장은 DB가 아니라 서비스에서 막는다 — 초과 시 필요한 응답이 도메인 메시지다.
-- ---------------------------------------------------------------------------
CREATE TABLE `post_image` (
  `post_image_id` BIGINT       NOT NULL AUTO_INCREMENT,
  `post_id`       BIGINT       NOT NULL,
  `sort_order`    INT          NOT NULL COMMENT '0 = 대표 사진. 셀 드래그 순서 변경의 결과',
  `image_url`     VARCHAR(512) NOT NULL COMMENT '표시용(크롭 반영)',
  `original_url`  VARCHAR(512) NOT NULL COMMENT '원본 — 유예 기간 내려받기·재크롭용. 파기 시점까지 보존',
  `width`         INT          NULL,
  `height`        INT          NULL,
  `file_size`     INT          NULL,
  `created_at`    DATETIME(6)  NOT NULL,
  PRIMARY KEY (`post_image_id`),
  UNIQUE KEY `uk_post_image_order` (`post_id`, `sort_order`),
  CONSTRAINT `fk_post_image_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. post_like (구 post_wishlist)
--
-- 구 스키마 대비 두 곳이 다르다 — created_at이 NOT NULL(기존은 nullable이라 기간 집계가 불가능했다),
-- (post_id, created_at) 인덱스 추가(인사이트 ① 좋아요 지표용).
-- ---------------------------------------------------------------------------
CREATE TABLE `post_like` (
  `post_like_id` BIGINT      NOT NULL AUTO_INCREMENT,
  `post_id`      BIGINT      NOT NULL,
  `user_id`      BIGINT      NOT NULL,
  `created_at`   DATETIME(6) NOT NULL COMMENT '인사이트 기간 필터의 기준 — 반드시 NOT NULL',
  PRIMARY KEY (`post_like_id`),
  UNIQUE KEY `uk_post_like` (`user_id`, `post_id`),
  KEY `idx_post_like_post_time` (`post_id`, `created_at`),
  CONSTRAINT `fk_post_like_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
  CONSTRAINT `fk_post_like_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. post_suspension — 운영자 조치(§24-5)
--
-- post에 컬럼으로 붙이지 않고 테이블로 뺀 이유 — 재게시 후 재조치가 가능하다.
-- 게시물당 1행으로 두면 첫 조치 이력이 덮인다. resolution IS NULL인 행이 진행 중인 조치다.
-- ---------------------------------------------------------------------------
CREATE TABLE `post_suspension` (
  `suspension_id`   BIGINT       NOT NULL AUTO_INCREMENT,
  `post_id`         BIGINT       NOT NULL,
  `reason_code`     VARCHAR(40)  NOT NULL COMMENT 'MEDICAL_CLAIM / AD_DISCLOSURE / COPYRIGHT / ...',
  `reason_detail`   VARCHAR(500) NULL,
  `policy_ref`      VARCHAR(200) NULL COMMENT '근거 규정 조항 — 운영정책 문서 조번호',
  `suspended_by`    BIGINT       NOT NULL COMMENT '처리자(운영자) — 시점 고정',
  `suspended_at`    DATETIME(6)  NOT NULL,
  `appeal_deadline` DATETIME(6)  NOT NULL COMMENT '이의 신청 기한 (시안 7일 · 법률 자문 대기)',
  `resolution`      VARCHAR(20)  NULL COMMENT 'REPUBLISHED / DELETED_BY_REJECT / DELETED_BY_EXPIRE / DELETED_BY_SELF',
  `resolved_at`     DATETIME(6)  NULL,
  PRIMARY KEY (`suspension_id`),
  KEY `idx_post_suspension_post` (`post_id`, `suspended_at`),
  KEY `idx_post_suspension_deadline` (`appeal_deadline`, `resolution`),
  CONSTRAINT `fk_post_suspension_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. post_appeal — 이의 신청(§24-5)
--
-- uk_post_appeal_post가 "게시물당 1회"를 DB로 강제한다. 서비스 검증만 두면 동시 요청에서 뚫린다.
-- 재조치 후 재신청을 허용하기로 확정되면 이 유니크를 suspension_id 기준으로 옮긴다.
-- ---------------------------------------------------------------------------
CREATE TABLE `post_appeal` (
  `appeal_id`      BIGINT        NOT NULL AUTO_INCREMENT,
  `suspension_id`  BIGINT        NOT NULL,
  `post_id`        BIGINT        NOT NULL,
  `content`        VARCHAR(1000) NOT NULL,
  `submitted_at`   DATETIME(6)   NOT NULL,
  `status`         VARCHAR(20)   NOT NULL COMMENT 'PENDING / APPROVED / REJECTED',
  `reviewed_by`    BIGINT        NULL,
  `reviewed_at`    DATETIME(6)   NULL,
  `review_comment` VARCHAR(500)  NULL,
  `grace_until`    DATETIME(6)   NULL COMMENT '반려 시 — 원본 내려받기 유예 만료',
  PRIMARY KEY (`appeal_id`),
  UNIQUE KEY `uk_post_appeal_post` (`post_id`),
  CONSTRAINT `fk_post_appeal_suspension` FOREIGN KEY (`suspension_id`) REFERENCES `post_suspension` (`suspension_id`),
  CONSTRAINT `fk_post_appeal_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. post_impression — 노출 원천 로그(§24-7)
--
-- 카운터(post.impression_count)만 있으면 기간 필터·연령 분포·귀속을 영원히 못 만든다.
-- viewer_key 규칙을 showroom_visit과 같게 맞추는 것이 핵심이다 — 라스트 터치 귀속이
-- 이 키의 일치로만 성립한다. 중복 노출은 showroom_visit과 같은 30분 세션 규칙으로 적재 시점에 거른다.
-- ---------------------------------------------------------------------------
CREATE TABLE `post_impression` (
  `impression_id` BIGINT      NOT NULL AUTO_INCREMENT,
  `post_id`       BIGINT      NOT NULL,
  `creator_id`    BIGINT      NOT NULL COMMENT '집계 조인 제거용 비정규화',
  `user_id`       BIGINT      NULL COMMENT '로그인 노출만 — 연령/성별 집계의 표본',
  `viewer_key`    VARCHAR(64) NOT NULL COMMENT 'showroom_visit.visitor_key와 같은 규칙: u:{user_id} 또는 디바이스 식별자',
  `viewed_at`     DATETIME(6) NOT NULL,
  PRIMARY KEY (`impression_id`),
  KEY `idx_post_impression_post_time` (`post_id`, `viewed_at`),
  KEY `idx_post_impression_attribution` (`viewer_key`, `viewed_at`),
  CONSTRAINT `fk_post_impression_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. post_notification_log — 알림 이력(§24-6). 발송 인프라는 이번 범위 밖이라 어댑터는 no-op다.
--
-- post_id에 FK를 걸지 않는 유일한 테이블이다. §24-6이 "삭제 이력은 알림 이력에 영구 보존"을
-- 요구하는데 게시물은 N개월 후 파기된다 — FK가 있으면 파기 배치가 이력을 같이 지우거나 막힌다.
-- ---------------------------------------------------------------------------
CREATE TABLE `post_notification_log` (
  `log_id`     BIGINT      NOT NULL AUTO_INCREMENT,
  `post_id`    BIGINT      NOT NULL COMMENT '게시물이 파기돼도 남는다 — FK를 걸지 않는다',
  `creator_id` BIGINT      NOT NULL,
  `event_type` VARCHAR(40) NOT NULL COMMENT 'SUSPENDED / APPEAL_RECEIVED / APPEAL_APPROVED / APPEAL_REJECTED / DELETED_BY_EXPIRE / PUBLISHED_TO_FOLLOWERS',
  `payload`    TEXT        NULL COMMENT '사유·근거 규정·기한 등 통지 당시 문구를 그대로 굳힌다(JSON 문자열)',
  `sent_at`    DATETIME(6) NOT NULL,
  `delivered`  BIT(1)      NOT NULL DEFAULT b'0' COMMENT '발송 인프라 도입 전에는 항상 false',
  PRIMARY KEY (`log_id`),
  KEY `idx_post_notification_creator` (`creator_id`, `sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. 귀속 — 기존 테이블에 컬럼 추가(§24-7 라스트 터치)
--
-- 이 두 테이블은 실데이터가 있으므로 DROP 대상이 아니다. 컬럼만 추가하고 기존 행은 NULL(귀속 불명)로 남긴다.
-- FK를 걸지 않는다 — post가 파기되면 방문·팔로우 행까지 막히거나 지워진다.
-- 귀속은 참조 무결성이 필요한 관계가 아니라 통계 태그다.
--
-- 알려진 구멍 — 언팔로우하면 creator_follow 행이 삭제되므로 과거에 귀속된 팔로우 수가 줄어든다.
-- 정확히 하려면 creator_follow_event 로그가 필요하지만, 팔로우 로그는 쇼룸 관리(§22-4)와 공유 자산이라
-- 그쪽 작업과 함께 설계한다. 그때까지 인사이트의 팔로우 수치는 감소할 수 있다.
-- ---------------------------------------------------------------------------
ALTER TABLE `showroom_visit`
  ADD COLUMN `attributed_post_id` BIGINT NULL COMMENT '§24-7 라스트 터치 — 방문 24h 이내 마지막으로 본 게시물',
  ADD KEY `idx_showroom_visit_attributed` (`attributed_post_id`, `visited_at`);

ALTER TABLE `creator_follow`
  ADD COLUMN `attributed_post_id` BIGINT NULL COMMENT '§24-7 라스트 터치 — 팔로우 24h 이내 마지막으로 본 게시물',
  ADD KEY `idx_creator_follow_attributed` (`attributed_post_id`, `created_at`);
