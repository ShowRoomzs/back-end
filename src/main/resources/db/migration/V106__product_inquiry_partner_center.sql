-- §23 파트너센터 문의 관리 개편
-- 1) 문의 유형을 3종에서 5종으로 재편 (옵션 · 성분·사용법 · 재입고 · 배송 · 기타)
-- 2) 비밀글 · 첨부 사진(최대 3장) 복원 — 소비자 문의 사양
-- 3) 상태를 두 축으로 분리 — 답변 축(status)은 그대로 두고 노출 축(exposure_status)을 신설한다.
--    삭제 요청 중에도 답변 축 값이 보존돼야 반려 시 요청 직전 상태로 정확히 되돌아간다.
-- 4) 처리 이력 테이블 신설 — 삭제 요청 반려처럼 상태값을 늘리지 않는 이벤트의 인지 경로다.
-- 5) market_inquiry_view 제거 — V102에서 1:1 문의가 빠져 상품 문의 한 테이블만 감싸는 껍데기가 됐다.

-- ── 1) 문의 유형 5종 ──────────────────────────────────────────────
ALTER TABLE product_inquiry
    MODIFY COLUMN type VARCHAR(50) NOT NULL;

-- 사이즈 문의는 옵션, 재고/재입고 문의는 재입고로 이관하고, 포괄적이던 상품 문의는 기타로 둔다.
UPDATE product_inquiry SET type = 'OPTION'  WHERE type = 'SIZE_INQUIRY';
UPDATE product_inquiry SET type = 'RESTOCK' WHERE type = 'STOCK_INQUIRY';
UPDATE product_inquiry SET type = 'ETC'
WHERE type NOT IN ('OPTION', 'INGREDIENT_USAGE', 'RESTOCK', 'DELIVERY', 'ETC');

ALTER TABLE product_inquiry
    MODIFY COLUMN type ENUM('OPTION', 'INGREDIENT_USAGE', 'RESTOCK', 'DELIVERY', 'ETC') NOT NULL;

-- ── 2) 비밀글 · 첨부 사진 ────────────────────────────────────────
-- 비밀글은 작성자가 지정하며 브랜드는 변경할 수 없다 (§23-6 ③). 기존 문의는 전부 공개로 본다.
ALTER TABLE product_inquiry
    ADD COLUMN is_secret BIT(1) NOT NULL DEFAULT b'0' COMMENT '비밀글 여부 — 작성자 지정, 변경 불가';

CREATE TABLE `product_inquiry_images` (
    `product_inquiry_id` BIGINT NOT NULL,
    `image_url`          VARCHAR(512) NULL,
    KEY `idx_product_inquiry_images_inquiry` (`product_inquiry_id`),
    CONSTRAINT `fk_product_inquiry_images_inquiry`
        FOREIGN KEY (`product_inquiry_id`) REFERENCES `product_inquiry` (`product_inquiry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── 3) 노출 축 · 답변 수정 · 삭제 요청 ───────────────────────────
ALTER TABLE product_inquiry
    ADD COLUMN `answer_modified_at`    DATETIME(6) NULL COMMENT '답변 수정 시각 — 등록 시각과 병기한다',
    ADD COLUMN `exposure_status`       VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        COMMENT '노출 축: NORMAL, DELETE_REQUESTED, DELETED',
    ADD COLUMN `delete_request_reason` VARCHAR(32) NULL
        COMMENT '브랜드 요청 사유: ABUSE, PRIVACY_EXPOSURE, ADVERTISEMENT, BRAND_COMPARISON, ETC',
    ADD COLUMN `delete_request_detail` VARCHAR(500) NULL COMMENT '요청 상세 설명 — 기타 사유일 때 필수',
    ADD COLUMN `delete_requested_at`   DATETIME(6) NULL,
    ADD COLUMN `delete_reviewed_at`    DATETIME(6) NULL COMMENT '운영자 처리 시각 — 반려·집행 공통',
    ADD COLUMN `delete_reject_reason`  VARCHAR(500) NULL COMMENT '반려 사유 — 요청 브랜드에게 전달된다',
    ADD COLUMN `delete_reason`         VARCHAR(500) NULL COMMENT '삭제 사유 — 운영자 내부 기록, 브랜드 비노출',
    ADD COLUMN `deleted_at`            DATETIME(6) NULL;

CREATE INDEX `idx_product_inquiry_exposure` ON `product_inquiry` (`exposure_status`, `status`);

-- ── 4) 처리 이력 ────────────────────────────────────────────────
CREATE TABLE `product_inquiry_history` (
    `product_inquiry_history_id` BIGINT NOT NULL AUTO_INCREMENT,
    `product_inquiry_id`         BIGINT NOT NULL,
    `history_type`               VARCHAR(32) NOT NULL
        COMMENT 'REGISTERED, ANSWERED, ANSWER_MODIFIED, DELETE_REQUESTED, DELETE_REJECTED, DELETE_EXECUTED',
    `actor_type`                 VARCHAR(16) NOT NULL COMMENT 'CONSUMER, BRAND, OPERATOR',
    `detail`                     VARCHAR(500) NULL COMMENT '삭제 요청 사유 등 부가 문구',
    `created_at`                 DATETIME(6) NOT NULL,
    PRIMARY KEY (`product_inquiry_history_id`),
    KEY `idx_product_inquiry_history_inquiry` (`product_inquiry_id`, `created_at`),
    CONSTRAINT `fk_product_inquiry_history_inquiry`
        FOREIGN KEY (`product_inquiry_id`) REFERENCES `product_inquiry` (`product_inquiry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 문의의 이력 백필 — 등록은 전부, 답변은 답변완료 건만 남긴다.
INSERT INTO `product_inquiry_history` (`product_inquiry_id`, `history_type`, `actor_type`, `created_at`)
SELECT pi.product_inquiry_id, 'REGISTERED', 'CONSUMER', pi.created_at
FROM product_inquiry pi;

INSERT INTO `product_inquiry_history` (`product_inquiry_id`, `history_type`, `actor_type`, `created_at`)
SELECT pi.product_inquiry_id, 'ANSWERED', 'BRAND', pi.answered_at
FROM product_inquiry pi
WHERE pi.answered_at IS NOT NULL;

-- ── 5) 마켓 문의 뷰 제거 ────────────────────────────────────────
DROP VIEW IF EXISTS market_inquiry_view;

-- ── 6) 답변 템플릿 카테고리도 문의 유형 5종과 맞춘다 ─────────────
ALTER TABLE answer_template
    MODIFY COLUMN category VARCHAR(50) NOT NULL;

UPDATE answer_template SET category = 'OPTION'  WHERE category = 'SIZE';
UPDATE answer_template SET category = 'RESTOCK' WHERE category = 'STOCK';
UPDATE answer_template SET category = 'ETC'
WHERE category NOT IN ('OPTION', 'INGREDIENT_USAGE', 'RESTOCK', 'DELIVERY', 'ETC');

ALTER TABLE answer_template
    MODIFY COLUMN category ENUM('OPTION', 'INGREDIENT_USAGE', 'RESTOCK', 'DELIVERY', 'ETC') NOT NULL;
