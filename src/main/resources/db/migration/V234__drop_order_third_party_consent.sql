-- 결제 화면 「브랜드 개인정보 제3자 제공」 필수 동의 · 이력(수정요청 C-3, V231) 삭제.
--
-- 기능을 걷어내면서 V231 이 추가한 동의 이력 컬럼도 지운다. 기존 동의 이력은 없다(2026-11-21).
ALTER TABLE `orders`
    DROP COLUMN `third_party_consent_at`,
    DROP COLUMN `third_party_recipients`;
