-- 스튜디오 기본정보 관리(§50) — 탈퇴 반려 안내 S8 [확인](50 설계서 2-3 · 구현 계획서 S2).
ALTER TABLE creator_withdrawal_request
    ADD COLUMN result_acknowledged_at DATETIME(6) NULL COMMENT 'S8 반려 안내 [확인] 시각 — NULL 이면 반려 배너가 남는다';

-- 이미 반려된 건은 확인한 것으로 본다 — 배포 순간 옛 반려가 배너로 튀어나오지 않게.
UPDATE creator_withdrawal_request SET result_acknowledged_at = processed_at WHERE status = 'REJECTED';
