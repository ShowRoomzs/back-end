-- 도메인 변경: showroomz.shop → showroomz.co.kr (2026-11-09)
--
-- 저장된 URL 의 호스트만 바꾼다. 대상은 현재 스키마의 모든 문자열(char · varchar · *text) · json 컬럼이다.
--   https://media.showroomz.shop/uploads/a.jpg → https://media.showroomz.co.kr/uploads/a.jpg
--   https://showroomz.shop/market/1           → https://showroomz.co.kr/market/1
-- 「://」 바로 뒤의 호스트만 매칭하므로 이메일(admin@showroomz.shop · 로그인 ID)은 바꾸지 않는다.
-- 뒤에 영숫자 · 하이픈이 이어지면(showroomz.shopping) 매칭하지 않는다. 다시 돌려도 결과가 같다.
-- 컬럼을 하나씩 나열하지 않고 information_schema 를 훑는다 — 이미지 · 첨부 · 본문 HTML 에 박힌 URL 까지 빠짐없이 잡기 위해서다.
-- 생성 컬럼 · 뷰 · flyway_schema_history 는 건너뛴다.

DROP PROCEDURE IF EXISTS v222_replace_showroomz_domain;

DELIMITER $$

CREATE PROCEDURE v222_replace_showroomz_domain()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_table VARCHAR(64);
    DECLARE v_column VARCHAR(64);
    DECLARE v_type VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
        FROM information_schema.COLUMNS c
        JOIN information_schema.TABLES t
          ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME
        WHERE c.TABLE_SCHEMA = DATABASE()
          AND t.TABLE_TYPE = 'BASE TABLE'
          AND c.TABLE_NAME <> 'flyway_schema_history'
          AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext', 'json')
          AND c.EXTRA NOT LIKE '%GENERATED%';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- 백슬래시 이스케이프를 피하려고 점은 [.] 로 쓴다. $1 = 「https://」 + 서브도메인들.
    -- (?i) — _bin 콜레이션 컬럼에서도 대소문자를 가리지 않게.
    SET @v222_pattern = '(?i)(https?://([a-z0-9-]+[.])*)showroomz[.]shop(?![a-z0-9-])';
    SET @v222_replace = '$1showroomz.co.kr';

    OPEN cur;
    scan: LOOP
        FETCH cur INTO v_table, v_column, v_type;
        IF done = 1 THEN
            LEAVE scan;
        END IF;

        -- 패턴은 리터럴(QUOTE)로 박는다 — 리터럴은 컬럼 콜레이션을 따라가서 콜레이션 충돌이 없다.
        IF v_type = 'json' THEN
            SET @v222_sql = CONCAT(
                'UPDATE `', v_table, '` SET `', v_column, '` = CAST(REGEXP_REPLACE(CAST(`', v_column,
                '` AS CHAR CHARACTER SET utf8mb4), ', QUOTE(@v222_pattern), ', ', QUOTE(@v222_replace), ') AS JSON)',
                ' WHERE CAST(`', v_column, '` AS CHAR CHARACTER SET utf8mb4) REGEXP ', QUOTE(@v222_pattern));
        ELSE
            SET @v222_sql = CONCAT(
                'UPDATE `', v_table, '` SET `', v_column, '` = REGEXP_REPLACE(`', v_column, '`, ',
                QUOTE(@v222_pattern), ', ', QUOTE(@v222_replace), ')',
                ' WHERE `', v_column, '` REGEXP ', QUOTE(@v222_pattern));
        END IF;

        PREPARE v222_stmt FROM @v222_sql;
        EXECUTE v222_stmt;
        DEALLOCATE PREPARE v222_stmt;
    END LOOP;
    CLOSE cur;
END$$

DELIMITER ;

CALL v222_replace_showroomz_domain();

DROP PROCEDURE v222_replace_showroomz_domain;
