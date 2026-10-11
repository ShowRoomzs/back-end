package showroomz.api.admin.settlement.docs;

/**
 * 어드민 정산 관리(07a 목록 · 07b 상세) Swagger 예시 JSON. 기준 시각은 2026-10-10 11:00(토) 이고, 금액은 44 어드민 설계서 2-4 의 산식
 * (전 항목 절사 · PG 3% · 플랫폼 0% · 리워드 부가세 10% · 원천징수 3% + 0.3% 각각 절사)으로 검산한 값이다.
 *
 * <ul>
 *   <li>{@code STL-2609-006}(id 26) — 데일리랩 × 소연_쇼룸(비사업자) · 자동 확정 · 지급 예정. 설계서 더미(5,480,000) 그대로</li>
 *   <li>{@code STL-2610-002}(id 29) — 데일리랩 × 민지의 뷰티룸(사업자) · 조정 협의(브랜드 요청 270,000 → 인플루언서 282,000)</li>
 *   <li>{@code STL-2609-004}(id 24) — 벨라코스 × 하늘의 화장대(비사업자) · 브랜드 행 분배 실패(예금주 불일치)</li>
 *   <li>{@code STL-2609-005}(id 25) — 데일리랩 × 지우_스타일(사업자) · 인플루언서 세금계산서 확인 대기 · 인플루언서 몫 지급 보류</li>
 * </ul>
 *
 * <p>상세는 응답이 크므로 블록을 상수로 나눠 이어 붙인다(컴파일 타임 상수 결합이라 어노테이션에 쓸 수 있다).
 */
final class AdminSettlementDocsExamples {

    private AdminSettlementDocsExamples() {
    }

    // ── 07a 목록 ──────────────────────────────────────────────────────────

    static final String LIST_ALL = """
            {
              "content": [
                {
                  "settlementId": 29, "settlementNumber": "STL-2610-002", "groupBuyId": 55, "groupBuyTitle": "가을 립 틴트 공구",
                  "marketId": 17, "brandName": "데일리랩", "creatorId": 31, "showroomName": "민지의 뷰티룸", "creatorBusinessType": "BUSINESS",
                  "periodStartAt": "2026-09-15T00:00:00", "periodEndAt": "2026-09-21T23:59:59",
                  "confirmedSalesAmount": 1960000, "brandPayoutAmount": 1577800, "creatorPayoutAmount": 323400,
                  "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                  "scheduleAt": "2026-10-20T23:59:59", "scheduleKind": "AGREEMENT_DUE"
                },
                {
                  "settlementId": 32, "settlementNumber": "STL-2610-005", "groupBuyId": 58, "groupBuyTitle": "가을 보습 크림 공구",
                  "marketId": 22, "brandName": "글로우코스", "creatorId": 7, "showroomName": "소연_쇼룸", "creatorBusinessType": "INDIVIDUAL",
                  "periodStartAt": "2026-09-21T00:00:00", "periodEndAt": "2026-09-27T23:59:59",
                  "confirmedSalesAmount": 2350000, "brandPayoutAmount": 1969300, "creatorPayoutAmount": 272694,
                  "status": "REVIEWING", "statusLabel": "정산 확인 중", "statusTone": "INFO",
                  "scheduleAt": "2026-10-13T23:59:59", "scheduleKind": "REVIEW_DUE"
                },
                {
                  "settlementId": 26, "settlementNumber": "STL-2609-006", "groupBuyId": 41, "groupBuyTitle": "여름 수분 세럼 공구",
                  "marketId": 17, "brandName": "데일리랩", "creatorId": 7, "showroomName": "소연_쇼룸", "creatorBusinessType": "INDIVIDUAL",
                  "periodStartAt": "2026-09-01T00:00:00", "periodEndAt": "2026-09-07T23:59:59",
                  "confirmedSalesAmount": 5480000, "brandPayoutAmount": 4712800, "creatorPayoutAmount": 529916,
                  "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                  "scheduleAt": "2026-10-12T00:00:00", "scheduleKind": "PAYOUT_DUE"
                },
                {
                  "settlementId": 24, "settlementNumber": "STL-2609-004", "groupBuyId": 38, "groupBuyTitle": "산뜻 선크림 공구",
                  "marketId": 25, "brandName": "벨라코스", "creatorId": 19, "showroomName": "하늘의 화장대", "creatorBusinessType": "INDIVIDUAL",
                  "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                  "confirmedSalesAmount": 1280000, "brandPayoutAmount": 1100800, "creatorPayoutAmount": 123776,
                  "status": "PAYOUT_FAILED", "statusLabel": "분배 실패", "statusTone": "DANGER",
                  "scheduleAt": "2026-10-07T10:00:12", "scheduleKind": "FAILED_AT"
                },
                {
                  "settlementId": 25, "settlementNumber": "STL-2609-005", "groupBuyId": 39, "groupBuyTitle": "가을 쿠션 팩트 공구",
                  "marketId": 17, "brandName": "데일리랩", "creatorId": 27, "showroomName": "지우_스타일", "creatorBusinessType": "BUSINESS",
                  "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                  "confirmedSalesAmount": 5480000, "brandPayoutAmount": 4712800, "creatorPayoutAmount": 602800,
                  "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                  "scheduleAt": "2026-10-07T00:00:00", "scheduleKind": "PAYOUT_DUE"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 3, "totalResults": 12, "limit": 5, "hasNext": true},
              "footer": {
                "count": 9, "confirmedSalesAmount": 31420000, "brandPayoutAmount": 25936600, "pgFeeAmount": 942600,
                "creatorPayoutAmount": 4129301, "withholdingAmount": 102099, "creatorVatAmount": 103400, "rewardVatAmount": 412800,
                "platformFeeAmount": 0
              },
              "toolbar": null
            }
            """;

    static final String LIST_ADJUSTING = """
            {
              "content": [
                {
                  "settlementId": 29, "settlementNumber": "STL-2610-002", "groupBuyId": 55, "groupBuyTitle": "가을 립 틴트 공구",
                  "marketId": 17, "brandName": "데일리랩", "creatorId": 31, "showroomName": "민지의 뷰티룸", "creatorBusinessType": "BUSINESS",
                  "periodStartAt": "2026-09-15T00:00:00", "periodEndAt": "2026-09-21T23:59:59",
                  "confirmedSalesAmount": 1960000, "brandPayoutAmount": 1577800, "creatorPayoutAmount": 323400,
                  "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                  "scheduleAt": "2026-10-20T23:59:59", "scheduleKind": "AGREEMENT_DUE"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false},
              "footer": null,
              "toolbar": {"heldAmount": 1960000, "earliestDeadlineAt": "2026-10-20T23:59:59"}
            }
            """;

    static final String LIST_PAYOUT_FAILED = """
            {
              "content": [
                {
                  "settlementId": 24, "settlementNumber": "STL-2609-004", "groupBuyId": 38, "groupBuyTitle": "산뜻 선크림 공구",
                  "marketId": 25, "brandName": "벨라코스", "creatorId": 19, "showroomName": "하늘의 화장대", "creatorBusinessType": "INDIVIDUAL",
                  "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                  "confirmedSalesAmount": 1280000, "brandPayoutAmount": 1100800, "creatorPayoutAmount": 123776,
                  "status": "PAYOUT_FAILED", "statusLabel": "분배 실패", "statusTone": "DANGER",
                  "scheduleAt": "2026-10-07T10:00:12", "scheduleKind": "FAILED_AT"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false},
              "footer": null,
              "toolbar": {"undeliveredAmount": 1100800, "failedPayeeLabel": "브랜드 1", "elapsedDays": 3}
            }
            """;

    static final String LIST_EVIDENCE = """
            {
              "content": [
                {
                  "documentId": 61, "settlementId": 24, "settlementNumber": "STL-2609-004", "groupBuyTitle": "산뜻 선크림 공구",
                  "counterpartyName": "벨라코스", "creatorBusinessType": "INDIVIDUAL",
                  "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서", "direction": "플랫폼 → 브랜드",
                  "inputAt": null, "dueAt": "2026-11-10", "status": "PENDING_ISSUE", "statusLabel": "발행 대기", "payoutImpact": "NONE"
                },
                {
                  "documentId": 64, "settlementId": 26, "settlementNumber": "STL-2609-006", "groupBuyTitle": "여름 수분 세럼 공구",
                  "counterpartyName": "데일리랩", "creatorBusinessType": "INDIVIDUAL",
                  "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서", "direction": "플랫폼 → 브랜드",
                  "inputAt": null, "dueAt": "2026-11-10", "status": "PENDING_ISSUE", "statusLabel": "발행 대기", "payoutImpact": "NONE"
                },
                {
                  "documentId": null, "settlementId": 23, "settlementNumber": "STL-2609-003", "groupBuyTitle": "초가을 토너 패드 공구",
                  "counterpartyName": "유나의 서랍", "creatorBusinessType": "INDIVIDUAL",
                  "type": "RESIDENT_NUMBER_MISSING", "typeLabel": "주민등록번호 미등록", "direction": "인플루언서 → 플랫폼",
                  "inputAt": null, "dueAt": null, "status": "WAITING_CREATOR", "statusLabel": "인플루언서 등록 대기", "payoutImpact": "BLOCKING"
                },
                {
                  "documentId": 63, "settlementId": 25, "settlementNumber": "STL-2609-005", "groupBuyTitle": "가을 쿠션 팩트 공구",
                  "counterpartyName": "지우_스타일", "creatorBusinessType": "BUSINESS",
                  "type": "CREATOR_TAX_INVOICE", "typeLabel": "인플루언서 세금계산서", "direction": "인플루언서 → 플랫폼",
                  "inputAt": "2026-10-08T16:40:00", "dueAt": null, "status": "SUBMITTED", "statusLabel": "확인 대기", "payoutImpact": "BLOCKING"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 4, "limit": 20, "hasNext": false},
              "footer": null,
              "toolbar": {"count": 4, "operatorActionCount": 3, "payoutBlockedCount": 2}
            }
            """;

    static final String LIST_CLAWBACK = """
            {
              "content": [
                {
                  "clawbackNumber": "CLW-0003", "originSettlementId": 11, "originSettlementNumber": "STL-2608-011",
                  "orderNumber": "20260801-000207", "productName": "수분 앰플 30ml", "brandName": "데일리랩", "showroomName": "소연_쇼룸",
                  "reasonLabel": "구매확정 후 하자", "refundAmount": 30000, "brandAmount": 26700, "creatorAmount": 3000,
                  "brandStatus": "PENDING", "brandStatusLabel": "차감 예정", "creatorStatus": "PENDING", "creatorStatusLabel": "차감 예정",
                  "appliedSettlementNumber": null, "unrecoverableReasonLabel": null, "createdAt": "2026-10-08T15:32:00"
                },
                {
                  "clawbackNumber": "CLW-0002", "originSettlementId": 2, "originSettlementNumber": "STL-2607-008",
                  "orderNumber": "20260718-000093", "productName": "비타 토너 200ml", "brandName": "오브제뷰티", "showroomName": "하루_일기",
                  "reasonLabel": "반품 검수 통과", "refundAmount": 19800, "brandAmount": 17622, "creatorAmount": 1980,
                  "brandStatus": "APPLIED", "brandStatusLabel": "차감 반영", "creatorStatus": "UNRECOVERABLE", "creatorStatusLabel": "미회수",
                  "appliedSettlementNumber": "STL-2608-014", "unrecoverableReasonLabel": "인플루언서 탈퇴", "createdAt": "2026-08-04T11:20:00"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false},
              "footer": null,
              "toolbar": {"count": 2, "unrecoverableCount": 1, "unrecoverableAmount": 1980}
            }
            """;

    static final String SUMMARY = """
            {
              "tabCounts": {"ALL": 12, "REVIEWING": 2, "ADJUSTING": 1, "PAYOUT_FAILED": 1, "EVIDENCE": 4, "CLAWBACK": 2},
              "gnbBadge": 5
            }
            """;

    // ── 07b 상세 — STL-2609-006 지급 예정 · 비사업자 ─────────────────────────────

    private static final String A_HEAD = """
            {
              "settlementId": 26, "settlementNumber": "STL-2609-006",
              "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
              "overview": {
                "groupBuyId": 41, "groupBuyNumber": "GB-20260814-041", "groupBuyTitle": "여름 수분 세럼 공구", "contractId": 52,
                "marketId": 17, "brandName": "데일리랩", "creatorId": 7, "showroomName": "소연_쇼룸", "creatorBusinessType": "INDIVIDUAL",
                "periodStartAt": "2026-09-01T00:00:00", "periodEndAt": "2026-09-07T23:59:59",
                "ordersClosedAt": "2026-09-30T13:52:00", "createdAt": "2026-09-30T14:00:00", "orderCount": 142, "closedCount": 142
              },
              "stage": {"current": "CONFIRMED", "adjustmentSkipped": true},
              "breakdown": {
                "brand": {
                  "grossOrderAmount": 5600000, "cancel": {"amount": 72000, "count": 2}, "return": {"amount": 24000, "count": 1},
                  "deliveryException": {"amount": 24000, "count": 1}, "confirmedSalesAmount": 5480000,
                  "pgFee": {"rate": 0.0300, "amount": 164400}, "platformFee": {"rate": 0.0000, "amount": 0},
                  "originalRewardAmount": 548000, "rewardAmount": 548000, "rewardVat": {"rate": 0.1000, "amount": 54800},
                  "reshipFee": {"amount": 0, "count": 0}, "consumerDeliveryFee": 0,
                  "payoutBeforeClawback": 4712800, "clawbackAmount": 0, "payoutAmount": 4712800
                },
                "creator": {
                  "businessType": "INDIVIDUAL", "rewardAmount": 548000, "clawbackAmount": 0,
                  "withholding": {"amount": 18084, "incomeRate": 0.0300, "localRate": 0.0030}, "vatAmount": 0, "payoutAmount": 529916
                },
                "platformShareAmount": 54800
              },
              "clawbacksApplied": [],
              "adjustment": null,
              "payouts": {
                "rows": [
                  {
                    "payoutId": 76, "payee": "BRAND", "payeeLabel": "브랜드", "payeeName": "데일리랩", "amount": 4712800,
                    "bankName": "국민은행", "accountNumber": "123401-04-567890", "accountHolder": "주식회사 데일리랩", "accountSource": "CURRENT_PROFILE",
                    "status": "SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL", "dueDate": "2026-10-12",
                    "requestedAt": null, "paidAt": null, "failedAt": null, "failCode": null, "failReason": null, "pgReference": null, "attempt": 0
                  },
                  {
                    "payoutId": 77, "payee": "CREATOR", "payeeLabel": "인플루언서", "payeeName": "소연_쇼룸", "amount": 529916,
                    "bankName": "카카오뱅크", "accountNumber": "3333-05-1234567", "accountHolder": "이소연", "accountSource": "CURRENT_PROFILE",
                    "status": "SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL", "dueDate": "2026-10-12",
                    "requestedAt": null, "paidAt": null, "failedAt": null, "failCode": null, "failReason": null, "pgReference": null, "attempt": 0
                  },
                  {
                    "payoutId": 78, "payee": "PLATFORM", "payeeLabel": "플랫폼", "payeeName": "SHOWROOMZ", "amount": 54800,
                    "bankName": null, "accountNumber": null, "accountHolder": "SHOWROOMZ", "accountSource": "NONE",
                    "status": "SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL", "dueDate": "2026-10-12",
                    "requestedAt": null, "paidAt": null, "failedAt": null, "failCode": null, "failReason": null, "pgReference": null, "attempt": 0
                  }
                ],
                "pgFeeAmount": 164400,
                "check": {"total": 5480000, "inflowAmount": 5480000, "confirmedSalesAmount": 5480000, "withholdingAmount": 18084, "balanced": true}
              },
            """;

    private static final String A_TAX_PENDING = """
              "taxDocuments": [
                {
                  "documentId": 64, "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서", "direction": "플랫폼 → 브랜드",
                  "counterpartyName": "주식회사 데일리랩", "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "PENDING_ISSUE", "statusLabel": "발행 대기", "approvalNumber": null, "issuedDate": null, "dueDate": "2026-11-10",
                  "submittedAt": null, "verifiedAt": null, "rejectReason": null, "rejectReasonLabel": null, "fileName": null,
                  "actions": {"canVerify": false, "canRegister": true}
                }
              ],
            """;

    private static final String A_TAX_ISSUED = """
              "taxDocuments": [
                {
                  "documentId": 64, "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서", "direction": "플랫폼 → 브랜드",
                  "counterpartyName": "주식회사 데일리랩", "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "ISSUED", "statusLabel": "발행 완료", "approvalNumber": "20261010-********-****5920",
                  "issuedDate": "2026-10-10", "dueDate": "2026-11-10",
                  "submittedAt": null, "verifiedAt": null, "rejectReason": null, "rejectReasonLabel": null,
                  "fileName": "세금계산서_STL-2609-006.pdf",
                  "actions": {"canVerify": false, "canRegister": false}
                }
              ],
            """;

    /** 명세 행 — 상세 미리보기와 GET …/items 가 같은 행을 쓴다. */
    private static final String A_ITEM_ROWS = """
                  {
                    "itemId": 2051, "orderId": 6120, "deliveryGroupId": 2440, "orderNumber": "20260907-000318", "subOrderNumber": "20260907-000318-01",
                    "consumerNameMasked": "이*연", "productName": "수분 세럼 50ml", "optionName": null,
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 24000, "paidAmount": 24000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 24000, "rewardRate": 10.0, "rewardAmount": 2400
                  },
                  {
                    "itemId": 2050, "orderId": 6114, "deliveryGroupId": 2433, "orderNumber": "20260907-000301", "subOrderNumber": "20260907-000301-01",
                    "consumerNameMasked": "박*윤", "productName": "수분 세럼 50ml", "optionName": "2개 세트",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 46000, "paidAmount": 46000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 46000, "rewardRate": 10.0, "rewardAmount": 4600
                  },
                  {
                    "itemId": 2047, "orderId": 6098, "deliveryGroupId": 2417, "orderNumber": "20260906-000287", "subOrderNumber": "20260906-000287-01",
                    "consumerNameMasked": "김*지", "productName": "수분 세럼 50ml", "optionName": null,
                    "quantity": 2, "returnedQuantity": 1, "settledQuantity": 1, "unitPrice": 24000, "paidAmount": 48000,
                    "status": "PARTIAL_RETURNED", "statusLabel": "부분 반품", "settledAmount": 24000, "rewardRate": 10.0, "rewardAmount": 2400
                  },
                  {
                    "itemId": 2046, "orderId": 6090, "deliveryGroupId": 2409, "orderNumber": "20260906-000254", "subOrderNumber": "20260906-000254-01",
                    "consumerNameMasked": "정*우", "productName": "수분 세럼 50ml", "optionName": "2개 세트",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 0, "unitPrice": 46000, "paidAmount": 46000,
                    "status": "CANCELLED", "statusLabel": "취소", "settledAmount": 0, "rewardRate": 10.0, "rewardAmount": 0
                  },
                  {
                    "itemId": 2043, "orderId": 6071, "deliveryGroupId": 2390, "orderNumber": "20260905-000233", "subOrderNumber": "20260905-000233-01",
                    "consumerNameMasked": "최*아", "productName": "수분 세럼 50ml", "optionName": null,
                    "quantity": 3, "returnedQuantity": 0, "settledQuantity": 3, "unitPrice": 24000, "paidAmount": 72000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 72000, "rewardRate": 10.0, "rewardAmount": 7200
                  }
            """;

    private static final String A_MID = """
              "fixedFee": {"contractId": 52, "amount": 300000, "trigger": "POST_REGISTERED", "triggerLabel": "공구 게시물 등록 후"},
              "items": {
                "total": 151, "returnCount": 1, "exchangeCount": 0,
                "rows": [
            """ + A_ITEM_ROWS + """
                ],
                "footer": {"paidAmount": 5600000, "settledAmount": 5480000, "itemRewardTotal": 548000, "rewardAmount": 548000}
              },
              "rail": {
                "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                "confirmedSalesAmount": 5480000, "brandPayoutAmount": 4712800, "creatorPayoutAmount": 529916,
                "reviewDueAt": "2026-10-05T23:59:59", "deadlineAt": null,
                "confirmedAt": "2026-10-06T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-12", "payoutBasis": "자동 확정 10.06 + 3영업일 · 10.09 한글날 제외", "paidAt": null,
                "blockReasons": [], "failedPayout": null
              },
            """;

    private static final String A_ACTIONS_PENDING = """
              "actions": {"canRedistribute": false, "canVerifyInvoice": false, "canRegisterBrandInvoice": true, "canDownloadStatement": true},
            """;

    private static final String A_ACTIONS_ISSUED = """
              "actions": {"canRedistribute": false, "canVerifyInvoice": false, "canRegisterBrandInvoice": false, "canDownloadStatement": true},
            """;

    private static final String A_HISTORY_ISSUED = """
                {"eventType": "BRAND_INVOICE_ISSUED", "label": "브랜드 세금계산서 발행본 등록", "actorType": "ADMIN", "actorLabel": "김운영",
                 "detail": "브랜드 세금계산서 발행본 등록 · 발행일 2026-10-10", "occurredAt": "2026-10-10T11:00:00"},
            """;

    private static final String A_HISTORY = """
                {"eventType": "TAX_INVOICE_REQUESTED", "label": "세금계산서 발행 요청", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "브랜드 세금계산서 발행 대기 · 기한 11.10", "occurredAt": "2026-10-06T00:00:00"},
                {"eventType": "AUTO_CONFIRMED", "label": "자동 확정", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "자동 확정 10.06 + 3영업일 · 10.09 한글날 제외", "occurredAt": "2026-10-06T00:00:00"},
                {"eventType": "CREATED", "label": "정산 생성 · 금액 공개", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "공구 종료 · 주문 142건 종결 · 금액 공개 · 확인 기간 10.05 23:59까지", "occurredAt": "2026-09-30T14:00:00"}
            """;

    private static final String HISTORY_OPEN = """
              "history": [
            """;

    private static final String DETAIL_CLOSE = """
              ]
            }
            """;

    static final String DETAIL_SCHEDULED = A_HEAD + A_TAX_PENDING + A_MID + A_ACTIONS_PENDING
            + HISTORY_OPEN + A_HISTORY + DETAIL_CLOSE;

    /** M5 발행본 등록 직후 — 문서 ISSUED · 등록 버튼 사라짐 · 이력 BRAND_INVOICE_ISSUED. */
    static final String DETAIL_BRAND_INVOICE_ISSUED = A_HEAD + A_TAX_ISSUED + A_MID + A_ACTIONS_ISSUED
            + HISTORY_OPEN + A_HISTORY_ISSUED + A_HISTORY + DETAIL_CLOSE;

    // ── 07b 상세 — STL-2610-002 조정 협의 · 사업자 ─────────────────────────────

    private static final String B_ADJUSTMENT = """
              "adjustment": {
                "adjustmentId": 4, "threadId": 812, "status": "OPEN", "statusLabel": "협의 중", "requesterType": "SELLER",
                "openedAt": "2026-10-05T15:20:00", "deadlineAt": "2026-10-20T23:59:59", "remainingBusinessDays": 7,
                "originalRewardAmount": 294000, "maxRewardAmount": 1728360, "agreedRewardAmount": null, "finalRewardAmount": null,
                "closedAt": null,
                "proposals": [
                  {
                    "proposalId": 7, "seq": 1, "proposerType": "SELLER", "proposerName": "데일리랩", "proposedAt": "2026-10-05T15:20:00",
                    "rewardAmount": 270000, "reason": "사전 제공 샘플 20개 중 12개 미도착(반송) — 샘플분 리워드 차감 요청",
                    "status": "COUNTERED", "statusLabel": "다른 금액 제안으로 응답됨", "respondedAt": "2026-10-07T11:05:00",
                    "preview": {"creatorNetAmount": 297000, "brandPayoutAmount": 1604200}
                  },
                  {
                    "proposalId": 9, "seq": 2, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "proposedAt": "2026-10-07T11:05:00",
                    "rewardAmount": 282000, "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다",
                    "status": "PENDING", "statusLabel": "응답 대기", "respondedAt": null,
                    "preview": {"creatorNetAmount": 310200, "brandPayoutAmount": 1591000}
                  }
                ]
              }\
            """;

    private static final String B_RAIL = """
              "rail": {
                "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                "confirmedSalesAmount": 1960000, "brandPayoutAmount": 1577800, "creatorPayoutAmount": 323400,
                "reviewDueAt": "2026-10-06T23:59:59", "deadlineAt": "2026-10-20T23:59:59",
                "confirmedAt": null, "confirmReason": null, "confirmReasonLabel": null,
                "payoutDueDate": null, "payoutBasis": null, "paidAt": null, "blockReasons": [], "failedPayout": null
              },
            """;

    static final String DETAIL_ADJUSTING = """
            {
              "settlementId": 29, "settlementNumber": "STL-2610-002",
              "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
              "overview": {
                "groupBuyId": 55, "groupBuyNumber": "GB-20260902-055", "groupBuyTitle": "가을 립 틴트 공구", "contractId": 61,
                "marketId": 17, "brandName": "데일리랩", "creatorId": 31, "showroomName": "민지의 뷰티룸", "creatorBusinessType": "BUSINESS",
                "periodStartAt": "2026-09-15T00:00:00", "periodEndAt": "2026-09-21T23:59:59",
                "ordersClosedAt": "2026-10-01T09:48:00", "createdAt": "2026-10-01T10:00:00", "orderCount": 68, "closedCount": 68
              },
              "stage": {"current": "ADJUSTMENT", "adjustmentSkipped": false},
              "breakdown": {
                "brand": {
                  "grossOrderAmount": 2040000, "cancel": {"amount": 52000, "count": 2}, "return": {"amount": 28000, "count": 1},
                  "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 1960000,
                  "pgFee": {"rate": 0.0300, "amount": 58800}, "platformFee": {"rate": 0.0000, "amount": 0},
                  "originalRewardAmount": 294000, "rewardAmount": 294000, "rewardVat": {"rate": 0.1000, "amount": 29400},
                  "reshipFee": {"amount": 0, "count": 0}, "consumerDeliveryFee": 0,
                  "payoutBeforeClawback": 1577800, "clawbackAmount": 0, "payoutAmount": 1577800
                },
                "creator": {
                  "businessType": "BUSINESS", "rewardAmount": 294000, "clawbackAmount": 0, "withholding": null,
                  "vatAmount": 29400, "payoutAmount": 323400
                },
                "platformShareAmount": 0
              },
              "clawbacksApplied": [],
            """ + B_ADJUSTMENT + """
            ,
              "payouts": null,
              "taxDocuments": [],
              "fixedFee": {"contractId": 61, "amount": null, "trigger": null, "triggerLabel": null},
              "items": {
                "total": 74, "returnCount": 1, "exchangeCount": 0,
                "rows": [
                  {
                    "itemId": 3120, "orderId": 6702, "deliveryGroupId": 2611, "orderNumber": "20260921-000412", "subOrderNumber": "20260921-000412-01",
                    "consumerNameMasked": "한*솔", "productName": "벨벳 립 틴트", "optionName": "03 로즈",
                    "quantity": 2, "returnedQuantity": 0, "settledQuantity": 2, "unitPrice": 14000, "paidAmount": 28000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 28000, "rewardRate": 15.0, "rewardAmount": 4200
                  },
                  {
                    "itemId": 3118, "orderId": 6695, "deliveryGroupId": 2604, "orderNumber": "20260921-000398", "subOrderNumber": "20260921-000398-01",
                    "consumerNameMasked": "윤*아", "productName": "벨벳 립 틴트", "optionName": "01 코랄",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 14000, "paidAmount": 14000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 14000, "rewardRate": 15.0, "rewardAmount": 2100
                  },
                  {
                    "itemId": 3115, "orderId": 6681, "deliveryGroupId": 2590, "orderNumber": "20260920-000377", "subOrderNumber": "20260920-000377-01",
                    "consumerNameMasked": "서*진", "productName": "벨벳 립 틴트", "optionName": "05 버건디",
                    "quantity": 2, "returnedQuantity": 2, "settledQuantity": 0, "unitPrice": 14000, "paidAmount": 28000,
                    "status": "RETURNED", "statusLabel": "반품", "settledAmount": 0, "rewardRate": 15.0, "rewardAmount": 0
                  },
                  {
                    "itemId": 3111, "orderId": 6670, "deliveryGroupId": 2579, "orderNumber": "20260920-000351", "subOrderNumber": "20260920-000351-01",
                    "consumerNameMasked": "조*희", "productName": "벨벳 립 틴트", "optionName": "03 로즈",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 14000, "paidAmount": 14000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 14000, "rewardRate": 15.0, "rewardAmount": 2100
                  },
                  {
                    "itemId": 3109, "orderId": 6662, "deliveryGroupId": 2571, "orderNumber": "20260919-000340", "subOrderNumber": "20260919-000340-01",
                    "consumerNameMasked": "강*현", "productName": "벨벳 립 틴트", "optionName": "02 누드",
                    "quantity": 3, "returnedQuantity": 0, "settledQuantity": 3, "unitPrice": 14000, "paidAmount": 42000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 42000, "rewardRate": 15.0, "rewardAmount": 6300
                  }
                ],
                "footer": {"paidAmount": 2040000, "settledAmount": 1960000, "itemRewardTotal": 294000, "rewardAmount": 294000}
              },
            """ + B_RAIL + """
              "actions": {"canRedistribute": false, "canVerifyInvoice": false, "canRegisterBrandInvoice": false, "canDownloadStatement": false},
              "history": [
                {"eventType": "ADJUSTMENT_COUNTERED", "label": "다른 금액 제안", "actorType": "CREATOR", "actorLabel": "민지의 뷰티룸",
                 "detail": "인플루언서 다른 금액 제안 · 리워드 282,000원", "occurredAt": "2026-10-07T11:05:00"},
                {"eventType": "ADJUSTMENT_REQUESTED", "label": "정산 조정 요청", "actorType": "SELLER", "actorLabel": "데일리랩",
                 "detail": "조정 요청 접수 · 전액 보류", "occurredAt": "2026-10-05T15:20:00"},
                {"eventType": "CREATED", "label": "정산 생성 · 금액 공개", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "공구 종료 · 주문 68건 종결 · 금액 공개 · 확인 기간 10.06 23:59까지", "occurredAt": "2026-10-01T10:00:00"}
              ]
            }
            """;

    /** 20b 이슈 패널 — 상세의 rail + adjustment. */
    static final String BY_THREAD = """
            {
              "settlementId": 29, "settlementNumber": "STL-2610-002",
            """ + B_RAIL + B_ADJUSTMENT + """

            }
            """;

    // ── 07b 상세 — STL-2609-004 분배 실패 · 재분배 ─────────────────────────────

    private static final String C_TOP_FAILED = """
            {
              "settlementId": 24, "settlementNumber": "STL-2609-004",
              "status": "PAYOUT_FAILED", "statusLabel": "분배 실패", "statusTone": "DANGER",
            """;

    private static final String C_TOP_RETRIED = """
            {
              "settlementId": 24, "settlementNumber": "STL-2609-004",
              "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
            """;

    private static final String C_BODY = """
              "overview": {
                "groupBuyId": 38, "groupBuyNumber": "GB-20260820-038", "groupBuyTitle": "산뜻 선크림 공구", "contractId": 47,
                "marketId": 25, "brandName": "벨라코스", "creatorId": 19, "showroomName": "하늘의 화장대", "creatorBusinessType": "INDIVIDUAL",
                "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                "ordersClosedAt": "2026-09-29T13:41:00", "createdAt": "2026-09-29T14:00:00", "orderCount": 39, "closedCount": 39
              },
              "stage": {"current": "CONFIRMED", "adjustmentSkipped": true},
              "breakdown": {
                "brand": {
                  "grossOrderAmount": 1280000, "cancel": {"amount": 0, "count": 0}, "return": {"amount": 0, "count": 0},
                  "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 1280000,
                  "pgFee": {"rate": 0.0300, "amount": 38400}, "platformFee": {"rate": 0.0000, "amount": 0},
                  "originalRewardAmount": 128000, "rewardAmount": 128000, "rewardVat": {"rate": 0.1000, "amount": 12800},
                  "reshipFee": {"amount": 0, "count": 0}, "consumerDeliveryFee": 0,
                  "payoutBeforeClawback": 1100800, "clawbackAmount": 0, "payoutAmount": 1100800
                },
                "creator": {
                  "businessType": "INDIVIDUAL", "rewardAmount": 128000, "clawbackAmount": 0,
                  "withholding": {"amount": 4224, "incomeRate": 0.0300, "localRate": 0.0030}, "vatAmount": 0, "payoutAmount": 123776
                },
                "platformShareAmount": 12800
              },
              "clawbacksApplied": [],
              "adjustment": null,
            """;

    private static final String C_PAYOUTS_OTHERS = """
                  {
                    "payoutId": 71, "payee": "CREATOR", "payeeLabel": "인플루언서", "payeeName": "하늘의 화장대", "amount": 123776,
                    "bankName": "토스뱅크", "accountNumber": "1000-2345-6789", "accountHolder": "정하늘", "accountSource": "SNAPSHOT",
                    "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS", "dueDate": "2026-10-07",
                    "requestedAt": "2026-10-07T10:00:05", "paidAt": "2026-10-07T10:00:12", "failedAt": null, "failCode": null, "failReason": null,
                    "pgReference": "TRF-20261007-58213", "attempt": 0
                  },
                  {
                    "payoutId": 72, "payee": "PLATFORM", "payeeLabel": "플랫폼", "payeeName": "SHOWROOMZ", "amount": 12800,
                    "bankName": null, "accountNumber": null, "accountHolder": "SHOWROOMZ", "accountSource": "NONE",
                    "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS", "dueDate": "2026-10-07",
                    "requestedAt": "2026-10-07T10:00:05", "paidAt": "2026-10-07T10:00:12", "failedAt": null, "failCode": null, "failReason": null,
                    "pgReference": "TRF-20261007-58214", "attempt": 0
                  }
                ],
                "pgFeeAmount": 38400,
                "check": {"total": 1280000, "inflowAmount": 1280000, "confirmedSalesAmount": 1280000, "withholdingAmount": 4224, "balanced": true}
              },
            """;

    private static final String C_PAYOUTS_FAILED = """
              "payouts": {
                "rows": [
                  {
                    "payoutId": 70, "payee": "BRAND", "payeeLabel": "브랜드", "payeeName": "벨라코스", "amount": 1100800,
                    "bankName": "신한은행", "accountNumber": "110-482-337120", "accountHolder": "벨라코스", "accountSource": "SNAPSHOT",
                    "status": "FAILED", "statusLabel": "분배 실패", "statusTone": "DANGER", "dueDate": "2026-10-07",
                    "requestedAt": "2026-10-07T10:00:05", "paidAt": null, "failedAt": "2026-10-07T10:00:12",
                    "failCode": "ACCOUNT_HOLDER_MISMATCH", "failReason": "예금주 불일치", "pgReference": null, "attempt": 0
                  },
            """ + C_PAYOUTS_OTHERS;

    /** 재분배 직후 — 즉시 지시가 나가 REQUESTED · 실패 기록(failedAt · failCode)은 지우지 않는다 · 회차 1. */
    private static final String C_PAYOUTS_RETRIED = """
              "payouts": {
                "rows": [
                  {
                    "payoutId": 70, "payee": "BRAND", "payeeLabel": "브랜드", "payeeName": "벨라코스", "amount": 1100800,
                    "bankName": "신한은행", "accountNumber": "110-482-337120", "accountHolder": "벨라코스 주식회사", "accountSource": "SNAPSHOT",
                    "status": "REQUESTED", "statusLabel": "지급 처리 중", "statusTone": "INFO", "dueDate": "2026-10-10",
                    "requestedAt": "2026-10-10T11:00:01", "paidAt": null, "failedAt": "2026-10-07T10:00:12",
                    "failCode": "ACCOUNT_HOLDER_MISMATCH", "failReason": "예금주 불일치", "pgReference": null, "attempt": 1
                  },
            """ + C_PAYOUTS_OTHERS;

    private static final String C_MID = """
              "taxDocuments": [
                {
                  "documentId": 61, "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서", "direction": "플랫폼 → 브랜드",
                  "counterpartyName": "벨라코스 주식회사", "supplyAmount": 128000, "vatAmount": 12800, "totalAmount": 140800,
                  "status": "PENDING_ISSUE", "statusLabel": "발행 대기", "approvalNumber": null, "issuedDate": null, "dueDate": "2026-11-10",
                  "submittedAt": null, "verifiedAt": null, "rejectReason": null, "rejectReasonLabel": null, "fileName": null,
                  "actions": {"canVerify": false, "canRegister": true}
                },
                {
                  "documentId": 65, "type": "WITHHOLDING_RECEIPT", "typeLabel": "원천징수영수증", "direction": "플랫폼 → 인플루언서",
                  "counterpartyName": "하늘의 화장대", "supplyAmount": 128000, "vatAmount": 0, "totalAmount": 128000,
                  "status": "GENERATED", "statusLabel": "생성 완료", "approvalNumber": null, "issuedDate": "2026-10-07", "dueDate": null,
                  "submittedAt": null, "verifiedAt": null, "rejectReason": null, "rejectReasonLabel": null,
                  "fileName": "원천징수영수증_STL-2609-004.pdf",
                  "actions": {"canVerify": false, "canRegister": false}
                }
              ],
              "fixedFee": {"contractId": 47, "amount": 200000, "trigger": "GROUP_BUY_ENDED", "triggerLabel": "공구 종료 후"},
              "items": {
                "total": 39, "returnCount": 0, "exchangeCount": 0,
                "rows": [
                  {
                    "itemId": 1880, "orderId": 5980, "deliveryGroupId": 2301, "orderNumber": "20260914-000502", "subOrderNumber": "20260914-000502-01",
                    "consumerNameMasked": "문*영", "productName": "산뜻 선크림 50ml", "optionName": null,
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 32000, "paidAmount": 32000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 32000, "rewardRate": 10.0, "rewardAmount": 3200
                  },
                  {
                    "itemId": 1879, "orderId": 5977, "deliveryGroupId": 2298, "orderNumber": "20260914-000488", "subOrderNumber": "20260914-000488-01",
                    "consumerNameMasked": "배*린", "productName": "산뜻 선크림 50ml", "optionName": null,
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 32000, "paidAmount": 32000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 32000, "rewardRate": 10.0, "rewardAmount": 3200
                  },
                  {
                    "itemId": 1876, "orderId": 5961, "deliveryGroupId": 2282, "orderNumber": "20260913-000431", "subOrderNumber": "20260913-000431-01",
                    "consumerNameMasked": "송*호", "productName": "산뜻 선크림 50ml", "optionName": null,
                    "quantity": 2, "returnedQuantity": 0, "settledQuantity": 2, "unitPrice": 32000, "paidAmount": 64000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 64000, "rewardRate": 10.0, "rewardAmount": 6400
                  },
                  {
                    "itemId": 1874, "orderId": 5950, "deliveryGroupId": 2271, "orderNumber": "20260913-000415", "subOrderNumber": "20260913-000415-01",
                    "consumerNameMasked": "임*경", "productName": "산뜻 선크림 50ml", "optionName": null,
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 32000, "paidAmount": 32000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 32000, "rewardRate": 10.0, "rewardAmount": 3200
                  },
                  {
                    "itemId": 1871, "orderId": 5932, "deliveryGroupId": 2253, "orderNumber": "20260912-000377", "subOrderNumber": "20260912-000377-01",
                    "consumerNameMasked": "노*진", "productName": "산뜻 선크림 50ml", "optionName": null,
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 32000, "paidAmount": 32000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 32000, "rewardRate": 10.0, "rewardAmount": 3200
                  }
                ],
                "footer": {"paidAmount": 1280000, "settledAmount": 1280000, "itemRewardTotal": 128000, "rewardAmount": 128000}
              },
            """;

    private static final String C_RAIL_FAILED = """
              "rail": {
                "status": "PAYOUT_FAILED", "statusLabel": "분배 실패", "statusTone": "DANGER",
                "confirmedSalesAmount": 1280000, "brandPayoutAmount": 1100800, "creatorPayoutAmount": 123776,
                "reviewDueAt": "2026-10-02T23:59:59", "deadlineAt": null,
                "confirmedAt": "2026-10-03T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-07", "payoutBasis": "자동 확정 10.03 + 3영업일", "paidAt": null, "blockReasons": [],
                "failedPayout": {
                  "payoutId": 70, "payee": "BRAND", "payeeLabel": "브랜드", "amount": 1100800, "failedAt": "2026-10-07T10:00:12",
                  "failCode": "ACCOUNT_HOLDER_MISMATCH", "failReason": "예금주 불일치", "attempt": 0, "retryLimit": 3
                }
              },
              "actions": {"canRedistribute": true, "canVerifyInvoice": false, "canRegisterBrandInvoice": true, "canDownloadStatement": true},
            """;

    private static final String C_RAIL_RETRIED = """
              "rail": {
                "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                "confirmedSalesAmount": 1280000, "brandPayoutAmount": 1100800, "creatorPayoutAmount": 123776,
                "reviewDueAt": "2026-10-02T23:59:59", "deadlineAt": null,
                "confirmedAt": "2026-10-03T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-07", "payoutBasis": "자동 확정 10.03 + 3영업일", "paidAt": null, "blockReasons": [],
                "failedPayout": null
              },
              "actions": {"canRedistribute": false, "canVerifyInvoice": false, "canRegisterBrandInvoice": true, "canDownloadStatement": true},
            """;

    private static final String C_HISTORY_RETRIED = """
                {"eventType": "PAYOUT_REQUESTED", "label": "지급 지시", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "브랜드 · 1,100,800원 지급 지시", "occurredAt": "2026-10-10T11:00:01"},
                {"eventType": "PAYOUT_RETRIED", "label": "재분배", "actorType": "ADMIN", "actorLabel": "김운영",
                 "detail": "브랜드 · 1회차 · 회원 정보 현재 계좌 · 확정 금액 그대로", "occurredAt": "2026-10-10T11:00:00"},
            """;

    private static final String C_HISTORY = """
                {"eventType": "WITHHOLDING_RECEIPT_GENERATED", "label": "원천징수영수증 생성", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "원천징수영수증 생성", "occurredAt": "2026-10-07T10:00:20"},
                {"eventType": "PAYOUT_FAILED", "label": "분배 실패", "actorType": "PG", "actorLabel": "PG",
                 "detail": "브랜드 · 예금주 불일치", "occurredAt": "2026-10-07T10:00:12"},
                {"eventType": "PAYOUT_PAID", "label": "지급 완료", "actorType": "PG", "actorLabel": "PG",
                 "detail": "플랫폼 · 12,800원 · TRF-20261007-58214", "occurredAt": "2026-10-07T10:00:12"},
                {"eventType": "PAYOUT_PAID", "label": "지급 완료", "actorType": "PG", "actorLabel": "PG",
                 "detail": "인플루언서 · 123,776원 · TRF-20261007-58213", "occurredAt": "2026-10-07T10:00:12"},
                {"eventType": "PAYOUT_REQUESTED", "label": "지급 지시", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "플랫폼 · 12,800원 지급 지시", "occurredAt": "2026-10-07T10:00:05"},
                {"eventType": "PAYOUT_REQUESTED", "label": "지급 지시", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "인플루언서 · 123,776원 지급 지시", "occurredAt": "2026-10-07T10:00:05"},
                {"eventType": "PAYOUT_REQUESTED", "label": "지급 지시", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "브랜드 · 1,100,800원 지급 지시", "occurredAt": "2026-10-07T10:00:05"},
                {"eventType": "TAX_INVOICE_REQUESTED", "label": "세금계산서 발행 요청", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "브랜드 세금계산서 발행 대기 · 기한 11.10", "occurredAt": "2026-10-03T00:00:00"},
                {"eventType": "AUTO_CONFIRMED", "label": "자동 확정", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "자동 확정 10.03 + 3영업일", "occurredAt": "2026-10-03T00:00:00"},
                {"eventType": "CREATED", "label": "정산 생성 · 금액 공개", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "공구 종료 · 주문 39건 종결 · 금액 공개 · 확인 기간 10.02 23:59까지", "occurredAt": "2026-09-29T14:00:00"}
            """;

    static final String DETAIL_PAYOUT_FAILED = C_TOP_FAILED + C_BODY + C_PAYOUTS_FAILED + C_MID + C_RAIL_FAILED
            + HISTORY_OPEN + C_HISTORY + DETAIL_CLOSE;

    static final String DETAIL_REDISTRIBUTED = C_TOP_RETRIED + C_BODY + C_PAYOUTS_RETRIED + C_MID + C_RAIL_RETRIED
            + HISTORY_OPEN + C_HISTORY_RETRIED + C_HISTORY + DETAIL_CLOSE;

    // ── 07b 상세 — STL-2609-005 사업자 · 인플루언서 세금계산서 대조 ─────────────────

    private static final String E_HEAD = """
            {
              "settlementId": 25, "settlementNumber": "STL-2609-005",
              "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
              "overview": {
                "groupBuyId": 39, "groupBuyNumber": "GB-20260822-039", "groupBuyTitle": "가을 쿠션 팩트 공구", "contractId": 48,
                "marketId": 17, "brandName": "데일리랩", "creatorId": 27, "showroomName": "지우_스타일", "creatorBusinessType": "BUSINESS",
                "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                "ordersClosedAt": "2026-09-29T14:46:00", "createdAt": "2026-09-29T15:00:00", "orderCount": 138, "closedCount": 138
              },
              "stage": {"current": "CONFIRMED", "adjustmentSkipped": true},
              "breakdown": {
                "brand": {
                  "grossOrderAmount": 5520000, "cancel": {"amount": 40000, "count": 1}, "return": {"amount": 0, "count": 0},
                  "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 5480000,
                  "pgFee": {"rate": 0.0300, "amount": 164400}, "platformFee": {"rate": 0.0000, "amount": 0},
                  "originalRewardAmount": 548000, "rewardAmount": 548000, "rewardVat": {"rate": 0.1000, "amount": 54800},
                  "reshipFee": {"amount": 0, "count": 0}, "consumerDeliveryFee": 0,
                  "payoutBeforeClawback": 4712800, "clawbackAmount": 0, "payoutAmount": 4712800
                },
                "creator": {
                  "businessType": "BUSINESS", "rewardAmount": 548000, "clawbackAmount": 0, "withholding": null,
                  "vatAmount": 54800, "payoutAmount": 602800
                },
                "platformShareAmount": 0
              },
              "clawbacksApplied": [],
              "adjustment": null,
              "payouts": {
                "rows": [
                  {
                    "payoutId": 73, "payee": "BRAND", "payeeLabel": "브랜드", "payeeName": "데일리랩", "amount": 4712800,
                    "bankName": "국민은행", "accountNumber": "123401-04-567890", "accountHolder": "주식회사 데일리랩", "accountSource": "SNAPSHOT",
                    "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS", "dueDate": "2026-10-07",
                    "requestedAt": "2026-10-07T10:00:05", "paidAt": "2026-10-07T10:00:12", "failedAt": null, "failCode": null, "failReason": null,
                    "pgReference": "TRF-20261007-58209", "attempt": 0
                  },
            """;

    private static final String E_CREATOR_BLOCKED = """
                  {
                    "payoutId": 74, "payee": "CREATOR", "payeeLabel": "인플루언서", "payeeName": "지우_스타일", "amount": 602800,
                    "bankName": "카카오뱅크", "accountNumber": "3333-01-2345678", "accountHolder": "김지우", "accountSource": "CURRENT_PROFILE",
                    "status": "BLOCKED", "statusLabel": "지급 보류", "statusTone": "WARNING", "dueDate": null,
                    "requestedAt": null, "paidAt": null, "failedAt": null, "failCode": null, "failReason": null, "pgReference": null, "attempt": 0
                  },
            """;

    private static final String E_CREATOR_RELEASED = """
                  {
                    "payoutId": 74, "payee": "CREATOR", "payeeLabel": "인플루언서", "payeeName": "지우_스타일", "amount": 602800,
                    "bankName": "카카오뱅크", "accountNumber": "3333-01-2345678", "accountHolder": "김지우", "accountSource": "CURRENT_PROFILE",
                    "status": "SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL", "dueDate": "2026-10-14",
                    "requestedAt": null, "paidAt": null, "failedAt": null, "failCode": null, "failReason": null, "pgReference": null, "attempt": 0
                  },
            """;

    private static final String E_PAYOUTS_TAIL = """
                  {
                    "payoutId": 75, "payee": "PLATFORM", "payeeLabel": "플랫폼", "payeeName": "SHOWROOMZ", "amount": 0,
                    "bankName": null, "accountNumber": null, "accountHolder": "SHOWROOMZ", "accountSource": "NONE",
                    "status": "NOT_APPLICABLE", "statusLabel": "—", "statusTone": "NEUTRAL", "dueDate": null,
                    "requestedAt": null, "paidAt": null, "failedAt": null, "failCode": null, "failReason": null, "pgReference": null, "attempt": 0
                  }
                ],
                "pgFeeAmount": 164400,
                "check": {"total": 5480000, "inflowAmount": 5480000, "confirmedSalesAmount": 5480000, "withholdingAmount": 0, "balanced": true}
              },
              "taxDocuments": [
                {
                  "documentId": 62, "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서", "direction": "플랫폼 → 브랜드",
                  "counterpartyName": "주식회사 데일리랩", "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "ISSUED", "statusLabel": "발행 완료", "approvalNumber": "20261008-********-****1234",
                  "issuedDate": "2026-10-08", "dueDate": "2026-11-10", "submittedAt": null, "verifiedAt": null,
                  "rejectReason": null, "rejectReasonLabel": null, "fileName": "세금계산서_STL-2609-005.pdf",
                  "actions": {"canVerify": false, "canRegister": false}
                },
            """;

    private static final String E_INVOICE_SUBMITTED = """
                {
                  "documentId": 63, "type": "CREATOR_TAX_INVOICE", "typeLabel": "인플루언서 세금계산서", "direction": "인플루언서 → 플랫폼",
                  "counterpartyName": "김지우", "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "SUBMITTED", "statusLabel": "확인 대기", "approvalNumber": "20261008-********-****5920",
                  "issuedDate": null, "dueDate": null, "submittedAt": "2026-10-08T16:40:00", "verifiedAt": null,
                  "rejectReason": null, "rejectReasonLabel": null, "fileName": "세금계산서_지우스타일_0929.pdf",
                  "actions": {"canVerify": true, "canRegister": false}
                }
              ],
            """;

    private static final String E_INVOICE_VERIFIED = """
                {
                  "documentId": 63, "type": "CREATOR_TAX_INVOICE", "typeLabel": "인플루언서 세금계산서", "direction": "인플루언서 → 플랫폼",
                  "counterpartyName": "김지우", "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "VERIFIED", "statusLabel": "확인 완료", "approvalNumber": "20261008-********-****5920",
                  "issuedDate": null, "dueDate": null, "submittedAt": "2026-10-08T16:40:00", "verifiedAt": "2026-10-10T11:00:00",
                  "rejectReason": null, "rejectReasonLabel": null, "fileName": "세금계산서_지우스타일_0929.pdf",
                  "actions": {"canVerify": false, "canRegister": false}
                }
              ],
            """;

    private static final String E_INVOICE_REJECTED = """
                {
                  "documentId": 63, "type": "CREATOR_TAX_INVOICE", "typeLabel": "인플루언서 세금계산서", "direction": "인플루언서 → 플랫폼",
                  "counterpartyName": "김지우", "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "REJECTED", "statusLabel": "반려", "approvalNumber": "20261008-********-****5920",
                  "issuedDate": null, "dueDate": null, "submittedAt": "2026-10-08T16:40:00", "verifiedAt": null,
                  "rejectReason": "AMOUNT_MISMATCH", "rejectReasonLabel": "금액 불일치", "fileName": "세금계산서_지우스타일_0929.pdf",
                  "actions": {"canVerify": false, "canRegister": false}
                }
              ],
            """;

    private static final String E_MID = """
              "fixedFee": {"contractId": 48, "amount": 500000, "trigger": "SETTLEMENT_COMPLETED", "triggerLabel": "정산 완료 후"},
              "items": {
                "total": 138, "returnCount": 0, "exchangeCount": 0,
                "rows": [
                  {
                    "itemId": 1990, "orderId": 6011, "deliveryGroupId": 2342, "orderNumber": "20260914-000611", "subOrderNumber": "20260914-000611-01",
                    "consumerNameMasked": "오*린", "productName": "커버 쿠션 팩트 15g", "optionName": "21호 아이보리",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 40000, "paidAmount": 40000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000
                  },
                  {
                    "itemId": 1988, "orderId": 6004, "deliveryGroupId": 2335, "orderNumber": "20260914-000590", "subOrderNumber": "20260914-000590-01",
                    "consumerNameMasked": "유*빈", "productName": "커버 쿠션 팩트 15g", "optionName": "23호 베이지",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 0, "unitPrice": 40000, "paidAmount": 40000,
                    "status": "CANCELLED", "statusLabel": "취소", "settledAmount": 0, "rewardRate": 10.0, "rewardAmount": 0
                  },
                  {
                    "itemId": 1985, "orderId": 5996, "deliveryGroupId": 2327, "orderNumber": "20260913-000574", "subOrderNumber": "20260913-000574-01",
                    "consumerNameMasked": "황*수", "productName": "커버 쿠션 팩트 15g", "optionName": "21호 아이보리",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 40000, "paidAmount": 40000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000
                  },
                  {
                    "itemId": 1983, "orderId": 5989, "deliveryGroupId": 2320, "orderNumber": "20260913-000552", "subOrderNumber": "20260913-000552-01",
                    "consumerNameMasked": "권*나", "productName": "커버 쿠션 팩트 15g", "optionName": "23호 베이지",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 40000, "paidAmount": 40000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000
                  },
                  {
                    "itemId": 1980, "orderId": 5975, "deliveryGroupId": 2306, "orderNumber": "20260912-000530", "subOrderNumber": "20260912-000530-01",
                    "consumerNameMasked": "남*우", "productName": "커버 쿠션 팩트 15g", "optionName": "21호 아이보리",
                    "quantity": 1, "returnedQuantity": 0, "settledQuantity": 1, "unitPrice": 40000, "paidAmount": 40000,
                    "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000
                  }
                ],
                "footer": {"paidAmount": 5520000, "settledAmount": 5480000, "itemRewardTotal": 548000, "rewardAmount": 548000}
              },
            """;

    private static final String E_RAIL_BLOCKED = """
              "rail": {
                "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                "confirmedSalesAmount": 5480000, "brandPayoutAmount": 4712800, "creatorPayoutAmount": 602800,
                "reviewDueAt": "2026-10-02T23:59:59", "deadlineAt": null,
                "confirmedAt": "2026-10-03T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-07", "payoutBasis": "자동 확정 10.03 + 3영업일", "paidAt": null,
                "blockReasons": [{"code": "TAX_INVOICE_UNVERIFIED", "label": "세금계산서 확인 전"}], "failedPayout": null
              },
            """;

    private static final String E_RAIL_RELEASED = """
              "rail": {
                "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                "confirmedSalesAmount": 5480000, "brandPayoutAmount": 4712800, "creatorPayoutAmount": 602800,
                "reviewDueAt": "2026-10-02T23:59:59", "deadlineAt": null,
                "confirmedAt": "2026-10-03T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-07", "payoutBasis": "자동 확정 10.03 + 3영업일", "paidAt": null,
                "blockReasons": [], "failedPayout": null
              },
            """;

    private static final String E_ACTIONS_VERIFY = """
              "actions": {"canRedistribute": false, "canVerifyInvoice": true, "canRegisterBrandInvoice": false, "canDownloadStatement": true},
            """;

    private static final String E_ACTIONS_DONE = """
              "actions": {"canRedistribute": false, "canVerifyInvoice": false, "canRegisterBrandInvoice": false, "canDownloadStatement": true},
            """;

    private static final String E_HISTORY_VERIFIED = """
                {"eventType": "TAX_INVOICE_VERIFIED", "label": "세금계산서 확인", "actorType": "ADMIN", "actorLabel": "김운영",
                 "detail": "승인번호 확인 · 인플루언서 몫 지급 예정 10.14", "occurredAt": "2026-10-10T11:00:00"},
            """;

    private static final String E_HISTORY_REJECTED = """
                {"eventType": "TAX_INVOICE_REJECTED", "label": "세금계산서 반려", "actorType": "ADMIN", "actorLabel": "김운영",
                 "detail": "반려 · 금액 불일치", "occurredAt": "2026-10-10T11:00:00"},
            """;

    private static final String E_HISTORY = """
                {"eventType": "TAX_INVOICE_SUBMITTED", "label": "세금계산서 승인번호 입력", "actorType": "CREATOR", "actorLabel": "지우_스타일",
                 "detail": "승인번호 입력 · 대조 대기", "occurredAt": "2026-10-08T16:40:00"},
                {"eventType": "BRAND_INVOICE_ISSUED", "label": "브랜드 세금계산서 발행본 등록", "actorType": "ADMIN", "actorLabel": "김운영",
                 "detail": "브랜드 세금계산서 발행본 등록 · 발행일 2026-10-08", "occurredAt": "2026-10-08T14:10:00"},
                {"eventType": "PAYOUT_PAID", "label": "지급 완료", "actorType": "PG", "actorLabel": "PG",
                 "detail": "브랜드 · 4,712,800원 · TRF-20261007-58209", "occurredAt": "2026-10-07T10:00:12"},
                {"eventType": "PAYOUT_REQUESTED", "label": "지급 지시", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "브랜드 · 4,712,800원 지급 지시", "occurredAt": "2026-10-07T10:00:05"},
                {"eventType": "TAX_INVOICE_REQUESTED", "label": "세금계산서 발행 요청", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "브랜드 세금계산서 발행 대기 · 기한 11.10 · 인플루언서 세금계산서 입력 대기", "occurredAt": "2026-10-03T00:00:00"},
                {"eventType": "AUTO_CONFIRMED", "label": "자동 확정", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "자동 확정 10.03 + 3영업일", "occurredAt": "2026-10-03T00:00:00"},
                {"eventType": "CREATED", "label": "정산 생성 · 금액 공개", "actorType": "SYSTEM", "actorLabel": "시스템",
                 "detail": "공구 종료 · 주문 138건 종결 · 금액 공개 · 확인 기간 10.02 23:59까지", "occurredAt": "2026-09-29T15:00:00"}
            """;

    static final String DETAIL_INVOICE_SUBMITTED = E_HEAD + E_CREATOR_BLOCKED + E_PAYOUTS_TAIL + E_INVOICE_SUBMITTED
            + E_MID + E_RAIL_BLOCKED + E_ACTIONS_VERIFY + HISTORY_OPEN + E_HISTORY + DETAIL_CLOSE;

    /** M4 MATCH — 인플루언서 몫 보류 해제 · 지급 예정일 = 확인일(10.10) + 3영업일 = 10.14. */
    static final String DETAIL_INVOICE_VERIFIED = E_HEAD + E_CREATOR_RELEASED + E_PAYOUTS_TAIL + E_INVOICE_VERIFIED
            + E_MID + E_RAIL_RELEASED + E_ACTIONS_DONE + HISTORY_OPEN + E_HISTORY_VERIFIED + E_HISTORY + DETAIL_CLOSE;

    /** M4 반려 — 보류 유지 · 인플루언서가 같은 행에 다시 입력한다. */
    static final String DETAIL_INVOICE_REJECTED = E_HEAD + E_CREATOR_BLOCKED + E_PAYOUTS_TAIL + E_INVOICE_REJECTED
            + E_MID + E_RAIL_BLOCKED + E_ACTIONS_DONE + HISTORY_OPEN + E_HISTORY_REJECTED + E_HISTORY + DETAIL_CLOSE;

    // ── 07b 명세 ─────────────────────────────────────────────────────────

    static final String ITEMS = """
            {
              "content": [
            """ + A_ITEM_ROWS + """
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 31, "totalResults": 151, "limit": 5, "hasNext": true}
            }
            """;

    // ── 요청 ──────────────────────────────────────────────────────────────

    static final String REQ_REDISTRIBUTE_CURRENT = """
            {"accountSource": "CURRENT_PROFILE"}
            """;

    static final String REQ_REDISTRIBUTE_PREVIOUS = """
            {"accountSource": "PREVIOUS"}
            """;

    static final String REQ_VERIFY_MATCH = """
            {"result": "MATCH"}
            """;

    static final String REQ_VERIFY_AMOUNT_MISMATCH = """
            {"result": "AMOUNT_MISMATCH"}
            """;

    // ── 오류 ─────────────────────────────────────────────────────────────

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_NOT_NULL = """
            {"code": "INVALID_INPUT", "message": "널이어서는 안됩니다"}
            """;

    static final String ERR_PAGE_SIZE = """
            {"code": "INVALID_INPUT", "message": "size 는 1 ~ 100 이어야 합니다."}
            """;

    static final String ERR_MONTH_REQUIRED = """
            {"code": "INVALID_INPUT", "message": "month(YYYY-MM)가 필요합니다."}
            """;

    static final String ERR_PDF_ONLY = """
            {"code": "INVALID_INPUT", "message": "PDF 파일만 올릴 수 있습니다."}
            """;

    static final String ERR_PDF_SIZE = """
            {"code": "INVALID_INPUT", "message": "PDF 는 10MB 까지 올릴 수 있습니다."}
            """;

    static final String ERR_ISSUE_FILE_REQUIRED = """
            {"code": "INVALID_INPUT", "message": "발행본 파일과 발행일이 필요합니다."}
            """;

    static final String ERR_UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "인증 정보가 유효하지 않습니다."}
            """;

    static final String ERR_FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "접근 권한이 없습니다."}
            """;

    static final String ERR_SETTLEMENT_NOT_FOUND = """
            {"code": "SETTLEMENT_NOT_FOUND", "message": "존재하지 않는 정산입니다."}
            """;

    static final String ERR_ADJUSTMENT_NOT_FOUND = """
            {"code": "SETTLEMENT_ADJUSTMENT_NOT_FOUND", "message": "정산 조정 협의를 찾을 수 없습니다."}
            """;

    static final String ERR_STATEMENT_NOT_READY = """
            {"code": "SETTLEMENT_STATEMENT_NOT_READY", "message": "정산이 확정된 뒤에 명세를 내려받을 수 있습니다."}
            """;

    static final String ERR_ACCOUNT_MISSING = """
            {"code": "SETTLEMENT_ACCOUNT_MISSING", "message": "수취자의 등록 계좌가 없습니다."}
            """;

    static final String ERR_STATE_CHANGED = """
            {"code": "SETTLEMENT_STATE_CHANGED", "message": "정산 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."}
            """;

    static final String ERR_PAYOUT_RETRY_EXCEEDED = """
            {"code": "SETTLEMENT_PAYOUT_RETRY_EXCEEDED", "message": "재분배 횟수를 넘었습니다. 수동 이체 절차로 처리해 주세요."}
            """;

    static final String ERR_TAX_DOCUMENT_STATE_CHANGED = """
            {"code": "SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED", "message": "증빙 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."}
            """;

    static final String ERR_TAX_INVOICE_NUMBER_INVALID = """
            {"code": "SETTLEMENT_TAX_INVOICE_NUMBER_INVALID", "message": "승인번호 24자리를 확인해 주세요 — 숫자와 하이픈(-)만 입력할 수 있어요."}
            """;
}
