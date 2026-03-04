-- seller.role_type 에 CREATOR 추가
ALTER TABLE seller
    MODIFY COLUMN role_type
        ENUM('ADMIN','GUEST','SELLER','USER','CREATOR') NOT NULL;

-- users.role_type 에 CREATOR 추가
ALTER TABLE users
    MODIFY COLUMN role_type
        ENUM('ADMIN','GUEST','SELLER','USER','CREATOR') NOT NULL;

