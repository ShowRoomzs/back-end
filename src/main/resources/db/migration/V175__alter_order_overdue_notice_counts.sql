-- 처리 지연 자동 알림 횟수(1009 기획 수정본 8-4 · 결정 「독촉 → 자동 알림」 · 어드민 06d).
--
-- 발송 기한 경과(하위주문) · 검수 기한 경과(클레임)에 영업일마다 한 번 브랜드 자동 알림이 나가고 그 횟수를 센다. 발송은 알림
-- 모듈이다. 3회 무응답이면 어드민 대행(송장 대행 · 직권 취소)이 열린다. 운영자 독촉 버튼은 없다.
ALTER TABLE order_delivery_group
    ADD COLUMN overdue_notice_count   INT         NOT NULL DEFAULT 0 COMMENT '발송 기한 경과 자동 알림 횟수' AFTER status_at_cancel,
    ADD COLUMN last_overdue_notice_at DATETIME(6) NULL AFTER overdue_notice_count;

ALTER TABLE order_claim
    ADD COLUMN inspect_notice_count   INT         NOT NULL DEFAULT 0 COMMENT '검수 기한 경과 자동 알림 횟수 — 미결제 고지와 다른 축' AFTER last_notice_at,
    ADD COLUMN last_inspect_notice_at DATETIME(6) NULL AFTER inspect_notice_count;
