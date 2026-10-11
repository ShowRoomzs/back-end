-- 검수 반려 6항목 · 운영자 개설 클레임(1009 기획 수정본 5-b · 8-1 B6).
--
-- 반려 입력 = 사유 · 상세 · 법적 근거(전자상거래법 제17조② 각 호) · 범위(전체 / 일부 + 수량) · 귀책 변경 · 증빙 · 소비자 메시지 —
-- 파트너 11 B1r · 소비자 C10-5 와 1:1 이다. 일부 반려는 반려 수량만큼 같은 요청의 새 클레임 행으로 가르고(split_from_claim_id)
-- 원래 행은 통과 수량만 남긴다. 구매확정 뒤 하자는 소비자가 직접 신청하지 못하고 운영자가 대신 연다(opened_by = OPERATOR).
ALTER TABLE order_claim
    ADD COLUMN reject_legal_basis      VARCHAR(20)  NULL COMMENT 'ART17_2_1, ART17_2_2, ART17_2_3, ART17_2_5' AFTER reject_detail,
    ADD COLUMN reject_consumer_message VARCHAR(500) NULL COMMENT '소비자에게 보낸 메시지 — 반려 상세와 따로' AFTER reject_legal_basis,
    ADD COLUMN fault_changed_to_seller BIT(1)       NOT NULL DEFAULT b'0' COMMENT '검수에서 브랜드 귀책으로 인정' AFTER reject_consumer_message,
    ADD COLUMN split_from_claim_id     BIGINT       NULL COMMENT '일부 반려로 갈라져 나온 행이면 원래 클레임' AFTER fault_changed_to_seller,
    ADD COLUMN opened_by               VARCHAR(16)  NOT NULL DEFAULT 'CONSUMER' COMMENT 'CONSUMER, OPERATOR' AFTER split_from_claim_id,
    ADD COLUMN open_reason             VARCHAR(100) NULL COMMENT '운영자 개설 사유' AFTER opened_by,
    ADD COLUMN opened_by_admin_id      BIGINT       NULL AFTER open_reason;
