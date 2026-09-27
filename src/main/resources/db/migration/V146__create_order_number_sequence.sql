-- 주문번호 yyyyMMdd-NNNNNN 의 일자별 일련번호(결제 계획서 3-3). group_buy_number_sequence 와 같은 upsert 구조다.
CREATE TABLE `order_number_sequence` (
    `seq_date` DATE NOT NULL,
    `last_seq` INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
