-- 취소 요청 — 이행 상태가 아니라 별도 테이블(34 설계서 0-2 · 1-5). 승인·거부 후 그룹 상태는 바뀐 적이 없으므로 복귀가 자동이다.
--
-- 「하위주문당 검토 중 1건」을 생성 컬럼 UNIQUE 로 막는다(group_buy_change_request 와 같은 수법).
-- delivery_group_id FK 에 CASCADE 를 걸지 않는다 — 생성 컬럼의 기반 컬럼 FK 는 CASCADE 를 쓸 수 없다(MySQL 제약).
CREATE TABLE `order_cancel_request` (
    `cancel_request_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `delivery_group_id`         BIGINT       NOT NULL,
    `order_id`                  BIGINT       NOT NULL COMMENT '비정규화 — 소비자 앱 조회용',
    `requested_by`              BIGINT       NOT NULL COMMENT '소비자 user id',
    `reason_code`               VARCHAR(30)  NOT NULL COMMENT 'CHANGE_OF_MIND, ORDER_MISTAKE, PAYMENT_CHANGE, ETC — 소비자 앱 4택 그대로',
    `reason_detail`             VARCHAR(300) NULL COMMENT 'ETC 만 자유 입력',
    `status`                    VARCHAR(16)  NOT NULL COMMENT 'PENDING, APPROVED, REJECTED',
    `status_at_request`         VARCHAR(30)  NOT NULL COMMENT '요청 당시 이행 상태(NEW, PREPARING) — 「준비 시작 후 경과」 표기·분쟁 근거',
    `requested_at`              DATETIME(6)  NOT NULL,
    `decided_at`                DATETIME(6)  NULL,
    `decided_by`                BIGINT       NULL COMMENT '셀러 id',
    `reject_reason`             VARCHAR(500) NULL COMMENT '거부 시 필수 — 소비자에게 그대로 전달(약관 제18조①)',
    `pending_delivery_group_id` BIGINT GENERATED ALWAYS AS (IF(`status` = 'PENDING', `delivery_group_id`, NULL)) STORED,
    PRIMARY KEY (`cancel_request_id`),
    CONSTRAINT `uk_order_cancel_request_pending` UNIQUE (`pending_delivery_group_id`),
    KEY `idx_order_cancel_request_group` (`delivery_group_id`, `requested_at`),
    KEY `idx_order_cancel_request_order` (`order_id`),
    CONSTRAINT `fk_order_cancel_request_group` FOREIGN KEY (`delivery_group_id`) REFERENCES `order_delivery_group` (`delivery_group_id`),
    CONSTRAINT `fk_order_cancel_request_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 요청 대상 항목 — 취소·반품·교환 단위는 SKU(주문 항목)다.
CREATE TABLE `order_cancel_request_item` (
    `cancel_request_item_id` BIGINT NOT NULL AUTO_INCREMENT,
    `cancel_request_id`      BIGINT NOT NULL,
    `order_product_id`       BIGINT NOT NULL,
    `quantity`               INT    NOT NULL COMMENT '항목 전량 — 수량 쪼개기 기획 없음, 스냅샷',
    `refund_amount`          INT    NOT NULL COMMENT '요청 시점 환불 예정액(단가×수량)',
    PRIMARY KEY (`cancel_request_item_id`),
    CONSTRAINT `uk_order_cancel_request_item` UNIQUE (`cancel_request_id`, `order_product_id`),
    CONSTRAINT `fk_order_cancel_request_item_request` FOREIGN KEY (`cancel_request_id`)
        REFERENCES `order_cancel_request` (`cancel_request_id`) ON DELETE CASCADE,
    CONSTRAINT `fk_order_cancel_request_item_product` FOREIGN KEY (`order_product_id`) REFERENCES `order_product` (`order_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
