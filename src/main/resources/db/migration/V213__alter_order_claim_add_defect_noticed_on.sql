-- 법정 청약철회 기한의 하자 인지일(어드민 06b B4 · 46 수정계획 3-2).
--
-- 운영자가 대신 연 클레임(B6 · opened_by = OPERATOR)만 쓴다. 적용 기한 = min(공급일 + 3개월, 인지일 + 30일) — 전자상거래법 제17조③.
-- 개설 때 운영자가 문의를 읽고 선택 입력한다. 없으면 공급 기준 기한만 나온다. 한 요청의 클레임에 같은 값을 넣는다.
--
-- main 에서 V184 로 들어왔으나 feat/add 의 V184(toss_payouts_partner)와 번호가 겹쳐 V213 으로 옮겼다.
-- main 의 V184 를 이미 적용한 DB 에서는 열이 있으므로 없을 때만 더한다.
SET @defect_noticed_on_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'order_claim'
    AND column_name = 'defect_noticed_on'
);
SET @add_defect_noticed_on_sql := IF(
  @defect_noticed_on_exists = 0,
  'ALTER TABLE order_claim ADD COLUMN defect_noticed_on DATE NULL COMMENT ''하자 인지일 — 운영자 개설 클레임만'' AFTER opened_by_admin_id',
  'SELECT 1'
);
PREPARE add_defect_noticed_on_stmt FROM @add_defect_noticed_on_sql;
EXECUTE add_defect_noticed_on_stmt;
DEALLOCATE PREPARE add_defect_noticed_on_stmt;
