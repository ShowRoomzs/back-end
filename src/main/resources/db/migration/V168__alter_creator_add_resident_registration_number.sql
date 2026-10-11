-- 인플루언서 주민등록번호(1009 기획 수정본 7-a · 쇼룸 스튜디오 03 온보딩 rev.3).
--
-- 리워드 지급자가 SHOWROOMZ 로 확정돼 비사업자 원천징수 신고(지급명세서)에 주민등록번호가 필요해졌다. 온보딩이 유일한
-- 수집 지점이고 비사업자만 필수다. 원문은 AES-256-GCM 암호문으로만 두고, 화면은 마스킹 값(900101-1******)을 읽는다.
ALTER TABLE creator
    ADD COLUMN resident_registration_number_enc    VARCHAR(255) NULL COMMENT 'AES-256-GCM base64(IV‖암호문‖태그) — 비사업자만',
    ADD COLUMN resident_registration_number_masked VARCHAR(14)  NULL COMMENT '표시용 마스킹 — 900101-1******';
