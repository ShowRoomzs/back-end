-- 54_어드민_일반게시물_모니터링_BE_설계서.md 0-3 · 2-6 — 조치 · 신고 사유 축 재매핑.
--
-- rev.3이 소비자 신고 사유(C18)를 6종으로 확정했다. 조치 사유는 그 6종 + 운영자 전용 「개인정보 노출」 7종이다.
-- PostSuspensionReason · PostReportReason enum 교체와 반드시 같은 배포로 나가야 한다 —
-- 한쪽만 나가면 @Enumerated(STRING) 역직렬화가 터진다.
--
-- post_notification_log.payload에 굳은 옛 코드 문자열은 고치지 않는다 — "그때 그 통지"다.

-- ------------------------------------------------------------------ post_suspension

-- 의료적 효능 표현은 사유가 아니라 허위 · 과장 정보의 근거 규정(3조 4항)이다
UPDATE `post_suspension`
   SET `policy_clause_code` = 'ARTICLE_3_4',
       `policy_ref` = COALESCE(`policy_ref`, '커뮤니티 운영정책 3조 4항 — 의약품 · 의료기기 오인 표현')
 WHERE `reason_code` = 'MEDICAL_CLAIM';

UPDATE `post_suspension`
   SET `reason_detail` = COALESCE(`reason_detail`, '대가관계 미표시(구 사유 코드)')
 WHERE `reason_code` = 'AD_DISCLOSURE';

UPDATE `post_suspension`
   SET `reason_detail` = CASE WHEN `reason_detail` IS NULL OR `reason_detail` = ''
                              THEN '저작권 침해(구 사유 코드)'
                              ELSE LEFT(CONCAT('저작권 침해 · ', `reason_detail`), 500) END
 WHERE `reason_code` = 'COPYRIGHT';

UPDATE `post_suspension` SET `reason_code` = 'MISLEADING_INFO'
 WHERE `reason_code` IN ('MEDICAL_CLAIM', 'AD_DISCLOSURE', 'MISLEADING_AD');
UPDATE `post_suspension` SET `reason_code` = 'OTHER'      WHERE `reason_code` = 'COPYRIGHT';
UPDATE `post_suspension` SET `reason_code` = 'ABUSE_HATE' WHERE `reason_code` = 'VIOLENCE_HATE';

-- ------------------------------------------------------------------ post_report

UPDATE `post_report`
   SET `reason_detail` = COALESCE(`reason_detail`, '대가관계 미표시(구 사유 코드)')
 WHERE `reason_code` = 'AD_DISCLOSURE';

UPDATE `post_report`
   SET `reason_detail` = CASE WHEN `reason_detail` IS NULL OR `reason_detail` = ''
                              THEN '저작권 침해(구 사유 코드)'
                              ELSE LEFT(CONCAT('저작권 침해 · ', `reason_detail`), 500) END
 WHERE `reason_code` = 'COPYRIGHT';

-- 개인정보 노출은 소비자 사유에서 빠졌다(운영자 전용) — 신고 쪽은 기타로 옮긴다
UPDATE `post_report`
   SET `reason_detail` = CASE WHEN `reason_detail` IS NULL OR `reason_detail` = ''
                              THEN '개인정보 노출(구 사유 코드)'
                              ELSE LEFT(CONCAT('개인정보 노출 · ', `reason_detail`), 500) END
 WHERE `reason_code` = 'PERSONAL_INFO';

UPDATE `post_report` SET `reason_code` = 'MISLEADING_INFO'
 WHERE `reason_code` IN ('MEDICAL_CLAIM', 'AD_DISCLOSURE', 'MISLEADING_AD');
UPDATE `post_report` SET `reason_code` = 'OTHER'      WHERE `reason_code` IN ('COPYRIGHT', 'PERSONAL_INFO');
UPDATE `post_report` SET `reason_code` = 'ABUSE_HATE' WHERE `reason_code` = 'VIOLENCE_HATE';
