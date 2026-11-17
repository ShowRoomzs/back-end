-- 수정요청 C(코드에 없는 기능) — 개인정보 · 소비자 보호 3건.
--
-- ① 마케팅 수신 동의 2년 재확인(정보통신망법 시행령 제62조의3) — 동의(또는 마지막 확인 통지)한 날부터 2년마다 수신 동의 사실을
--    알린다. 마지막 통지 시각을 남긴다.
-- ② 결제 화면 「브랜드 개인정보 제3자 제공」 필수 동의 · 이력 — 주문마다 동의 시각과 제공받는 자(브랜드명)를 남긴다.
-- ③ 소비자 불만 · 분쟁 처리 기한 — 1:1 문의 접수 + 3영업일 안에 답하지 못하면 처리 경과를 통지한다(결과는 + 10영업일). 통지 시각을 남긴다.
ALTER TABLE users
    ADD COLUMN marketing_consent_noticed_at DATETIME(6) NULL COMMENT '마케팅 수신 동의 2년 재확인 통지 — 마지막 통지 시각';

ALTER TABLE orders
    ADD COLUMN third_party_consent_at DATETIME(6)  NULL COMMENT '결제 화면 브랜드 개인정보 제3자 제공 동의 시각',
    ADD COLUMN third_party_recipients VARCHAR(500) NULL COMMENT '제공받는 자 — 주문 당시 브랜드명(쉼표 구분)';

ALTER TABLE one_to_one_inquiry
    ADD COLUMN progress_noticed_at DATETIME(6) NULL COMMENT '처리 경과 통지 — 접수 + 3영업일 미답변 시 1회';
