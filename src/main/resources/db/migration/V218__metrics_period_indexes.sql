-- 운영 지표 기간 열 인덱스(58 계획 1-1 · 10-26 개발 서버 무응답).
--
-- 운영 지표(MetricsQueryRepository · AdminOperationsQueryRepository.processedTimes)는 원천 행을 기간 [from, to) 로 자른다.
-- 아래 열로 시작하는 인덱스가 없어 매 계산이 표 전체를 훑었다. 이미 있는 것(post_report(status, reported_at) ·
-- order_delivery_group(fulfillment_status, delivered_at) · order_refund_task(status, executed_at))은 다시 만들지 않는다.
CREATE INDEX idx_orders_paid_at ON orders (paid_at);
CREATE INDEX idx_order_claim_requested_at ON order_claim (requested_at);
CREATE INDEX idx_one_to_one_inquiry_answered_at ON one_to_one_inquiry (answered_at);
CREATE INDEX idx_group_buy_post_reviewed_at ON group_buy_post (reviewed_at);
CREATE INDEX idx_users_role_created_at ON users (role_type, created_at);
CREATE INDEX idx_order_refund_task_failed_at ON order_refund_task (failed_at);
CREATE INDEX idx_settlement_adjustment_opened_at ON settlement_adjustment (opened_at);
CREATE INDEX idx_group_buy_opened_at ON group_buy (opened_at);
CREATE INDEX idx_contract_concluded_at ON contract (concluded_at);
