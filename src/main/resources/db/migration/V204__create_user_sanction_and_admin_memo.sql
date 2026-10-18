-- 소비자 계정 이용 정지(C20) + 어드민 소비자 제재 · 메모 · 이력(51-6 설계서 1 · 10절).
-- 브랜드(V199) · 인플루언서(V193)와 같은 골격이다 — 정지 = 행 · 메모 · 이력은 회원 유형별 테이블.
-- users.status 는 그대로 쓴다(정지 행에서 파생해 저장). user_status_history 는 전이 로그로 계속 쓴다(정지 탭 「최근 30일 신규 정지」).

-- 1-2. 정지 1건 = 1행 · 해제까지. 열린 정지 = released_at IS NULL(회원당 1건). 지급 정지 · 운영팀 카드는 없다(소비자 소명은 1:1 문의).
-- 앱의 「1회 노출」 두 가지(정지 안내 시트 · 해제 토스트)는 기기 저장소가 아니라 이 행이 기록한다(0-7).
CREATE TABLE `user_sanction` (
    `sanction_id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`                 BIGINT       NOT NULL,
    `reason`                  VARCHAR(40)  NOT NULL COMMENT 'UserSuspensionReason',
    `reason_detail`           VARCHAR(500) NULL     COMMENT 'OTHER 면 필수 · 앱 미노출',
    `suspended_at`            DATETIME(6)  NOT NULL,
    `suspended_by`            BIGINT       NOT NULL COMMENT '운영자 seller_id',
    `suspended_by_name`       VARCHAR(64)  NOT NULL COMMENT '이름 스냅샷',
    `notice_acked_at`         DATETIME(6)  NULL     COMMENT '정지 안내 시트 자동 노출 확인 — NULL 이면 다음 앱 진입 때 1회',
    `released_at`             DATETIME(6)  NULL,
    `released_by`             BIGINT       NULL,
    `released_by_name`        VARCHAR(64)  NULL,
    `release_reason`          VARCHAR(40)  NULL     COMMENT 'UserReleaseReason — RESOLVED, MISTAKE, TERM_ENDED',
    `release_notice_acked_at` DATETIME(6)  NULL     COMMENT '해제 토스트 확인',
    `active_key`              BIGINT GENERATED ALWAYS AS (IF(`released_at` IS NULL, `user_id`, NULL)) STORED,
    PRIMARY KEY (`sanction_id`),
    UNIQUE KEY `uk_user_sanction_active` (`active_key`),
    KEY `idx_user_sanction_user` (`user_id`, `suspended_at`),
    CONSTRAINT `fk_user_sanction_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-3. 관리자 메모 — 누적 · 신규 입력 300자(서버 검증). 열이 500인 것은 옛 단일 메모(users.admin_memo · 500자)를 자르지 않고 옮기기 위해서다.
CREATE TABLE `user_admin_memo` (
    `memo_id`     BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `author_id`   BIGINT       NULL     COMMENT '운영자 seller_id — 옛 메모 이관 행만 NULL',
    `author_name` VARCHAR(64)  NOT NULL COMMENT '이름 스냅샷',
    `content`     VARCHAR(500) NOT NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    `modified_at` DATETIME(6)  NULL,
    `deleted_at`  DATETIME(6)  NULL,
    PRIMARY KEY (`memo_id`),
    KEY `idx_user_admin_memo_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-3. 처리 이력 — 이 모듈이 일으킨 사건만 저장한다. 가입 · 탈퇴 · 재가입 · 소명 접수는 읽을 때 합친다.
CREATE TABLE `user_admin_history` (
    `history_id`  BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL,
    `event_type`  VARCHAR(40)  NOT NULL COMMENT 'UserAdminEventType',
    `actor_type`  VARCHAR(16)  NOT NULL COMMENT 'ADMIN, CONSUMER, SYSTEM',
    `actor_id`    BIGINT       NULL,
    `actor_name`  VARCHAR(64)  NULL     COMMENT '이름 스냅샷',
    `detail`      VARCHAR(500) NULL,
    `ref_type`    VARCHAR(32)  NULL,
    `ref_id`      BIGINT       NULL,
    `created_at`  DATETIME(6)  NOT NULL,
    PRIMARY KEY (`history_id`),
    KEY `idx_user_admin_history_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1-4. 「계정 이용 정지」 소명 문의 — 유형 값(CsCategory · FAQ 와 공유하는 5종)을 늘리지 않고 정지 행을 붙여 구분한다(V176 claim_id 와 같은 모양).
ALTER TABLE one_to_one_inquiry
    ADD COLUMN sanction_id BIGINT NULL COMMENT '계정 이용 정지 소명 대상 — 앱 「1:1 문의로 이의 신청」으로 쓴 문의만' AFTER claim_id,
    ADD KEY idx_one_to_one_inquiry_sanction (sanction_id);

-- 10-3. 옛 단일 메모 이관 — 작성자 · 시각이 기록된 적이 없다. modified_at 으로 추정하지 않는다(닉네임 · 인증 갱신에도 바뀌는 값이다).
INSERT INTO user_admin_memo (user_id, author_id, author_name, content, created_at)
SELECT u.user_id, NULL, '이전 단일 메모(작성자 미기록)', TRIM(u.admin_memo), NOW(6)
  FROM users u
 WHERE u.admin_memo IS NOT NULL AND TRIM(u.admin_memo) <> '';

-- 10-4. 옛 상태 변경(PATCH /status) 이관 — 사유 · 처리자가 없다. 탈퇴 전이는 처리 이력이 user_status_history 에서 직접 읽는다.
INSERT INTO user_admin_history (user_id, event_type, actor_type, actor_id, actor_name, detail, ref_type, ref_id, created_at)
SELECT h.user_id,
       CASE h.new_status WHEN 'SUSPENDED' THEN 'SUSPENDED' ELSE 'SUSPENSION_RELEASED' END,
       'ADMIN', NULL, NULL, '이전 방식 처리 — 사유 · 처리자 미기록', NULL, NULL,
       COALESCE(h.created_at, NOW(6))
  FROM user_status_history h
 WHERE h.new_status IN ('SUSPENDED', 'NORMAL')
   AND h.previous_status <> 'WITHDRAWN';

-- 10-5. 옛 정지 회원(status = SUSPENDED)에 정지 행은 만들지 않는다 — 사유를 지어내지 않는다. 앱 · 어드민이 행 없는 정지를 견딘다.
-- users.admin_memo 는 DROP 하지 않는다(2단계 마이그레이션의 2단계 — 시점 미정).
