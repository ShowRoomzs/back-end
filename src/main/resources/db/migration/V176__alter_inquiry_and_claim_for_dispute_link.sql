-- 반려 이의 자동 연결(어드민 06b · 기획 §38-8 B-12 확정 2026-10-09 — 앱 자동 연결).
--
-- 소비자가 앱 반려 안내(C10-5)의 「이의 제기」로 1:1 문의를 쓰면 문의에 claim_id 가 붙고, 그 클레임에 이의 문의 · 접수 시각이 남는다.
-- 어드민 06b 는 이 값으로 요약 「반려 이의 N건」 · 목록 보조줄 · 상세 ④ 「소비자 이의」를 그린다. 운영자 수동 연결은 두지 않는다.
ALTER TABLE one_to_one_inquiry
    ADD COLUMN claim_id BIGINT NULL COMMENT '반려 이의 대상 클레임 — 앱 「이의 제기」로 쓴 문의만' AFTER order_id,
    ADD KEY idx_one_to_one_inquiry_claim (claim_id);

ALTER TABLE order_claim
    ADD COLUMN dispute_inquiry_id BIGINT      NULL COMMENT '가장 최근 이의 문의(one_to_one_inquiry)' AFTER opened_by_admin_id,
    ADD COLUMN disputed_at        DATETIME(6) NULL COMMENT '가장 최근 이의 접수 시각' AFTER dispute_inquiry_id;
