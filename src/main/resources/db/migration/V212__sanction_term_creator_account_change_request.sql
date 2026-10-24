-- 1021 설계서 7-2 — 계정 이용 정지 기간 · 자동 해제 · 이의 신청 기한(기-02 【잠정】).
-- ends_on 은 종료일(포함) — 다음 날 00:00 KST 에 자동 해제. NULL 은 기간 없는 정지(기존 행 · 수동 해제).
ALTER TABLE `brand_sanction`
    ADD COLUMN `ends_on` DATE NULL COMMENT '정지 종료일(포함) · 다음 날 00:00 자동 해제 · NULL = 기간 없음',
    ADD COLUMN `appeal_deadline_at` DATETIME(6) NULL COMMENT '이의 신청 기한 = 통지 + N영업일 23:59:59(표시용 · 접수 차단 안 함)',
    ADD COLUMN `auto_released` BIT NOT NULL DEFAULT 0;

ALTER TABLE `creator_sanction`
    ADD COLUMN `ends_on` DATE NULL COMMENT '정지 종료일(포함) · 다음 날 00:00 자동 해제 · NULL = 기간 없음',
    ADD COLUMN `appeal_deadline_at` DATETIME(6) NULL COMMENT '이의 신청 기한 = 통지 + N영업일 23:59:59(표시용 · 접수 차단 안 함)',
    ADD COLUMN `auto_released` BIT NOT NULL DEFAULT 0;

ALTER TABLE `user_sanction`
    ADD COLUMN `ends_on` DATE NULL COMMENT '정지 종료일(포함) · 다음 날 00:00 자동 해제 · NULL = 기간 없음',
    ADD COLUMN `appeal_deadline_at` DATETIME(6) NULL COMMENT '이의 신청 기한 = 통지 + N영업일 23:59:59(표시용 · 접수 차단 안 함)',
    ADD COLUMN `auto_released` BIT NOT NULL DEFAULT 0;

CREATE INDEX `idx_brand_sanction_open_ends_on` ON `brand_sanction` (`released_at`, `ends_on`);
CREATE INDEX `idx_creator_sanction_open_ends_on` ON `creator_sanction` (`released_at`, `ends_on`);
CREATE INDEX `idx_user_sanction_open_ends_on` ON `user_sanction` (`released_at`, `ends_on`);

-- 1021 설계서 8-1 — 인플루언서 정산 계좌 변경 → 변경 요청(기-22). 증빙(통장 사본)은 기존 evidence_file_* 열.
ALTER TABLE `brand_change_request`
    ADD COLUMN `account_bank_code` VARCHAR(10) NULL COMMENT 'CREATOR_ACCOUNT — 요청 은행 코드',
    ADD COLUMN `account_number_enc` VARCHAR(255) NULL COMMENT 'CREATOR_ACCOUNT — 요청 계좌번호(암호화)',
    ADD COLUMN `account_holder_name` VARCHAR(50) NULL COMMENT 'CREATOR_ACCOUNT — 예금주 = creator 실명 스냅샷';
