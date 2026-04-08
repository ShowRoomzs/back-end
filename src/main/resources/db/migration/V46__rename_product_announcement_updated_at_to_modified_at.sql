-- BaseTimeEntity.modifiedAt → 컬럼명 modified_at (V45의 updated_at과 일치시킴)
ALTER TABLE product_announcement CHANGE COLUMN updated_at modified_at DATETIME(6) NULL;
