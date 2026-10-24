-- 스튜디오 기본정보 관리(§50) — 정산 계좌 확인 상태 · 서류 메타 · 주민등록번호 등록 시각(50 설계서 2-2 · 구현 계획서 S3).
ALTER TABLE creator
    ADD COLUMN account_verification_status   VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'CreatorAccountVerificationStatus',
    ADD COLUMN account_verified_at           DATETIME(6)  NULL COMMENT '실명 확인 완료 시각 — T1 확인일',
    ADD COLUMN account_verification_code     VARCHAR(40)  NULL COMMENT 'FAILED 사유 — PayoutBlockReason 이름',
    ADD COLUMN bankbook_file_name            VARCHAR(255) NULL COMMENT '통장 사본 원본 파일명(표시용) — 온보딩분은 NULL',
    ADD COLUMN bankbook_submitted_at         DATETIME(6)  NULL COMMENT '통장 사본 제출 시각 — 온보딩분은 NULL',
    ADD COLUMN resident_number_registered_at DATETIME(6)  NULL COMMENT '주민등록번호 등록 시각 — 온보딩 · 비사업자 전환 승인';

-- PG 셀러가 연결된 인플루언서 = 그 계좌가 토스 검증을 통과했다. 인플루언서 계좌 변경 경로가 없었으므로 그 계좌가 현재 계좌다.
-- 날짜를 모르는 칸(서류 · 주민번호 등록 시각)은 NULL 로 둔다 — 가짜 시각을 만들지 않는다.
UPDATE creator
   SET account_verification_status = 'VERIFIED', account_verified_at = pg_partner_synced_at
 WHERE pg_partner_id IS NOT NULL AND account_number IS NOT NULL;

-- 주민등록번호 표시 — 뒤 7자리 전부 마스킹(온보딩 rev.3 · 성별 자리도 숨긴다). 읽을 때도 ResidentRegistrationNumber.display 가 맞춘다.
UPDATE creator
   SET resident_registration_number_masked = CONCAT(LEFT(resident_registration_number_masked, 7), '*******')
 WHERE resident_registration_number_masked IS NOT NULL;
