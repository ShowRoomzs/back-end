-- 소통 스레드 파기(수정요청 C · 파트너 약관 제12조③ — 쌍 스레드 1년).
--
-- 브랜드 · 인플루언서 연결이 해제(탈퇴 포함)된 뒤 1년이 지나면 그 쌍의 연결 스레드 메시지 · 첨부를 파기한다. 스레드 행은 연결 이력의
-- 근거라 남기고 파기 시각만 적는다. 공구 이슈 · 정산 조정 스레드는 거래 기록이라 대상이 아니다.
ALTER TABLE message_thread
    ADD COLUMN purged_at DATETIME(6) NULL COMMENT '보존 기간(연결 해제 + 1년) 경과로 메시지 · 첨부를 파기한 시각' AFTER last_message_sender_type;
