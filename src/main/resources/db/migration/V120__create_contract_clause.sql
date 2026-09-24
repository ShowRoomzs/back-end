-- 표준 조항(§25-7) — 브랜드가 편집할 수 없고 계약서에 자동 삽입된다.
-- 문안이 개정되면 이미 서명된 계약은 옛 문안으로 남아야 하므로 계약이 조항 「버전」을 참조한다.
-- 구조는 기존 terms_document / terms_version(V110)과 같은 패턴이다.
CREATE TABLE `contract_clause_version` (
    `contract_clause_version_id` BIGINT      NOT NULL AUTO_INCREMENT,
    `version_number`             VARCHAR(20) NOT NULL COMMENT '접두 v를 뺀 숫자·점 표기(예: 1.0)',
    `effective_date`             DATE        NOT NULL COMMENT '시행일',
    `status`                     VARCHAR(20) NOT NULL COMMENT 'SCHEDULED(시행 예정), EFFECTIVE(시행중), PAST(과거 버전)',
    `created_at`                 DATETIME(6) NULL,
    `modified_at`                DATETIME(6) NULL,
    PRIMARY KEY (`contract_clause_version_id`),
    CONSTRAINT `uk_contract_clause_version_number` UNIQUE (`version_number`),
    KEY `idx_contract_clause_version_status` (`status`, `effective_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 요약과 전문을 「같은 행에서」 파생시킨다(설계서 4-6).
-- 두 목록을 따로 관리하면 요약 11개 ↔ 전문 9조 같은 어긋남이 또 생긴다.
CREATE TABLE `contract_clause` (
    `contract_clause_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `contract_clause_version_id` BIGINT       NOT NULL,
    `code`                       VARCHAR(64)  NOT NULL COMMENT '조항 식별자',
    `sort_order`                 INT          NOT NULL DEFAULT 0,
    `summary_title`              VARCHAR(100) NOT NULL COMMENT '작성 화면 「표준 조항」 카드의 좌측 라벨',
    `summary_description`        VARCHAR(500) NOT NULL COMMENT '같은 카드의 우측 요약',
    `full_title`                 VARCHAR(100) NULL COMMENT '전문 모달(C5)의 「제N조 …」 — 미확정이면 NULL',
    `full_body`                  TEXT         NULL COMMENT '전문 본문 — 미확정이면 NULL',
    PRIMARY KEY (`contract_clause_id`),
    CONSTRAINT `uk_contract_clause_version_code` UNIQUE (`contract_clause_version_id`, `code`),
    CONSTRAINT `fk_contract_clause_version` FOREIGN KEY (`contract_clause_version_id`)
        REFERENCES `contract_clause_version` (`contract_clause_version_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- InnoDB의 외래키 이름은 테이블이 아니라 스키마 단위로 유일해야 한다.
-- 위 contract_clause가 이미 `fk_contract_clause_version`을 쓰고 있으므로 다른 이름을 준다.
ALTER TABLE `contract`
    ADD CONSTRAINT `fk_contract_clause_version_id` FOREIGN KEY (`clause_version_id`)
        REFERENCES `contract_clause_version` (`contract_clause_version_id`);

-- ─────────────────────────────────────────────────────────────────────────────
-- seed — placeholder(설계서 미결 #5)
-- 문안 2건([자문대기-법률] 제2조 대가관계 표시 · 제8조 손해배상)이 법률 검토 대기이고,
-- 요약 11개 ↔ 전문 9조 불일치도 기획 확정 전이다.
-- 확정 전까지 없는 전문을 지어내지 않는다 — 「단독 판매」·「샘플 제공」은 full_* 를 NULL로 둔다.
-- 개정은 이 행을 UPDATE 하는 것이 아니라 새 version 행을 쌓는 것으로만 이뤄진다.
INSERT INTO `contract_clause_version` (`version_number`, `effective_date`, `status`, `created_at`, `modified_at`)
VALUES ('1.0', '2026-01-01', 'EFFECTIVE', NOW(6), NOW(6));

SET @v = LAST_INSERT_ID();

INSERT INTO `contract_clause`
    (`contract_clause_version_id`, `code`, `sort_order`, `summary_title`, `summary_description`, `full_title`, `full_body`)
VALUES
(@v, 'COSMETIC_ADVERTISING', 1, '화장품 표시·광고',
 '효능·기능성 과장 금지 등 화장품법·표시광고법 준수',
 '제1조 화장품 표시·광고 준수',
 '브랜드와 인플루언서는 「화장품법」 및 「표시·광고의 공정화에 관한 법률」을 준수하며, 의약품으로 오인될 수 있는 표현과 객관적 근거 없는 효능·기능성 표현을 사용하지 않는다. 위반으로 발생한 행정처분·손해는 해당 표현을 작성한 당사자가 부담한다.'),
(@v, 'SPONSORSHIP_DISCLOSURE', 2, '대가관계 표시',
 '게시물 유료광고(광고·협찬) 표시 의무',
 '제2조 대가관계 표시 의무',
 '인플루언서는 이 계약에 따른 게시물에 경제적 이해관계를 소비자가 명확히 인식할 수 있는 방식으로 표시한다. 표시 문구·위치·크기는 관계 법령과 심사지침을 따른다.'),
(@v, 'EXCLUSIVE_SALE', 3, '단독 판매',
 '공구 기간 중 해당 상품은 이 공구에서만 판매합니다. 브랜드는 같은 기간 타 채널·타 인플루언서 공구를 병행하지 않습니다.',
 NULL, NULL),
(@v, 'SAMPLE_SUPPLY', 4, '샘플 제공',
 '브랜드는 콘텐츠 제작에 필요한 상품을 무상으로 제공합니다. 샘플 비용은 청구·정산 대상이 아닙니다.',
 NULL, NULL),
(@v, 'PRICE_POLICY', 5, '가격 정책',
 '공구 기간 중 타 채널 최저가 준수',
 '제3조 최저가 정책',
 '브랜드는 공구 기간 중 동일 상품을 공구가보다 낮은 가격으로 타 채널에 판매하지 않는다. 불가피한 사유가 있으면 사전에 인플루언서에게 알리고 협의한다.'),
(@v, 'INTELLECTUAL_PROPERTY', 6, '지식재산권',
 '브랜드 상표·상품 이미지 사용 허락 범위',
 '제4조 지식재산권 사용 범위',
 '브랜드는 상표·상품 이미지의 사용을 이 계약 목적 범위에서 인플루언서에게 허락한다. 인플루언서가 제작한 콘텐츠의 저작권은 인플루언서에게 있으며, 브랜드의 2차 활용은 계약에 정한 범위·기간에 한한다.'),
(@v, 'CONFIDENTIALITY', 7, '비밀유지',
 '공구가·리워드율·계약 조건 비공개',
 '제5조 비밀유지',
 '당사자는 공구가·리워드율·고정 지급비 등 계약 조건을 제3자에게 공개하지 않는다. 이 의무는 계약 종료 후 1년간 존속한다.'),
(@v, 'BREACH_MEASURES', 8, '위반 시 조치',
 '콘텐츠 미이행·허위광고 시 리워드 보류·공구 중단',
 '제6조 위반 시 조치',
 '콘텐츠 의무 미이행, 허위·과장 광고, 대가관계 미표시가 확인되면 플랫폼은 리워드 지급을 보류하거나 공구를 중단할 수 있다.'),
(@v, 'CUSTOMER_SERVICE', 9, 'CS 응대 주체',
 '상품 문의·반품 처리는 브랜드가 담당합니다. 콘텐츠 이행 확인도 당사자 간 직접 확인합니다.',
 '제7조 CS 응대 주체',
 '상품 문의·반품·교환 처리는 브랜드가 담당한다. 콘텐츠 이행 확인은 브랜드와 인플루언서가 연결·소통 스레드에서 직접 한다.'),
(@v, 'DAMAGES_PENALTY', 10, '손해배상·위약금',
 '위반 시 손해배상·위약금',
 '제8조 손해배상·위약금',
 '당사자의 귀책으로 상대방에게 손해가 발생한 경우 그 손해를 배상한다. 위약금의 산정 방식과 상한은 별도로 정한다.'),
(@v, 'GOVERNING_LAW', 11, '준거법·분쟁',
 '관할·준거법·분쟁 해결 절차',
 '제9조 준거법·분쟁',
 '이 계약은 대한민국 법을 준거법으로 하고, 분쟁은 브랜드 주소지 관할 법원을 제1심 관할로 한다.');
