package showroomz.api.seller.settlement.docs;

/**
 * 파트너센터 정산 관리(13) · 정산 조정 협의(14 이슈 스레드) Swagger 예시 JSON — 뷰어는 브랜드 「데일리랩」(marketId 17)이다.
 * 기준 시각은 2026-10-10 11:00(토). 금액은 44 어드민 설계서 2-4 산식(전 항목 절사)으로 검산한 값이고 어드민 예시와 같은 정산이다.
 *
 * <ul>
 *   <li>{@code STL-2609-006}(id 26) — × 소연_쇼룸(비사업자) · 자동 확정 · 10.12 지급 예정</li>
 *   <li>{@code STL-2610-002}(id 29) — × 민지의 뷰티룸(사업자) · 조정 협의(협의 id 4 · 스레드 812)</li>
 *   <li>{@code STL-2609-005}(id 25) — × 지우_스타일(사업자) · 브랜드 행 지급 완료 · 인플루언서 행 보류</li>
 * </ul>
 */
final class SellerSettlementDocsExamples {

    private SellerSettlementDocsExamples() {
    }

    // ── 목록 · 요약 ──────────────────────────────────────────────────────

    static final String LIST = """
            {
              "content": [
                {
                  "settlementId": 29, "settlementNumber": "STL-2610-002", "groupBuyId": 55, "groupBuyTitle": "가을 립 틴트 공구",
                  "influencer": {"creatorId": 31, "showroomName": "민지의 뷰티룸"},
                  "periodStartAt": "2026-09-15T00:00:00", "periodEndAt": "2026-09-21T23:59:59",
                  "confirmedSalesAmount": 1960000, "feeAmount": 58800, "rewardAmount": 294000, "rewardVatAmount": 29400,
                  "brandPayoutAmount": 1577800,
                  "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                  "schedule": {"kind": "NONE", "date": null}
                },
                {
                  "settlementId": 26, "settlementNumber": "STL-2609-006", "groupBuyId": 41, "groupBuyTitle": "여름 수분 세럼 공구",
                  "influencer": {"creatorId": 7, "showroomName": "소연_쇼룸"},
                  "periodStartAt": "2026-09-01T00:00:00", "periodEndAt": "2026-09-07T23:59:59",
                  "confirmedSalesAmount": 5480000, "feeAmount": 164400, "rewardAmount": 548000, "rewardVatAmount": 54800,
                  "brandPayoutAmount": 4712800,
                  "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                  "schedule": {"kind": "PAYOUT_DUE", "date": "2026-10-12"}
                },
                {
                  "settlementId": 25, "settlementNumber": "STL-2609-005", "groupBuyId": 39, "groupBuyTitle": "가을 쿠션 팩트 공구",
                  "influencer": {"creatorId": 27, "showroomName": "지우_스타일"},
                  "periodStartAt": "2026-09-08T00:00:00", "periodEndAt": "2026-09-14T23:59:59",
                  "confirmedSalesAmount": 5480000, "feeAmount": 164400, "rewardAmount": 548000, "rewardVatAmount": 54800,
                  "brandPayoutAmount": 4712800,
                  "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
                  "schedule": {"kind": "PAID", "date": "2026-10-07"}
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 2, "totalResults": 6, "limit": 3, "hasNext": true}
            }
            """;

    static final String SUMMARY = """
            {
              "paid": {"amount": 10812600, "count": 4},
              "scheduled": {"amount": 4712800, "count": 1},
              "nextPayoutDate": "2026-10-12",
              "scheduledHasClawback": false,
              "reviewingCount": 0,
              "reviewingDueAt": null,
              "adjustingCount": 1,
              "statusCounts": {"REVIEWING": 0, "ADJUSTING": 1, "PAYOUT_SCHEDULED": 2, "PAID": 3},
              "attentionCount": 0
            }
            """;

    static final String SUMMARY_FIRST = """
            {
              "paid": {"amount": null, "count": 0},
              "scheduled": {"amount": null, "count": 0},
              "nextPayoutDate": null,
              "scheduledHasClawback": false,
              "reviewingCount": 1,
              "reviewingDueAt": "2026-10-13T23:59:59",
              "adjustingCount": 0,
              "statusCounts": {"REVIEWING": 1, "ADJUSTING": 0, "PAYOUT_SCHEDULED": 0, "PAID": 0},
              "attentionCount": 1
            }
            """;

    // ── 상세 ─────────────────────────────────────────────────────────────

    /** 명세 행 — 상세 미리보기와 GET …/items 가 같은 행을 쓴다. */
    private static final String A_ITEM_ROWS = """
                {
                  "orderNumber": "20260907-000318", "subOrderNumber": "20260907-000318-01", "consumerNameMasked": "이*연",
                  "productName": "수분 세럼 50ml", "optionName": null, "quantity": 1, "settledQuantity": 1, "unitPrice": 24000, "paidAmount": 24000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 24000, "rewardRate": 10.0, "rewardAmount": 2400
                },
                {
                  "orderNumber": "20260907-000301", "subOrderNumber": "20260907-000301-01", "consumerNameMasked": "박*윤",
                  "productName": "수분 세럼 50ml", "optionName": "2개 세트", "quantity": 1, "settledQuantity": 1, "unitPrice": 46000, "paidAmount": 46000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 46000, "rewardRate": 10.0, "rewardAmount": 4600
                },
                {
                  "orderNumber": "20260906-000287", "subOrderNumber": "20260906-000287-01", "consumerNameMasked": "김*지",
                  "productName": "수분 세럼 50ml", "optionName": null, "quantity": 2, "settledQuantity": 1, "unitPrice": 24000, "paidAmount": 48000,
                  "status": "PARTIAL_RETURNED", "statusLabel": "부분 반품", "settledAmount": 24000, "rewardRate": 10.0, "rewardAmount": 2400
                },
                {
                  "orderNumber": "20260906-000254", "subOrderNumber": "20260906-000254-01", "consumerNameMasked": "정*우",
                  "productName": "수분 세럼 50ml", "optionName": "2개 세트", "quantity": 1, "settledQuantity": 0, "unitPrice": 46000, "paidAmount": 46000,
                  "status": "CANCELLED", "statusLabel": "취소", "settledAmount": 0, "rewardRate": 10.0, "rewardAmount": 0
                },
                {
                  "orderNumber": "20260905-000233", "subOrderNumber": "20260905-000233-01", "consumerNameMasked": "최*아",
                  "productName": "수분 세럼 50ml", "optionName": null, "quantity": 3, "settledQuantity": 3, "unitPrice": 24000, "paidAmount": 72000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 72000, "rewardRate": 10.0, "rewardAmount": 7200
                }
            """;

    static final String DETAIL_SCHEDULED = """
            {
              "settlementId": 26, "settlementNumber": "STL-2609-006",
              "groupBuy": {"groupBuyId": 41, "title": "여름 수분 세럼 공구"},
              "influencer": {"creatorId": 7, "showroomName": "소연_쇼룸"},
              "periodStartAt": "2026-09-01T00:00:00", "periodEndAt": "2026-09-07T23:59:59",
              "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정", "statusTone": "NEUTRAL",
              "dates": {
                "createdAt": "2026-09-30T14:00:00", "ordersClosedAt": "2026-09-30T13:52:00", "reviewDueAt": "2026-10-05T23:59:59",
                "confirmedAt": "2026-10-06T00:00:00", "confirmReason": "AUTO", "confirmReasonLabel": "자동 확정",
                "payoutDueDate": "2026-10-12", "paidAt": null, "payoutBasis": "자동 확정 10.06 + 3영업일 · 10.09 한글날 제외"
              },
              "adjustment": null,
              "breakdown": {
                "grossOrderAmount": 5600000, "cancel": {"amount": 72000, "count": 2}, "return": {"amount": 24000, "count": 1},
                "deliveryException": {"amount": 24000, "count": 1}, "confirmedSalesAmount": 5480000,
                "pgFee": {"rate": 0.0300, "amount": 164400}, "platformFee": {"rate": 0.0000, "amount": 0, "normalRate": 0.0200},
                "originalRewardAmount": 548000, "rewardAmount": 548000, "rewardVat": {"rate": 0.1000, "amount": 54800},
                "reshipFee": {"amount": 0, "count": 0}, "consumerDeliveryFee": 0,
                "brandPayoutBeforeClawback": 4712800, "clawbacks": [], "brandClawbackAmount": 0, "brandPayoutAmount": 4712800
              },
              "payouts": [
                {"payee": "BRAND", "label": "우리", "amount": 4712800, "note": null,
                 "status": "SCHEDULED", "statusLabel": "지급 예정", "dueDate": "2026-10-12", "paidAt": null},
                {"payee": "CREATOR", "label": "소연_쇼룸", "amount": 529916, "note": "리워드 548,000 − 원천징수 18,084(3.3%)",
                 "status": "SCHEDULED", "statusLabel": "지급 예정", "dueDate": "2026-10-12", "paidAt": null},
                {"payee": "PLATFORM", "label": "플랫폼", "amount": 54800, "note": "리워드 부가세",
                 "status": "SCHEDULED", "statusLabel": "지급 예정", "dueDate": "2026-10-12", "paidAt": null}
              ],
              "payment": {
                "bankName": "국민은행", "accountMasked": "*********567890", "accountHolder": "주식회사 데일리랩",
                "payoutDueDate": "2026-10-12", "paidAt": null, "pgReference": null
              },
              "taxDocuments": [
                {
                  "documentId": 64, "type": "BRAND_TAX_INVOICE", "typeLabel": "브랜드 세금계산서",
                  "supplyAmount": 548000, "vatAmount": 54800, "totalAmount": 602800,
                  "status": "PENDING_ISSUE", "statusLabel": "발행 대기 · 운영팀이 11.10까지 발행합니다",
                  "dueDate": "2026-11-10", "issuedDate": null, "approvalNumber": null, "downloadable": false
                }
              ],
              "influencerTax": {"businessType": "INDIVIDUAL", "withholdingType": "WITHHOLDING_3_3", "label": "비사업자 · 원천징수 3.3% · 플랫폼 신고"},
              "claimShipping": {"consumer": {"amount": 6000, "count": 1}, "brand": {"amount": 0, "count": 0}},
              "items": [
            """ + A_ITEM_ROWS + """
              ],
              "itemTotalCount": 151,
              "actions": {"canRequestAdjustment": false, "canRespondAdjustment": false, "canDownloadStatement": true, "canDownloadTaxInvoice": false}
            }
            """;

    static final String DETAIL_ADJUSTING = """
            {
              "settlementId": 29, "settlementNumber": "STL-2610-002",
              "groupBuy": {"groupBuyId": 55, "title": "가을 립 틴트 공구"},
              "influencer": {"creatorId": 31, "showroomName": "민지의 뷰티룸"},
              "periodStartAt": "2026-09-15T00:00:00", "periodEndAt": "2026-09-21T23:59:59",
              "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
              "dates": {
                "createdAt": "2026-10-01T10:00:00", "ordersClosedAt": "2026-10-01T09:48:00", "reviewDueAt": "2026-10-06T23:59:59",
                "confirmedAt": null, "confirmReason": null, "confirmReasonLabel": null,
                "payoutDueDate": null, "paidAt": null, "payoutBasis": null
              },
              "adjustment": {
                "adjustmentId": 4, "threadId": 812, "status": "OPEN", "statusLabel": "협의 중", "requesterType": "SELLER",
                "openedAt": "2026-10-05T15:20:00", "deadlineAt": "2026-10-20T23:59:59", "remainingBusinessDays": 7,
                "originalRewardAmount": 294000, "maxRewardAmount": 1728360, "agreedRewardAmount": null, "finalRewardAmount": null,
                "myLatestAmount": 270000, "counterpartLatestAmount": 282000, "counterpartLatestAt": "2026-10-07T11:05:00",
                "turn": "MY_TURN", "turnLabel": "내 응답 필요", "turnTone": "WARNING",
                "proposals": [
                  {"seq": 1, "proposerType": "SELLER", "mine": true, "rewardAmount": 270000,
                   "reason": "사전 제공 샘플 20개 중 12개 미도착(반송) — 샘플분 리워드 차감 요청",
                   "status": "COUNTERED", "proposedAt": "2026-10-05T15:20:00", "respondedAt": "2026-10-07T11:05:00"},
                  {"seq": 2, "proposerType": "CREATOR", "mine": false, "rewardAmount": 282000,
                   "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다",
                   "status": "PENDING", "proposedAt": "2026-10-07T11:05:00", "respondedAt": null}
                ]
              },
              "breakdown": {
                "grossOrderAmount": 2040000, "cancel": {"amount": 52000, "count": 2}, "return": {"amount": 28000, "count": 1},
                "deliveryException": {"amount": 0, "count": 0}, "confirmedSalesAmount": 1960000,
                "pgFee": {"rate": 0.0300, "amount": 58800}, "platformFee": {"rate": 0.0000, "amount": 0, "normalRate": 0.0200},
                "originalRewardAmount": 294000, "rewardAmount": 294000, "rewardVat": {"rate": 0.1000, "amount": 29400},
                "reshipFee": {"amount": 0, "count": 0}, "consumerDeliveryFee": 0,
                "brandPayoutBeforeClawback": 1577800, "clawbacks": [], "brandClawbackAmount": 0, "brandPayoutAmount": 1577800
              },
              "payouts": null,
              "payment": null,
              "taxDocuments": null,
              "influencerTax": {"businessType": "BUSINESS", "withholdingType": "TAX_INVOICE", "label": "사업자 · 세금계산서 발행 · 플랫폼 처리"},
              "claimShipping": {"consumer": {"amount": 6000, "count": 1}, "brand": {"amount": 0, "count": 0}},
              "items": [
                {
                  "orderNumber": "20260921-000412", "subOrderNumber": "20260921-000412-01", "consumerNameMasked": "한*솔",
                  "productName": "벨벳 립 틴트", "optionName": "03 로즈", "quantity": 2, "settledQuantity": 2, "unitPrice": 14000, "paidAmount": 28000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 28000, "rewardRate": 15.0, "rewardAmount": 4200
                },
                {
                  "orderNumber": "20260921-000398", "subOrderNumber": "20260921-000398-01", "consumerNameMasked": "윤*아",
                  "productName": "벨벳 립 틴트", "optionName": "01 코랄", "quantity": 1, "settledQuantity": 1, "unitPrice": 14000, "paidAmount": 14000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 14000, "rewardRate": 15.0, "rewardAmount": 2100
                },
                {
                  "orderNumber": "20260920-000377", "subOrderNumber": "20260920-000377-01", "consumerNameMasked": "서*진",
                  "productName": "벨벳 립 틴트", "optionName": "05 버건디", "quantity": 2, "settledQuantity": 0, "unitPrice": 14000, "paidAmount": 28000,
                  "status": "RETURNED", "statusLabel": "반품", "settledAmount": 0, "rewardRate": 15.0, "rewardAmount": 0
                },
                {
                  "orderNumber": "20260920-000351", "subOrderNumber": "20260920-000351-01", "consumerNameMasked": "조*희",
                  "productName": "벨벳 립 틴트", "optionName": "03 로즈", "quantity": 1, "settledQuantity": 1, "unitPrice": 14000, "paidAmount": 14000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 14000, "rewardRate": 15.0, "rewardAmount": 2100
                },
                {
                  "orderNumber": "20260919-000340", "subOrderNumber": "20260919-000340-01", "consumerNameMasked": "강*현",
                  "productName": "벨벳 립 틴트", "optionName": "02 누드", "quantity": 3, "settledQuantity": 3, "unitPrice": 14000, "paidAmount": 42000,
                  "status": "CONFIRMED", "statusLabel": "구매확정", "settledAmount": 42000, "rewardRate": 15.0, "rewardAmount": 6300
                }
              ],
              "itemTotalCount": 74,
              "actions": {"canRequestAdjustment": false, "canRespondAdjustment": true, "canDownloadStatement": false, "canDownloadTaxInvoice": false}
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

    // ── 조정 협의 — 미리보기 · 요청 ────────────────────────────────────────────

    static final String PREVIEW_OPEN = """
            {
              "settlementId": 29, "settlementNumber": "STL-2610-002", "canRequest": true, "cannotRequestReason": null,
              "reviewEndsAt": "2026-10-06T23:59:59", "originalRewardAmount": 294000, "maxRewardAmount": 1728360,
              "input": null, "preview": null,
              "original": {"rewardAmount": 294000, "rewardVatAmount": 29400, "withholdingAmount": 0, "creatorNetAmount": 323400, "brandPayoutAmount": 1577800},
              "inRange": null
            }
            """;

    static final String PREVIEW_INPUT = """
            {
              "settlementId": 29, "settlementNumber": "STL-2610-002", "canRequest": true, "cannotRequestReason": null,
              "reviewEndsAt": "2026-10-06T23:59:59", "originalRewardAmount": 294000, "maxRewardAmount": 1728360,
              "input": {"rewardAmount": 270000},
              "preview": {"rewardAmount": 270000, "rewardVatAmount": 27000, "withholdingAmount": 0, "creatorNetAmount": 297000, "brandPayoutAmount": 1604200},
              "original": {"rewardAmount": 294000, "rewardVatAmount": 29400, "withholdingAmount": 0, "creatorNetAmount": 323400, "brandPayoutAmount": 1577800},
              "inRange": true
            }
            """;

    static final String PREVIEW_OUT_OF_RANGE = """
            {
              "settlementId": 29, "settlementNumber": "STL-2610-002", "canRequest": true, "cannotRequestReason": null,
              "reviewEndsAt": "2026-10-06T23:59:59", "originalRewardAmount": 294000, "maxRewardAmount": 1728360,
              "input": {"rewardAmount": 1800000},
              "preview": null,
              "original": {"rewardAmount": 294000, "rewardVatAmount": 29400, "withholdingAmount": 0, "creatorNetAmount": 323400, "brandPayoutAmount": 1577800},
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
            {"rewardAmount": 270000, "reason": "사전 제공 샘플 20개 중 12개 미도착(반송) — 샘플분 리워드 차감 요청"}
            """;

    static final String REQUEST_CREATED = """
            {"adjustmentId": 4, "threadId": 812, "proposalId": 7, "deadlineAt": "2026-10-20T23:59:59"}
            """;

    // ── 조정 협의 — 고정 카드 · 응답 ──────────────────────────────────────────

    private static final String V_HEAD_OPEN = """
            {
              "adjustmentId": 4, "threadId": 812, "status": "OPEN", "statusLabel": "협의 중",
            """;

    private static final String V_REFS = """
              "settlement": {"settlementId": 29, "settlementNumber": "STL-2610-002"},
              "groupBuy": {"groupBuyId": 55, "groupBuyNumber": "GB-20260902-055", "title": "가을 립 틴트 공구"},
              "counterpart": {"type": "CREATOR", "name": "민지의 뷰티룸"},
              "requesterType": "SELLER", "originalRewardAmount": 294000, "maxRewardAmount": 1728360,
            """;

    /** 제안 조각 — 끝 줄바꿈 없이 끝난다. 배열 중간 · latestProposal 값이면 뒤에 {@link #NEXT}, 배열 끝이면 {@link #LAST}. */
    private static final String P1 = """
                {"proposalId": 7, "seq": 1, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 270000, "deltaAmount": -24000,
                 "reason": "사전 제공 샘플 20개 중 12개 미도착(반송) — 샘플분 리워드 차감 요청", "status": "COUNTERED",
                 "proposedAt": "2026-10-05T15:20:00", "respondedAt": "2026-10-07T11:05:00", "cardMessageId": 5531}\
            """;

    private static final String P2_PENDING = """
                {"proposalId": 9, "seq": 2, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "rewardAmount": 282000, "deltaAmount": -12000,
                 "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다", "status": "PENDING",
                 "proposedAt": "2026-10-07T11:05:00", "respondedAt": null, "cardMessageId": 5547}\
            """;

    private static final String P2_COUNTERED = """
                {"proposalId": 9, "seq": 2, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "rewardAmount": 282000, "deltaAmount": -12000,
                 "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다", "status": "COUNTERED",
                 "proposedAt": "2026-10-07T11:05:00", "respondedAt": "2026-10-08T14:30:00", "cardMessageId": 5547}\
            """;

    private static final String P2_ACCEPTED = """
                {"proposalId": 9, "seq": 2, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "rewardAmount": 282000, "deltaAmount": -12000,
                 "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다", "status": "ACCEPTED",
                 "proposedAt": "2026-10-07T11:05:00", "respondedAt": "2026-10-08T14:30:00", "cardMessageId": 5547}\
            """;

    private static final String P2_REJECTED = """
                {"proposalId": 9, "seq": 2, "proposerType": "CREATOR", "proposerName": "민지의 뷰티룸", "rewardAmount": 282000, "deltaAmount": -12000,
                 "reason": "반송은 택배사 분실 건이라 절반만 반영 부탁드립니다", "status": "REJECTED",
                 "proposedAt": "2026-10-07T11:05:00", "respondedAt": "2026-10-08T14:30:00", "cardMessageId": 5547}\
            """;

    private static final String P3_PENDING = """
                {"proposalId": 12, "seq": 3, "proposerType": "SELLER", "proposerName": "데일리랩", "rewardAmount": 276000, "deltaAmount": -18000,
                 "reason": "반송분 절반 기준이면 276,000원이 맞습니다", "status": "PENDING",
                 "proposedAt": "2026-10-08T14:30:00", "respondedAt": null, "cardMessageId": 5588}\
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

    /** 인플루언서가 다른 금액(282,000)을 제안했고 브랜드(나)의 응답 차례. */
    static final String VIEW_MY_TURN = V_HEAD_OPEN + """
              "turn": "MY_TURN", "turnLabel": "내 응답 필요", "turnTone": "WARNING",
            """ + V_REFS + LATEST + P2_PENDING + NEXT + OPEN_DATES + """
              "permissions": {"canAccept": true, "canReject": true, "canCounter": true, "canSend": true},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2_PENDING + LAST + VIEW_CLOSE;

    /** 다른 금액(276,000) 제안 — 인플루언서 제안은 COUNTERED 로 닫히고 상대 차례. */
    static final String VIEW_COUNTERED = V_HEAD_OPEN + """
              "turn": "THEIR_TURN", "turnLabel": "상대 응답 대기", "turnTone": "INFO",
            """ + V_REFS + LATEST + P3_PENDING + NEXT + OPEN_DATES + """
              "permissions": {"canAccept": false, "canReject": false, "canCounter": false, "canSend": true},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2_COUNTERED + NEXT + P3_PENDING + LAST + VIEW_CLOSE;

    /** 동의 — 금액 변경 + 보류 해제 + 종결이 한 번에. 정산은 합의 확정 · 지급 예정. */
    static final String VIEW_ACCEPTED = """
            {
              "adjustmentId": 4, "threadId": 812, "status": "AGREED", "statusLabel": "합의 · 금액 변경",
              "turn": "CLOSED", "turnLabel": "종결", "turnTone": "NEUTRAL",
            """ + V_REFS + LATEST + P2_ACCEPTED + NEXT + """
              "agreedRewardAmount": 282000, "finalRewardAmount": 282000,
              "openedAt": "2026-10-05T15:20:00", "deadlineAt": "2026-10-20T23:59:59", "remainingBusinessDays": null,
              "closedAt": "2026-10-08T14:30:00",
              "permissions": {"canAccept": false, "canReject": false, "canCounter": false, "canSend": false},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2_ACCEPTED + LAST + VIEW_CLOSE;

    /** 반대 — 협의는 계속된다(OPEN_FLOOR). 반대한 쪽은 그 제안에 동의하거나 다른 금액을 낼 수 있다. */
    static final String VIEW_REJECTED = V_HEAD_OPEN + """
              "turn": "OPEN_FLOOR", "turnLabel": "협의 중", "turnTone": "NEUTRAL",
            """ + V_REFS + LATEST + P2_REJECTED + NEXT + OPEN_DATES + """
              "permissions": {"canAccept": true, "canReject": false, "canCounter": true, "canSend": true},
            """ + PROPOSALS_OPEN + P1 + NEXT + P2_REJECTED + LAST + VIEW_CLOSE;

    static final String REQ_COUNTER = """
            {"rewardAmount": 276000, "reason": "반송분 절반 기준이면 276,000원이 맞습니다"}
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

    static final String ERR_TAX_INVOICE_NOT_ISSUED = """
            {"code": "SETTLEMENT_TAX_INVOICE_NOT_ISSUED", "message": "세금계산서가 아직 발행되지 않았습니다."}
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
            {"code": "SETTLEMENT_ADJUSTMENT_AMOUNT_OUT_OF_RANGE", "message": "리워드 금액은 0원 ~ 1,728,360원 안에서 원래 금액과 다르게 입력해 주세요."}
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
