/* 1. seller 테이블에 activity_name 컬럼 추가 (NULL 허용) */
alter table seller 
add column activity_name varchar(100) default null;

/* 2. market 테이블에 market_type 컬럼 추가 */
alter table market 
add column market_type varchar(20);

/* 3. 기존 market 데이터의 market_type을 'MARKET'으로 일괄 업데이트 */
update market 
set market_type = 'MARKET' 
where market_type is null;

/* 4. market_type 업데이트 완료 후 not null 제약조건 추가 (필수 권장) */
alter table market 
modify column market_type varchar(20) not null;