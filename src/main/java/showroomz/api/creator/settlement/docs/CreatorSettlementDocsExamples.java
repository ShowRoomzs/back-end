package showroomz.api.creator.settlement.docs;

/**
 * 쇼룸 스튜디오 정산 관리(12) · 정산 조정 협의(11 이슈 스레드) Swagger 예시 JSON. 기준 시각은 2026-10-10 11:00(토). 금액은 44 어드민 설계서
 * 2-4 산식(전 항목 절사 · 원천징수 3% + 0.3% 각각 절사)으로 검산한 값이고 어드민 · 파트너 예시와 같은 정산이다.
 *
 * <p>목록 · 요약 · 정산 확인 중 · 지급 예정 상세의 뷰어는 「소연_쇼룸」(비사업자 · creatorId 7)이다. 조정 협의 상세와 스레드 예시는
 * 「민지의 뷰티룸」(사업자 · 협의 id 4 · 스레드 812), 세금계산서 예시는 「지우_스타일」(사업자 · STL-2609-005)이 뷰어다.
 */
final class CreatorSettlementDocsExamples {

    private CreatorSettlementDocsExamples() {
    }

    // ── 목록 · 요약 ──────────────────────────────────────────────────────

    static final String LIST = """
            {
              "content": [
                {
                  "settlementId": 32, "settlementNumber": "STL-2610-005", "groupBuyId": 58, "groupBuyTitle": "가을 보습 크림 공구",
                  "brandName": "글로우코스", "periodStartAt": "2026-09-21T00:00:00", "periodEndAt": "2026-09-27T23:59:59",
                  "confirmedSalesAmount": 2350000, "rewardRateLabel": "12%", "rewardAmount": 282000, "rewardClawbackAmount": 0,
                  "withholdingAmount": 9306, "creatorVatAmount": 0, "creatorPayoutAmount": 272694,
                  "status": "REVIEWING", "statusLabel": "정산 확인 중", "statusTone": "INFO",
                  "payoutDate": null, "payoutDateKind": null
                },
                {
                  "settlementId": 26, "settlementNumber": "STL-2609-006", "groupBuyId": 41, "groupBuyTitle": "여름 수분 세럼 공구",
                  "brandName": "데일리랩", "periodStartAt": "2026-09-01T00:00:00", "periodEndAt": "2026-09-07T23:59:59",
                  "confirmedSalesAmount": 5480000, "rewardRateLabel": "10%", "rewardAmount": 548000, "rewardClawbackAmount": 0,
                  "withholdingAmount": 18084, "creatorVatAmount": 0, "creatorPayoutAmount": 529916,
                  "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                  "payoutDate": "2026-10-12", "payoutDateKind": "SCHEDULED"
                },
                {
                  "settlementId": 11, "settlementNumber": "STL-2608-011", "groupBuyId": 29, "groupBuyTitle": "수분 앰플 공구",
                  "brandName": "데일리랩", "periodStartAt": "2026-07-27T00:00:00", "periodEndAt": "2026-08-02T23:59:59",
                  "confirmedSalesAmount": 3120000, "rewardRateLabel": "10%", "rewardAmount": 312000, "rewardClawbackAmount": 0,
                  "withholdingAmount": 10296, "creatorVatAmount": 0, "creatorPayoutAmount": 301704,
                  "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS",
                  "payoutDate": "2026-09-02", "payoutDateKind": "PAID"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 2, "totalResults": 5, "limit": 3, "hasNext": true}
            }
            """;

    static final String SUMMARY = """
            {
              "totalPaid": {"amount": 1184210, "count": 3},
              "payoutScheduled": {"amount": 529916, "count": 1, "nearestDate": "2026-10-12"},
              "reviewing": {"count": 1, "nearestDueAt": "2026-10-13T23:59:59"},
              "adjusting": {"count": 0},
              "statusCounts": {"REVIEWING": 1, "ADJUSTING": 0, "PAYOUT_SCHEDULED": 1, "PAID": 3},
              "attentionCount": 1
            }
            """;

    // ── 상세 — 정산 확인 중(STL-2610-005 · 소연_쇼룸) ─────────────────────────

    static final String DETAIL_REVIEWING = """
            {
              "settlement": {
                "settlementId": 32, "settlementNumber": "STL-2610-005", "groupBuyId": 58, "groupBuyTitle": "가을 보습 크림 공구",
                "brandName": "글로우코스", "marketId": 22, "periodStartAt": "2026-09-21T00:00:00", "periodEndAt": "2026-09-27T23:59:59",
                "status": "REVIEWING", "statusLabel": "정산 확인 중", "statusTone": "INFO",
                "creatorBusinessType": "INDIVIDUAL", "taxInvoiceRequired": false
              },
              "timeline": {
                "createdAt": "2026-10-07T10:00:00", "ordersClosedAt": "2026-10-07T09:51:00", "reviewDueAt": "2026-10-13T23:59:59",
                "confirmedAt": null, "confirmReason": null, "confirmReasonLabel": null,
                "payoutDueDate": null, "payoutDueNote": null, "paidAt": null
              },
              "review": {"canRequestAdjustment": true, "requestDeadlineAt": "2026-10-13T23:59:59", "maxRewardAmount": 2072270},
              "breakdown": {
                "grossOrderAmount": 2420500, "cancel": {"amount": 47000, "count": 1}, "return": {"amount": 23500, "count": 1},
                "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 2350000,
                "rewardRateLabel": "12%", "rewardRates": [{"productName": "세라마이드 보습 크림 80ml", "rate": 12.0}],
                "originalRewardAmount": 282000, "rewardAmount": 282000, "rewardClawbackAmount": 0, "rewardAfterClawback": 282000,
                "withholdingIncomeRate": 0.0300, "withholdingLocalRate": 0.0030, "withholdingAmount": 9306,
                "creatorVatRate": 0.1000, "creatorVatAmount": 0, "creatorPayoutAmount": 272694
              },
              "clawbacks": [],
              "payouts": {
                "shownAfterConfirm": true,
                "rows": [
                  {"payee": "CREATOR", "payeeLabel": "인플루언서", "name": "소연_쇼룸", "amount": 272694,
                   "note": "리워드 282,000 − 원천징수 9,306(3.3%)", "status": "WAITING", "statusLabel": "확인 기간 후 지급", "date": null},
                  {"payee": "BRAND", "payeeLabel": "브랜드", "name": "글로우코스", "amount": 1969300,
                   "note": null, "status": "WAITING", "statusLabel": "확인 기간 후 지급", "date": null},
                  {"payee": "PLATFORM", "payeeLabel": "플랫폼", "name": "SHOWROOMZ", "amount": 28200,
                   "note": "플랫폼 신고 · 납부", "status": "WAITING", "statusLabel": "확인 기간 후 지급", "date": null}
                ]
              },
              "payment": {
                "bankName": "카카오뱅크", "accountNumberMasked": "*********234567", "accountHolder": "이소연",
                "dueDate": null, "dueNote": null, "paidAt": null, "pgReference": null
              },
              "adjustment": null,
              "withholding": {"receiptIssuer": "SHOWROOMZ", "amount": 9306, "receiptAvailable": false, "receiptLabel": "징수 예정액"},
              "taxInvoice": null,
              "items": {
                "preview": [
                  {"orderNumber": "20260927-000588", "productName": "세라마이드 보습 크림 80ml", "optionName": null, "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 23500, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 23500, "rewardRate": 12.0, "rewardAmount": 2820},
                  {"orderNumber": "20260927-000561", "productName": "세라마이드 보습 크림 80ml", "optionName": null, "quantity": 2, "settledQuantity": 2,
                   "paidAmount": 47000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 47000, "rewardRate": 12.0, "rewardAmount": 5640},
                  {"orderNumber": "20260926-000530", "productName": "세라마이드 보습 크림 80ml", "optionName": null, "quantity": 1, "settledQuantity": 0,
                   "paidAmount": 23500, "status": "RETURNED", "statusLabel": "반품", "settledAmount": 0, "rewardRate": 12.0, "rewardAmount": 0},
                  {"orderNumber": "20260926-000502", "productName": "세라마이드 보습 크림 80ml", "optionName": null, "quantity": 2, "settledQuantity": 0,
                   "paidAmount": 47000, "status": "CANCELLED", "statusLabel": "취소", "settledAmount": 0, "rewardRate": 12.0, "rewardAmount": 0},
                  {"orderNumber": "20260925-000477", "productName": "세라마이드 보습 크림 80ml", "optionName": null, "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 23500, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 23500, "rewardRate": 12.0, "rewardAmount": 2820}
                ],
                "totalCount": 98,
                "downloadAvailable": false
              }
            }
            """;

    // ── 상세 — 지급 예정(STL-2609-006 · 소연_쇼룸) ───────────────────────────

    /** 명세 행 — 상세 미리보기와 GET …/items 가 같은 행을 쓴다(소비자 열 없음). */
    private static final String A_ITEM_ROWS = """
                  {"orderNumber": "20260907-000318", "productName": "수분 세럼 50ml", "optionName": null, "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 24000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 24000, "rewardRate": 10.0, "rewardAmount": 2400},
                  {"orderNumber": "20260907-000301", "productName": "수분 세럼 50ml", "optionName": "2개 세트", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 46000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 46000, "rewardRate": 10.0, "rewardAmount": 4600},
                  {"orderNumber": "20260906-000287", "productName": "수분 세럼 50ml", "optionName": null, "quantity": 2, "settledQuantity": 1,
                   "paidAmount": 48000, "status": "PARTIAL_RETURNED", "statusLabel": "부분 반품", "settledAmount": 24000, "rewardRate": 10.0, "rewardAmount": 2400},
                  {"orderNumber": "20260906-000254", "productName": "수분 세럼 50ml", "optionName": "2개 세트", "quantity": 1, "settledQuantity": 0,
                   "paidAmount": 46000, "status": "CANCELLED", "statusLabel": "취소", "settledAmount": 0, "rewardRate": 10.0, "rewardAmount": 0},
                  {"orderNumber": "20260905-000233", "productName": "수분 세럼 50ml", "optionName": null, "quantity": 3, "settledQuantity": 3,
                   "paidAmount": 72000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 72000, "rewardRate": 10.0, "rewardAmount": 7200}
            """;

    static final String DETAIL_SCHEDULED = """
            {
              "settlement": {
                "settlementId": 26, "settlementNumber": "STL-2609-006", "groupBuyId": 41, "groupBuyTitle": "여름 수분 세럼 공구",
                "brandName": "데일리랩", "marketId": 17, "periodStartAt": "2026-09-01T00:00:00", "periodEndAt": "2026-09-07T23:59:59",
                "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                "creatorBusinessType": "INDIVIDUAL", "taxInvoiceRequired": false
              },
              "timeline": {
                "createdAt": "2026-09-30T14:00:00", "ordersClosedAt": "2026-09-30T13:52:00", "reviewDueAt": "2026-10-05T23:59:59",
                "confirmedAt": "2026-10-06T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-12", "payoutDueNote": "자동 확정 10.06 + 3영업일 · 10.09 한글날 제외", "paidAt": null
              },
              "review": null,
              "breakdown": {
                "grossOrderAmount": 5600000, "cancel": {"amount": 72000, "count": 2}, "return": {"amount": 24000, "count": 1},
                "deliveryException": {"amount": 24000, "count": 1}, "confirmedSalesAmount": 5480000,
                "rewardRateLabel": "10%", "rewardRates": [{"productName": "수분 세럼 50ml", "rate": 10.0}],
                "originalRewardAmount": 548000, "rewardAmount": 548000, "rewardClawbackAmount": 0, "rewardAfterClawback": 548000,
                "withholdingIncomeRate": 0.0300, "withholdingLocalRate": 0.0030, "withholdingAmount": 18084,
                "creatorVatRate": 0.1000, "creatorVatAmount": 0, "creatorPayoutAmount": 529916
              },
              "clawbacks": [],
              "payouts": {
                "shownAfterConfirm": false,
                "rows": [
                  {"payee": "CREATOR", "payeeLabel": "인플루언서", "name": "소연_쇼룸", "amount": 529916,
                   "note": "리워드 548,000 − 원천징수 18,084(3.3%)", "status": "SCHEDULED", "statusLabel": "10.12 지급 예정", "date": "2026-10-12"},
                  {"payee": "BRAND", "payeeLabel": "브랜드", "name": "데일리랩", "amount": 4712800,
                   "note": null, "status": "SCHEDULED", "statusLabel": "10.12 지급 예정", "date": "2026-10-12"},
                  {"payee": "PLATFORM", "payeeLabel": "플랫폼", "name": "SHOWROOMZ", "amount": 54800,
                   "note": "플랫폼 신고 · 납부", "status": "SCHEDULED", "statusLabel": "10.12 지급 예정", "date": "2026-10-12"}
                ]
              },
              "payment": {
                "bankName": "카카오뱅크", "accountNumberMasked": "*********234567", "accountHolder": "이소연",
                "dueDate": "2026-10-12", "dueNote": "자동 확정 10.06 + 3영업일 · 10.09 한글날 제외", "paidAt": null, "pgReference": null
              },
              "adjustment": null,
              "withholding": {"receiptIssuer": "SHOWROOMZ", "amount": 18084, "receiptAvailable": false, "receiptLabel": "징수 예정액"},
              "taxInvoice": null,
              "items": {
                "preview": [
            """ + A_ITEM_ROWS + """
                ],
                "totalCount": 151,
                "downloadAvailable": true
              }
            }
            """;

    // ── 상세 — 조정 협의(STL-2610-002 · 민지의 뷰티룸 · 사업자) ─────────────────────

    static final String DETAIL_ADJUSTING = """
            {
              "settlement": {
                "settlementId": 29, "settlementNumber": "STL-2610-002", "groupBuyId": 55, "groupBuyTitle": "가을 립 틴트 공구",
                "brandName": "데일리랩", "marketId": 17, "periodStartAt": "2026-09-15T00:00:00", "periodEndAt": "2026-09-21T23:59:59",
                "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                "creatorBusinessType": "BUSINESS", "taxInvoiceRequired": true
              },
              "timeline": {
                "createdAt": "2026-10-01T10:00:00", "ordersClosedAt": "2026-10-01T09:48:00", "reviewDueAt": "2026-10-06T23:59:59",
                "confirmedAt": null, "confirmReason": null, "confirmReasonLabel": null,
                "payoutDueDate": null, "payoutDueNote": null, "paidAt": null
              },
              "review": null,
              "breakdown": {
                "grossOrderAmount": 2040000, "cancel": {"amount": 52000, "count": 2}, "return": {"amount": 28000, "count": 1},
                "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 1960000,
                "rewardRateLabel": "15%", "rewardRates": [{"productName": "벨벳 립 틴트", "rate": 15.0}],
                "originalRewardAmount": 294000, "rewardAmount": 294000, "rewardClawbackAmount": 0, "rewardAfterClawback": 294000,
                "withholdingIncomeRate": 0.0300, "withholdingLocalRate": 0.0030, "withholdingAmount": 0,
                "creatorVatRate": 0.1000, "creatorVatAmount": 29400, "creatorPayoutAmount": 323400
              },
              "clawbacks": [],
              "payouts": {
                "shownAfterConfirm": true,
                "rows": [
                  {"payee": "CREATOR", "payeeLabel": "인플루언서", "name": "민지의 뷰티룸", "amount": 323400,
                   "note": "리워드 294,000 + 부가세 29,400", "status": "HELD", "statusLabel": "보류 중", "date": null},
                  {"payee": "BRAND", "payeeLabel": "브랜드", "name": "데일리랩", "amount": 1577800,
                   "note": null, "status": "HELD", "statusLabel": "보류 중", "date": null},
                  {"payee": "PLATFORM", "payeeLabel": "플랫폼", "name": "SHOWROOMZ", "amount": 0,
                   "note": "부가세는 인플루언서에게 지급", "status": "HELD", "statusLabel": "보류 중", "date": null}
                ]
              },
              "payment": {
                "bankName": "신한은행", "accountNumberMasked": "********123456", "accountHolder": "박민지",
                "dueDate": null, "dueNote": null, "paidAt": null, "pgReference": null
              },
              "adjustment": {
                "adjustmentId": 4, "threadId": 812, "status": "OPEN", "statusLabel": "협의 중", "requesterType": "SELLER",
                "openedAt": "2026-10-05T15:20:00", "deadlineAt": "2026-10-20T23:59:59", "remainingBusinessDays": 7,
                "originalRewardAmount": 294000, "maxRewardAmount": 1728360, "agreedRewardAmount": null, "finalRewardAmount": null,
                "myLatestAmount": 282000, "counterpartLatestAmount": 270000, "counterpartLatestAt": "2026-10-05T15:20:00",
                "turn": "THEIR_TURN", "turnLabel": "상대 응답 대기", "turnTone": "INFO",
                "proposals": [
                  {"seq": 1, "proposerType": "SELLER", "mine": false, "rewardAmount": 270000,
                   "reason": "사전 제공 샘플 20개 중 12개 미도착(반송) — 샘플분 리워드 차감 요청",
                   "status": "COUNTERED", "proposedAt": "2026-10-05T15:20:00", "respondedAt": "2026-10-07T11:05:00"},
                  {"seq": 2, "proposerType": "CREATOR", "mine": true, "rewardAmount": 282000,
                   "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다",
                   "status": "PENDING", "proposedAt": "2026-10-07T11:05:00", "respondedAt": null}
                ]
              },
              "withholding": null,
              "taxInvoice": null,
              "items": {
                "preview": [
                  {"orderNumber": "20260921-000412", "productName": "벨벳 립 틴트", "optionName": "03 로즈", "quantity": 2, "settledQuantity": 2,
                   "paidAmount": 28000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 28000, "rewardRate": 15.0, "rewardAmount": 4200},
                  {"orderNumber": "20260921-000398", "productName": "벨벳 립 틴트", "optionName": "01 코랄", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 14000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 14000, "rewardRate": 15.0, "rewardAmount": 2100},
                  {"orderNumber": "20260920-000377", "productName": "벨벳 립 틴트", "optionName": "05 버건디", "quantity": 2, "settledQuantity": 0,
                   "paidAmount": 28000, "status": "RETURNED", "statusLabel": "반품", "settledAmount": 0, "rewardRate": 15.0, "rewardAmount": 0},
                  {"orderNumber": "20260920-000351", "productName": "벨벳 립 틴트", "optionName": "03 로즈", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 14000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 14000, "rewardRate": 15.0, "rewardAmount": 2100},
                  {"orderNumber": "20260919-000340", "productName": "벨벳 립 틴트", "optionName": "02 누드", "quantity": 3, "settledQuantity": 3,
                   "paidAmount": 42000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 42000, "rewardRate": 15.0, "rewardAmount": 6300}
                ],
                "totalCount": 74,
                "downloadAvailable": false
              }
            }
            """;

    // ── 상세 — 사업자 세금계산서 확인 중(STL-2609-005 · 지우_스타일) ───────────────────

    private static final String TAX_INVOICE_SUBMITTED = """
                "cardStatus": "SUBMITTED",
                "supplier": {
                  "name": "SHOWROOMZ TEST", "representative": "TEST 대표", "registrationNumber": "000-00-00000",
                  "address": "TEST 주소", "taxEmail": "tax-test@showroomz.shop"
                },
                "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                "approvalNumber": "20261008-41000027-38475920", "submittedAt": "2026-10-08T16:40:00",
                "attachmentName": "세금계산서_지우스타일_0929.pdf",
                "rejectReason": null, "rejectReasonLabel": null, "rejectedAt": null, "verifiedAt": null
            """;

    static final String DETAIL_TAX_INVOICE = """
            {
              "settlement": {
                "settlementId": 25, "settlementNumber": "STL-2609-005", "groupBuyId": 39, "groupBuyTitle": "가을 쿠션 팩트 공구",
                "brandName": "데일리랩", "marketId": 17, "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                "creatorBusinessType": "BUSINESS", "taxInvoiceRequired": true
              },
              "timeline": {
                "createdAt": "2026-09-29T15:00:00", "ordersClosedAt": "2026-09-29T14:46:00", "reviewDueAt": "2026-10-02T23:59:59",
                "confirmedAt": "2026-10-03T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": null, "payoutDueNote": "확인 후 + 3영업일", "paidAt": null
              },
              "review": null,
              "breakdown": {
                "grossOrderAmount": 5520000, "cancel": {"amount": 40000, "count": 1}, "return": {"amount": 0, "count": 0},
                "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 5480000,
                "rewardRateLabel": "10%", "rewardRates": [{"productName": "커버 쿠션 팩트 15g", "rate": 10.0}],
                "originalRewardAmount": 548000, "rewardAmount": 548000, "rewardClawbackAmount": 0, "rewardAfterClawback": 548000,
                "withholdingIncomeRate": 0.0300, "withholdingLocalRate": 0.0030, "withholdingAmount": 0,
                "creatorVatRate": 0.1000, "creatorVatAmount": 54800, "creatorPayoutAmount": 602800
              },
              "clawbacks": [],
              "payouts": {
                "shownAfterConfirm": false,
                "rows": [
                  {"payee": "CREATOR", "payeeLabel": "인플루언서", "name": "지우_스타일", "amount": 602800,
                   "note": "리워드 548,000 + 부가세 54,800", "status": "BLOCKED", "statusLabel": "확인 중", "date": null},
                  {"payee": "BRAND", "payeeLabel": "브랜드", "name": "데일리랩", "amount": 4712800,
                   "note": null, "status": "PAID", "statusLabel": "10.07 지급 완료", "date": "2026-10-07"},
                  {"payee": "PLATFORM", "payeeLabel": "플랫폼", "name": "SHOWROOMZ", "amount": 0,
                   "note": "부가세는 인플루언서에게 지급", "status": "NOT_APPLICABLE", "statusLabel": "—", "date": null}
                ]
              },
              "payment": {
                "bankName": "카카오뱅크", "accountNumberMasked": "*********345678", "accountHolder": "김지우",
                "dueDate": null, "dueNote": "확인 후 + 3영업일", "paidAt": null, "pgReference": null
              },
              "adjustment": null,
              "withholding": null,
              "taxInvoice": {
            """ + TAX_INVOICE_SUBMITTED + """
              },
              "items": {
                "preview": [
                  {"orderNumber": "20260914-000611", "productName": "커버 쿠션 팩트 15g", "optionName": "21호 아이보리", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 40000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000},
                  {"orderNumber": "20260914-000590", "productName": "커버 쿠션 팩트 15g", "optionName": "23호 베이지", "quantity": 1, "settledQuantity": 0,
                   "paidAmount": 40000, "status": "CANCELLED", "statusLabel": "취소", "settledAmount": 0, "rewardRate": 10.0, "rewardAmount": 0},
                  {"orderNumber": "20260913-000574", "productName": "커버 쿠션 팩트 15g", "optionName": "21호 아이보리", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 40000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000},
                  {"orderNumber": "20260913-000552", "productName": "커버 쿠션 팩트 15g", "optionName": "23호 베이지", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 40000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000},
                  {"orderNumber": "20260912-000530", "productName": "커버 쿠션 팩트 15g", "optionName": "21호 아이보리", "quantity": 1, "settledQuantity": 1,
                   "paidAmount": 40000, "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 40000, "rewardRate": 10.0, "rewardAmount": 4000}
                ],
                "totalCount": 138,
                "downloadAvailable": true
              }
            }
            """;

    static final String TAX_INVOICE_SUBMIT_RESPONSE = """
            {
              "settlementId": 25,
              "taxInvoice": {
            """ + TAX_INVOICE_SUBMITTED + """
              }
            }
            """;

    static final String ITEMS = """
            {
              "content": [
            """ + A_ITEM_ROWS + """
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 31, "totalResults": 151, "limit": 5, "hasNext": true}
            }
            """;

    // ── 조정 협의 — 미리보기 · 요청(STL-2610-005 · 소연_쇼룸) ──────────────────────

    static final String PREVIEW_OPEN = """
            {
              "settlementId": 32, "settlementNumber": "STL-2610-005", "canRequest": true, "cannotRequestReason": null,
              "reviewEndsAt": "2026-10-13T23:59:59", "originalRewardAmount": 282000, "maxRewardAmount": 2072270,
              "input": null, "preview": null,
              "original": {"rewardAmount": 282000, "rewardVatAmount": 28200, "withholdingAmount": 9306, "creatorNetAmount": 272694, "brandPayoutAmount": 1969300},
              "inRange": null
            }
            """;

    static final String PREVIEW_INPUT = """
            {
              "settlementId": 32, "settlementNumber": "STL-2610-005", "canRequest": true, "cannotRequestReason": null,
              "reviewEndsAt": "2026-10-13T23:59:59", "originalRewardAmount": 282000, "maxRewardAmount": 2072270,
              "input": {"rewardAmount": 300000},
              "preview": {"rewardAmount": 300000, "rewardVatAmount": 30000, "withholdingAmount": 9900, "creatorNetAmount": 290100, "brandPayoutAmount": 1949500},
              "original": {"rewardAmount": 282000, "rewardVatAmount": 28200, "withholdingAmount": 9306, "creatorNetAmount": 272694, "brandPayoutAmount": 1969300},
              "inRange": true
            }
            """;

    static final String PREVIEW_OUT_OF_RANGE = """
            {
              "settlementId": 32, "settlementNumber": "STL-2610-005", "canRequest": true, "cannotRequestReason": null,
              "reviewEndsAt": "2026-10-13T23:59:59", "originalRewardAmount": 282000, "maxRewardAmount": 2072270,
              "input": {"rewardAmount": 2100000},
              "preview": null,
              "original": {"rewardAmount": 282000, "rewardVatAmount": 28200, "withholdingAmount": 9306, "creatorNetAmount": 272694, "brandPayoutAmount": 1969300},
              "inRange": false
            }
            """;

    static final String PREVIEW_CLOSED = """
            {
              "settlementId": 26, "settlementNumber": "STL-2609-006", "canRequest": false, "cannotRequestReason": "REVIEW_CLOSED",
              "reviewEndsAt": "2026-10-05T23:59:59", "originalRewardAmount": 548000, "maxRewardAmount": 4832360,
              "input": null, "preview": null,
              "original": {"rewardAmount": 548000, "rewardVatAmount": 54800, "withholdingAmount": 18084, "creatorNetAmount": 529916, "brandPayoutAmount": 4712800},
              "inRange": null
            }
            """;

    static final String REQ_REQUEST = """
            {"rewardAmount": 300000, "reason": "릴스 2편 추가 게시분 — 계약서 3조(추가 홍보) 반영 요청"}
            """;

    static final String REQUEST_CREATED = """
            {"adjustmentId": 6, "threadId": 845, "proposalId": 15, "deadlineAt": "2026-10-23T23:59:59"}
            """;

    // ── 조정 협의 — 고정 카드 · 응답(협의 4 · 민지의 뷰티룸 시점) ────────────────────

    private static final String V_HEAD_OPEN = """
            {
              "adjustmentId": 4, "threadId": 812, "status": "OPEN", "statusLabel": "협의 중",
            """;

    private static final String V_REFS = """
              "settlement": {"settlementId": 29, "settlementNumber": "STL-2610-002"},
              "groupBuy": {"groupBuyId": 55, "groupBuyNumber": "GB-20260902-055", "title": "가을 립 틴트 공구"},
              "counterpart": {"type": "SELLER", "name": "데일리랩"},
              "requesterType": "SELLER", "originalRewardAmount": 294000, "maxRewardAmount": 1728360,
            """;

    /** 제안 조각 — 끝 줄바꿈 없이 끝난다. 배열 중간 · latestProposal 값이면 뒤에 {@link #NEXT}, 배열 끝이면 {@link #LAST}. */
    private static final String P1 = """
                {"proposalId": 7, "seq": 1, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 270000, "deltaAmount": -24000,
                 "reason": "사전 제공 샘플 20개 중 12개 미도착(반송) — 샘플분 리워드 차감 요청", "status": "COUNTERED",
                 "proposedAt": "2026-10-05T15:20:00", "respondedAt": "2026-10-07T11:05:00", "cardMessageId": 5531}\
            """;

    private static final String P2 = """
                {"proposalId": 9, "seq": 2, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "rewardAmount": 282000, "deltaAmount": -12000,
                 "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다", "status": "COUNTERED",
                 "proposedAt": "2026-10-07T11:05:00", "respondedAt": "2026-10-08T14:30:00", "cardMessageId": 5547}\
            """;

    private static final String P3_PENDING = """
                {"proposalId": 12, "seq": 3, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 276000, "deltaAmount": -18000,
                 "reason": "반송분 절반 기준이면 276,000원이 맞습니다", "status": "PENDING",
                 "proposedAt": "2026-10-08T14:30:00", "respondedAt": null, "cardMessageId": 5588}\
            """;

    private static final String P3_COUNTERED = """
                {"proposalId": 12, "seq": 3, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 276000, "deltaAmount": -18000,
                 "reason": "반송분 절반 기준이면 276,000원이 맞습니다", "status": "COUNTERED",
                 "proposedAt": "2026-10-08T14:30:00", "respondedAt": "2026-10-10T11:00:00", "cardMessageId": 5588}\
            """;

    private static final String P3_ACCEPTED = """
                {"proposalId": 12, "seq": 3, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 276000, "deltaAmount": -18000,
                 "reason": "반송분 절반 기준이면 276,000원이 맞습니다", "status": "ACCEPTED",
                 "proposedAt": "2026-10-08T14:30:00", "respondedAt": "2026-10-10T11:00:00", "cardMessageId": 5588}\
            """;

    private static final String P3_REJECTED = """
                {"proposalId": 12, "seq": 3, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 276000, "deltaAmount": -18000,
                 "reason": "반송분 절반 기준이면 276,000원이 맞습니다", "status": "REJECTED",
                 "proposedAt": "2026-10-08T14:30:00", "respondedAt": "2026-10-10T11:00:00", "cardMessageId": 5588}\
            """;

    private static final String P4_PENDING = """
                {"proposalId": 16, "seq": 4, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "rewardAmount": 280000, "deltaAmount": -14000,
                 "reason": "택배사 분실 확인서 첨부드렸어요 — 280,000원으로 마무리 부탁드립니다", "status": "PENDING",
                 "proposedAt": "2026-10-10T11:00:00", "respondedAt": null, "cardMessageId": 5609}\
            """;

    private static final String NEXT = ",\n";
    private static final String LAST = "\n";

    private static final String OPEN_DATES = """
              "agreedRewardAmount": null, "finalRewardAmount": null,
              "openedAt": "2026-10-05T15:20:00", "deadlineAt": "2026-10-20T23:59:59", "remainingBusinessDays": 7, "closedAt": null,
            """;

    private static final String LATEST = """
              "latestProposal":
            """;

    private static final String PROPOSALS_OPEN = """
              "proposals": [
            """;

    private static final String VIEW_CLOSE = """
              ]
            }
            """;

    /** 브랜드가 다른 금액(276,000)을 제안했고 인플루언서(나)의 응답 차례. */
    static final String VIEW_MY_TURN = V_HEAD_OPEN + """
              "turn": "MY_TURN", "turnLabel": "내 응답 필요", "turnTone": "WARNING",
            """ + V_REFS + LATEST + P3_PENDING + NEXT + OPEN_DATES + """
              "permissions": {"canAccept": true, "canReject": true, "canCounter": true, "canSend": true},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2 + NEXT + P3_PENDING + LAST + VIEW_CLOSE;

    /** 다른 금액(280,000) 제안 — 브랜드 제안은 COUNTERED 로 닫히고 상대 차례. */
    static final String VIEW_COUNTERED = V_HEAD_OPEN + """
              "turn": "THEIR_TURN", "turnLabel": "상대 응답 대기", "turnTone": "INFO",
            """ + V_REFS + LATEST + P4_PENDING + NEXT + OPEN_DATES + """
              "permissions": {"canAccept": false, "canReject": false, "canCounter": false, "canSend": true},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2 + NEXT + P3_COUNTERED + NEXT + P4_PENDING + LAST + VIEW_CLOSE;

    /** 동의 — 금액 변경 + 보류 해제 + 종결이 한 번에. 정산은 합의 확정 · 지급 예정. */
    static final String VIEW_ACCEPTED = """
            {
              "adjustmentId": 4, "threadId": 812, "status": "AGREED", "statusLabel": "합의 · 금액 변경",
              "turn": "CLOSED", "turnLabel": "종결", "turnTone": "NEUTRAL",
            """ + V_REFS + LATEST + P3_ACCEPTED + NEXT + """
              "agreedRewardAmount": 276000, "finalRewardAmount": 276000,
              "openedAt": "2026-10-05T15:20:00", "deadlineAt": "2026-10-20T23:59:59", "remainingBusinessDays": null,
              "closedAt": "2026-10-10T11:00:00",
              "permissions": {"canAccept": false, "canReject": false, "canCounter": false, "canSend": false},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2 + NEXT + P3_ACCEPTED + LAST + VIEW_CLOSE;

    /** 반대 — 협의는 계속된다(OPEN_FLOOR). 반대한 쪽은 그 제안에 동의하거나 다른 금액을 낼 수 있다. */
    static final String VIEW_REJECTED = V_HEAD_OPEN + """
              "turn": "OPEN_FLOOR", "turnLabel": "협의 중", "turnTone": "NEUTRAL",
            """ + V_REFS + LATEST + P3_REJECTED + NEXT + OPEN_DATES + """
              "permissions": {"canAccept": true, "canReject": false, "canCounter": true, "canSend": true},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2 + NEXT + P3_REJECTED + LAST + VIEW_CLOSE;

    static final String REQ_COUNTER = """
            {"rewardAmount": 280000, "reason": "택배사 분실 확인서 첨부드렸어요 — 280,000원으로 마무리 부탁드립니다"}
            """;

    // ── 오류 ─────────────────────────────────────────────────────────────

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_PAGE_SIZE = """
            {"code": "INVALID_INPUT", "message": "size 는 1 ~ 100 이어야 합니다."}
            """;

    static final String ERR_PAYOUT_FAILED_FILTER = """
            {"code": "INVALID_INPUT", "message": "PAYOUT_FAILED 는 필터로 받지 않습니다 — PAID 에 포함됩니다."}
            """;

    static final String ERR_NOT_NULL = """
            {"code": "INVALID_INPUT", "message": "널이어서는 안됩니다"}
            """;

    static final String ERR_MIN_ZERO = """
            {"code": "INVALID_INPUT", "message": "0 이상이어야 합니다"}
            """;

    static final String ERR_REASON_TOO_LONG = """
            {"code": "INVALID_INPUT", "message": "조정 사유는 1000자까지 입력할 수 있습니다."}
            """;

    static final String ERR_PDF_ONLY = """
            {"code": "INVALID_INPUT", "message": "PDF 파일만 올릴 수 있습니다."}
            """;

    static final String ERR_PDF_SIZE = """
            {"code": "INVALID_INPUT", "message": "PDF 는 10MB 까지 올릴 수 있습니다."}
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

    static final String ERR_STATEMENT_NOT_READY = """
            {"code": "SETTLEMENT_STATEMENT_NOT_READY", "message": "정산이 확정된 뒤에 명세를 내려받을 수 있습니다."}
            """;

    static final String ERR_RECEIPT_NOT_READY = """
            {"code": "SETTLEMENT_RECEIPT_NOT_READY", "message": "원천징수영수증이 아직 준비되지 않았습니다."}
            """;

    static final String ERR_TAX_INVOICE_NOT_REQUIRED = """
            {"code": "SETTLEMENT_TAX_INVOICE_NOT_REQUIRED", "message": "사업자 정산만 세금계산서를 제출합니다."}
            """;

    static final String ERR_TAX_INVOICE_NOT_OPEN = """
            {"code": "SETTLEMENT_TAX_INVOICE_NOT_OPEN", "message": "지금은 세금계산서를 제출할 수 없습니다."}
            """;

    static final String ERR_TAX_INVOICE_NUMBER_INVALID = """
            {"code": "SETTLEMENT_TAX_INVOICE_NUMBER_INVALID", "message": "승인번호 24자리를 확인해 주세요 — 숫자와 하이픈(-)만 입력할 수 있어요."}
            """;

    static final String ERR_TAX_INVOICE_NUMBER_DUPLICATED = """
            {"code": "SETTLEMENT_TAX_INVOICE_NUMBER_INVALID", "message": "이미 다른 정산에서 확인된 승인번호입니다."}
            """;

    static final String ERR_TAX_DOCUMENT_STATE_CHANGED = """
            {"code": "SETTLEMENT_TAX_DOCUMENT_STATE_CHANGED", "message": "증빙 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."}
            """;

    static final String ERR_THREAD_NOT_FOUND = """
            {"code": "THREAD_NOT_FOUND", "message": "존재하지 않는 스레드입니다."}
            """;

    static final String ERR_THREAD_ACCESS_DENIED = """
            {"code": "THREAD_ACCESS_DENIED", "message": "해당 스레드에 대한 권한이 없습니다."}
            """;

    static final String ERR_GROUP_BUY_THREAD_UNAVAILABLE = """
            {"code": "GROUP_BUY_THREAD_UNAVAILABLE", "message": "지금은 스레드를 열 수 없습니다. 운영자에게 문의해 주세요."}
            """;

    static final String ERR_ADJUSTMENT_NOT_FOUND = """
            {"code": "SETTLEMENT_ADJUSTMENT_NOT_FOUND", "message": "정산 조정 협의를 찾을 수 없습니다."}
            """;

    static final String ERR_ADJUSTMENT_ACCESS_DENIED = """
            {"code": "SETTLEMENT_ADJUSTMENT_ACCESS_DENIED", "message": "이 정산의 당사자가 아닙니다."}
            """;

    static final String ERR_ADJUSTMENT_WINDOW_CLOSED = """
            {"code": "SETTLEMENT_ADJUSTMENT_WINDOW_CLOSED", "message": "정산 확인 기간에만 조정을 요청할 수 있습니다."}
            """;

    static final String ERR_ADJUSTMENT_ALREADY_EXISTS = """
            {"code": "SETTLEMENT_ADJUSTMENT_ALREADY_EXISTS", "message": "이 정산에는 이미 조정 협의가 있습니다."}
            """;

    static final String ERR_ADJUSTMENT_REASON_REQUIRED = """
            {"code": "SETTLEMENT_ADJUSTMENT_REASON_REQUIRED", "message": "조정 사유를 입력해 주세요."}
            """;

    static final String ERR_ADJUSTMENT_AMOUNT_OUT_OF_RANGE = """
            {"code": "SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE", "message": "리워드 금액은 0원 ~ 2,072,270원 안에서 원래 금액과 다르게 입력해 주세요."}
            """;

    static final String ERR_ADJUSTMENT_AMOUNT_UNCHANGED = """
            {"code": "SETTLEMENT_ADJUSTMENT_AMOUNT_UNCHANGED", "message": "제안 금액이 현재 요청 금액과 같습니다. 동의로 응답해 주세요."}
            """;

    static final String ERR_ADJUSTMENT_NOT_YOUR_TURN = """
            {"code": "SETTLEMENT_ADJUSTMENT_NOT_YOUR_TURN", "message": "상대의 응답을 기다리는 중입니다."}
            """;

    static final String ERR_ADJUSTMENT_DEADLINE_PASSED = """
            {"code": "SETTLEMENT_ADJUSTMENT_DEADLINE_PASSED", "message": "합의 기한이 지났습니다."}
            """;

    static final String ERR_ADJUSTMENT_STATE_CHANGED = """
            {"code": "SETTLEMENT_ADJUSTMENT_STATE_CHANGED", "message": "협의 상태가 바뀌었습니다. 화면을 새로 고쳐 주세요."}
            """;

    static final String ERR_ADJUSTMENT_CLOSED = """
            {"code": "SETTLEMENT_ADJUSTMENT_CLOSED", "message": "종결된 이슈 스레드입니다. 메시지를 보낼 수 없습니다."}
            """;
}
