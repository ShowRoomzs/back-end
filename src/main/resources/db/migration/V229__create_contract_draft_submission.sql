-- 사전 검수 승인 간주(수정요청 B-13 · 확정 정책 · 계약서 제8조).
--
-- 인플루언서가 콘텐츠 초안을 제출(= 브랜드 수령)하면 브랜드는 3영업일 안에 승인 또는 수정 요청으로 회신한다. 기간 안에 회신이 없으면
-- 시스템이 승인한 것으로 보고 초안 승인 기록(contract_draft_approval · source = DEEMED)을 남긴다.
CREATE TABLE `contract_draft_submission` (
    `submission_id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `contract_id`             BIGINT       NOT NULL,
    `title`                   VARCHAR(100) NOT NULL COMMENT '대상 — 「릴스 1차 초안」',
    `submitted_by_creator_id` BIGINT       NOT NULL,
    `submitted_at`            DATETIME(6)  NOT NULL COMMENT '제출 = 브랜드 수령 시각',
    `thread_message_id`       BIGINT       NULL COMMENT '연결·소통 스레드의 초안 메시지(선택)',
    `note`                    VARCHAR(300) NULL,
    `review_due_at`           DATETIME(6)  NOT NULL COMMENT '회신 기한 — 수령 + 3영업일의 끝',
    `status`                  VARCHAR(24)  NOT NULL COMMENT 'PENDING · APPROVED · REVISION_REQUESTED · DEEMED_APPROVED',
    `decided_at`              DATETIME(6)  NULL,
    `decision_note`           VARCHAR(300) NULL COMMENT '수정 요청 내용',
    `approval_id`             BIGINT       NULL COMMENT '승인 · 승인 간주로 남은 초안 승인 기록',
    `created_at`              DATETIME(6)  NOT NULL,
    PRIMARY KEY (`submission_id`),
    KEY `idx_contract_draft_submission_contract` (`contract_id`),
    KEY `idx_contract_draft_submission_due` (`status`, `review_due_at`),
    CONSTRAINT `fk_contract_draft_submission_contract` FOREIGN KEY (`contract_id`) REFERENCES `contract` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
