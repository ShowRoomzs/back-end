-- 1017 설계서 6-1 · 7-2 — 환불 실패 시각. 어드민 홈 「환불 실패」 경과와 운영 지표 「환불 실패(기간)」가 읽는다.
-- 지금까지는 modified_at 으로 대신했다 — 이미 FAILED 인 행은 modified_at 으로 채운다.
ALTER TABLE order_refund_task
    ADD COLUMN failed_at DATETIME(6) NULL COMMENT '마지막 실패 시각 — 재시도가 성공해도 남는다' AFTER executed_at;

UPDATE order_refund_task SET failed_at = modified_at WHERE status = 'FAILED' AND failed_at IS NULL;
