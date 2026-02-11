CREATE TABLE `bank` (
    `bank_code` varchar(3) NOT NULL,
    `bank_name` varchar(50) NOT NULL,
    `is_active` bit(1) NOT NULL,
    `display_order` int NOT NULL,
    PRIMARY KEY (`bank_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 1. 주요 인터넷/시중 은행 (최상단 노출)
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('090', '카카오뱅크', true, 1);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('092', '토스뱅크', true, 2);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('004', 'KB국민은행', true, 3);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('088', '신한은행', true, 4);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('020', '우리은행', true, 5);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('081', '하나은행', true, 6);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('011', 'NH농협은행', true, 7);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('089', '케이뱅크', true, 8);

-- 2. 기타 시중/특수 은행
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('003', 'IBK기업은행', true, 10);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('023', 'SC제일은행', true, 11);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('071', '우체국', true, 12);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('045', '새마을금고', true, 13);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('048', '신협', true, 14);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('007', 'Sh수협은행', true, 15);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('027', '한국씨티은행', true, 16);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('012', '단위농협(지역농축협)', true, 17);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('064', '산림조합', true, 18);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('050', '저축은행중앙회', true, 19);

-- 3. 지방 은행
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('031', '대구은행(iM뱅크)', true, 30);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('032', '부산은행', true, 31);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('039', '경남은행', true, 32);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('034', '광주은행', true, 33);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('037', '전북은행', true, 34);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('035', '제주은행', true, 35);

-- 4. 증권사 (가나다순 정렬, display_order 50~)
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('261', '교보증권', true, 50);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('267', '대신증권', true, 51);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('287', '메리츠증권', true, 52);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('238', '미래에셋증권', true, 53);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('290', '부국증권', true, 54);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('240', '삼성증권', true, 55);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('291', '신영증권', true, 56);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('278', '신한금융투자', true, 57);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('209', '유안타증권', true, 58);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('280', '유진투자증권', true, 59);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('264', '키움증권', true, 60);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('271', '토스증권', true, 61);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('270', '하나금융투자', true, 62);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('262', '하이투자증권', true, 63);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('243', '한국투자증권', true, 64);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('269', '한화투자증권', true, 65);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('263', '현대차증권', true, 66);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('279', 'DB금융투자', true, 67);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('218', 'KB증권', true, 68);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('227', 'KTB투자증권', true, 69);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('292', 'LIG투자증권', true, 70);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('247', 'NH투자증권', true, 71);
INSERT INTO bank (bank_code, bank_name, is_active, display_order) VALUES ('266', 'SK증권', true, 72);
