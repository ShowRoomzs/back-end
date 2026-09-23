-- 계약번호 CTR-YYYYMMDD-NNN 의 일자별 일련번호(설계서 1-7).
--
-- INSERT … ON DUPLICATE KEY UPDATE last_seq = last_seq + 1 한 방으로 뽑는다.
-- ProductService.generateProductNumber()가 쓰는 findAll() → max 방식을 복제하지 않는다 —
-- 동시 요청에서 같은 번호가 나오고 행이 늘수록 느려진다.
CREATE TABLE `contract_number_sequence` (
    `seq_date` DATE NOT NULL,
    `last_seq` INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
