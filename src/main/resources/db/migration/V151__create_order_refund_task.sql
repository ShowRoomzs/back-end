-- 환불 집행 큐(34 설계서 1-9) — 환불은 브랜드가 절대 실행하지 않는다. 브랜드 액션·배치는 큐 행만 쌓고
-- 집행(포트원 부분 취소 호출)은 어드민 거래 관리가 한다. refund_amount 는 예정액이지 확정액이 아니다
-- (반송 왕복 배송비 차감은 약관 근거 대기 — §34-13 #12).
CREATE TABLE `order_refund_task` (
    `refund_task_id`    BIGINT      NOT NULL AUTO_INCREMENT,
    `delivery_group_id` BIGINT      NOT NULL,
    `order_id`          BIGINT      NOT NULL,
    `source`            VARCHAR(30) NOT NULL COMMENT 'CANCEL_REQUEST_APPROVED, SELLER_DIRECT_CANCEL, RETURN_COMPLETED',
    `source_id`         BIGINT      NULL COMMENT '근거 행 id(취소 요청 등)',
    `refund_amount`     INT         NOT NULL COMMENT '부분 = 항목 합(배송비 재계산 없음) · 전체 = 항목 합 + 배송비',
    `status`            VARCHAR(16) NOT NULL COMMENT 'PENDING, DONE, VOID',
    `payment_cancel_id` BIGINT      NULL COMMENT '집행 결과(payment_cancel 행) — 어드민이 채운다',
    `created_at`        DATETIME(6) NOT NULL,
    `executed_at`       DATETIME(6) NULL,
    `executed_by`       BIGINT      NULL,
    PRIMARY KEY (`refund_task_id`),
    KEY `idx_order_refund_task_status` (`status`, `created_at`),
    KEY `idx_order_refund_task_group` (`delivery_group_id`),
    CONSTRAINT `fk_order_refund_task_group` FOREIGN KEY (`delivery_group_id`) REFERENCES `order_delivery_group` (`delivery_group_id`),
    CONSTRAINT `fk_order_refund_task_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
