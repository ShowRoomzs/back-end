-- 유저 환불 계좌 정보 컬럼 추가 (RefundAccount embeddable)
ALTER TABLE users
    ADD COLUMN refund_bank_code VARCHAR(3)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
    ADD COLUMN refund_account_number VARCHAR(20) NULL,
    ADD COLUMN refund_account_holder VARCHAR(50) NULL;

-- bank(bank_code) FK (환불 계좌 미등록 시 NULL 허용)
ALTER TABLE users
    ADD CONSTRAINT fk_users_refund_bank
    FOREIGN KEY (refund_bank_code) REFERENCES bank (bank_code);
