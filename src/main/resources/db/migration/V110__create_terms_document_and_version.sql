-- §21 어드민 약관·정책 관리
-- FAQ(§19)·공지(§20)와 결정적으로 다르다: 원문 수정 불가 · 내리기 불가(새 버전으로 대체만) · 이력 영구 보관.
-- 동의 기록이 "동의한 버전"을 참조하므로 원문을 갱신하는 UPDATE 경로 자체를 만들지 않는다.
-- 문서 : 버전 = 1 : N 이며, 개정은 terms_version 행을 새로 쌓는 것으로만 이뤄진다.

CREATE TABLE `terms_document` (
    `terms_document_id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `name`                       VARCHAR(100) NOT NULL COMMENT '문서명',
    `type`                       VARCHAR(32)  NOT NULL COMMENT '유형(등록 후 고정): TERMS_OF_SERVICE, PRIVACY_POLICY, MARKETING_CONSENT',
    `target`                     VARCHAR(32)  NOT NULL COMMENT '대상(등록 후 고정): ALL, USER, BRAND, INFLUENCER',
    `is_superseded`              BIT(1)       NOT NULL DEFAULT b'0' COMMENT '후속 문서로 대체된 문서 — 목록의 "구버전"',
    `superseded_by_document_id`  BIGINT       NULL COMMENT '이 문서를 대체한 문서 ID',
    `registered_by`              BIGINT       NULL COMMENT '등록자(운영자 seller_id)',
    `created_at`                 DATETIME(6)  NULL,
    `modified_at`                DATETIME(6)  NULL,
    PRIMARY KEY (`terms_document_id`),
    -- 마케팅 동의는 대상별로 같은 이름의 문서를 따로 두므로 이름만으로는 중복을 가릴 수 없다
    CONSTRAINT `uk_terms_document_name_target` UNIQUE (`name`, `target`),
    KEY `idx_terms_document_type` (`type`)
) ENGINE=InnoDB;

CREATE TABLE `terms_version` (
    `terms_version_id`   BIGINT      NOT NULL AUTO_INCREMENT,
    `terms_document_id`  BIGINT      NOT NULL,
    `version_number`     VARCHAR(20) NOT NULL COMMENT '접두 v를 뺀 숫자·점 표기(예: 3.1) — v는 화면 표기다',
    `effective_date`     DATE        NOT NULL COMMENT '시행일 — 이 날 00:00(KST)에 시행중으로 전환된다',
    `content`            LONGTEXT    NOT NULL COMMENT '원문 — 등록 후 수정하지 않는다',
    `status`             VARCHAR(20) NOT NULL COMMENT 'SCHEDULED(시행 예정), EFFECTIVE(시행중), PAST(과거 버전)',
    `registered_by`      BIGINT      NULL COMMENT '등록자(운영자 seller_id)',
    `created_at`         DATETIME(6) NULL COMMENT '등록일시',
    `modified_at`        DATETIME(6) NULL,
    PRIMARY KEY (`terms_version_id`),
    CONSTRAINT `uk_terms_version_document_number` UNIQUE (`terms_document_id`, `version_number`),
    CONSTRAINT `fk_terms_version_document` FOREIGN KEY (`terms_document_id`)
        REFERENCES `terms_document` (`terms_document_id`),
    -- 문서 상세의 버전 이력과 시행 전환 배치가 함께 쓰는 인덱스다
    KEY `idx_terms_version_document_effective` (`terms_document_id`, `effective_date`),
    KEY `idx_terms_version_status_effective` (`status`, `effective_date`)
) ENGINE=InnoDB;

-- 초기 문서 8종(이용 약관 3 · 개인정보처리방침 2 · 마케팅 목적 개인정보 수집·이용 동의 3)은
-- 확정 원문이 법률 검토 대기 상태라(기획 §21-7) 여기서 넣지 않는다. 운영자가 어드민에서 등록한다.
