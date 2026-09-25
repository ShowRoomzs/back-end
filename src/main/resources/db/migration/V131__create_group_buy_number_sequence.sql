-- 공구번호 GB-YYYYMMDD-NNN 의 일자별 일련번호(설계서 1-10).
-- contract_number_sequence와 같은 upsert 방식이다. 접두어 컬럼을 추가해 공용화하지 않는다 —
-- 계약 테이블의 PK를 바꾸는 마이그레이션이 되고, 얻는 것은 테이블 1개 절약뿐이다.
CREATE TABLE `group_buy_number_sequence` (
    `seq_date` DATE NOT NULL,
    `last_seq` INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`seq_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
