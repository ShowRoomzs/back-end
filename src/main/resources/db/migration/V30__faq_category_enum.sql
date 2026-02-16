-- FAQ 카테고리 컬럼을 ENUM 이름 저장용 varchar로 변경 (기존 한글 값 → enum name 매핑 없이 스키마만 변경)
-- 기존 데이터가 있다면 수동으로 enum 값(DELIVERY 등)으로 업데이트 필요
ALTER TABLE faq MODIFY COLUMN category varchar(30) NOT NULL;
