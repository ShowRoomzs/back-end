package showroomz.api.creator.performance.docs;

/**
 * 쇼룸 스튜디오 성과 관리(10) Swagger 예시 JSON — 「데일리 선크림 공구」(× 데일리랩 · 정산 완료)다. 금액은 정산 행 값 그대로이고 비율은
 * % 값(소수 1자리)이다. 검산 — 주문 313 = 구매확정 291 + 취소 8 + 반품 13 + 배송 예외 1 · 내 리워드 1,003,600 = 판매 리워드 603,600 +
 * 고정 지급비 400,000 · 실지급 583,682 = 603,600 − (18,108 + 1,810).
 */
final class CreatorPerformanceDocsExamples {

    private CreatorPerformanceDocsExamples() {
    }

    static final String LIST = """
            {
              "content": [
                {
                  "groupBuyId": 24, "groupBuyNumber": "GB-20260825-001", "groupBuyTitle": "수분 토너 공구",
                  "brandName": "뷰티로그", "marketId": 7,
                  "periodStartAt": "2026-08-25T10:00:00", "periodEndAt": "2026-08-31T23:59:00",
                  "status": "COLLECTING", "statusLabel": "집계 중", "statusTone": "NEUTRAL",
                  "confirmedSalesAmount": null, "confirmedQuantity": null, "conversionRate": null,
                  "rewardStage": "AFTER_SETTLEMENT", "myRewardAmount": null
                },
                {
                  "groupBuyId": 22, "groupBuyNumber": "GB-20260810-002", "groupBuyTitle": "비타민 세럼 공구",
                  "brandName": "데일리랩", "marketId": 4,
                  "periodStartAt": "2026-08-10T10:00:00", "periodEndAt": "2026-08-16T23:59:00",
                  "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                  "confirmedSalesAmount": 10513600, "confirmedQuantity": 412, "conversionRate": 6.1,
                  "rewardStage": "AFTER_AGREEMENT", "myRewardAmount": null
                },
                {
                  "groupBuyId": 21, "groupBuyNumber": "GB-20260801-003", "groupBuyTitle": "데일리 선크림 공구",
                  "brandName": "데일리랩", "marketId": 4,
                  "periodStartAt": "2026-08-01T10:00:00", "periodEndAt": "2026-08-07T23:59:00",
                  "status": "SETTLED", "statusLabel": "정산 완료", "statusTone": "SUCCESS",
                  "confirmedSalesAmount": 5480000, "confirmedQuantity": 274, "conversionRate": 5.8,
                  "rewardStage": "CONFIRMED", "myRewardAmount": 1003600
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 3, "limit": 20, "hasNext": false}
            }
            """;

    static final String SUMMARY = """
            {
              "periodFrom": "2026-04-18", "periodTo": "2026-10-18", "periodLabel": "최근 6개월",
              "kpi": {
                "totalReward": 2536600, "averageReward": 634150, "settledCount": 4,
                "averageConversionRate": 5.4, "averageRepeatRate": 28.6, "averageFollowerBuyerRate": 45.2,
                "averagePostImpressions": 4180,
                "genderRatio": {"majorFirst": "FEMALE", "female": 83, "male": 17}
              },
              "statusCounts": {"COLLECTING": 1, "ADJUSTING": 1, "SETTLED": 4},
              "hasAnyEnded": true
            }
            """;

    static final String SUMMARY_EMPTY = """
            {
              "periodFrom": "2026-04-18", "periodTo": "2026-10-18", "periodLabel": "최근 6개월",
              "kpi": {
                "totalReward": null, "averageReward": null, "settledCount": 0,
                "averageConversionRate": null, "averageRepeatRate": null, "averageFollowerBuyerRate": null,
                "averagePostImpressions": null, "genderRatio": null
              },
              "statusCounts": {"COLLECTING": 0, "ADJUSTING": 0, "SETTLED": 0},
              "hasAnyEnded": false
            }
            """;

    static final String DETAIL = """
            {
              "groupBuy": {"groupBuyId": 21, "groupBuyNumber": "GB-20260801-003", "groupBuyTitle": "데일리 선크림 공구",
                "brandName": "데일리랩", "marketId": 4, "contractId": 70,
                "periodStartAt": "2026-08-01T10:00:00", "periodEndAt": "2026-08-07T23:59:00",
                "status": "SETTLED", "statusLabel": "정산 완료", "statusTone": "SUCCESS"},
              "notice": null,
              "rewardBar": {
                "salesReward": {"stage": "CONFIRMED", "amount": 603600, "originalAmount": 603600, "note": "확정 거래액 × 리워드율(상품별)"},
                "fixedFee": {"stage": "CONFIRMED", "amount": 400000, "originalAmount": null, "note": "브랜드 직접 지급"},
                "totalReward": {"stage": "CONFIRMED", "amount": 1003600, "originalAmount": null, "note": "판매 리워드 + 고정 지급비"}
              },
              "kpi": {
                "myReward": {"stage": "CONFIRMED", "amount": 1003600, "originalAmount": null, "note": "판매 리워드 + 고정 지급비"},
                "confirmedSales": {"value": 5480000, "basis": "구매확정 291건 기준", "estimated": false},
                "conversionRate": {"value": 5.8, "basis": "쇼룸 방문 5,010명 대비 구매확정", "estimated": false},
                "repeatRate": {"value": 28.5, "basis": "구매 소비자 288명 중 82명", "estimated": false},
                "followerDelta": {"delta": 312, "countAtEnd": 12480},
                "genderRatio": {"majorFirst": "FEMALE", "female": 83, "male": 17},
                "genderEstimated": false
              },
              "traffic": {
                "visitorCount": 5010,
                "sources": [
                  {"source": "INTERNAL", "label": "쇼룸 내부 탐색", "visitors": 1880, "ratio": 37.5},
                  {"source": "PROFILE_LINK", "label": "프로필 쇼룸 링크", "visitors": 1640, "ratio": 32.7},
                  {"source": "POST_LINK", "label": "공구 게시물 링크", "visitors": 770, "ratio": 15.4},
                  {"source": "PRODUCT_LINK", "label": "상품 상세 직접 링크", "visitors": 720, "ratio": 14.4}
                ],
                "post": {"postId": 301, "impressions": 4290, "uniqueViewers": 3140, "productViews": 1062, "productViewRate": 24.8,
                  "orders": 152, "orderRate": 14.3, "likes": 312, "shares": null}
              },
              "funnel": {"visitorCount": 5010, "orderCount": 313, "nonOrderVisitors": 4697, "confirmedOrderCount": 291,
                "cancelCount": 8, "returnCount": 13, "deliveryExceptionCount": 1,
                "confirmedRate": 93.0, "finalConversionRate": 5.8, "estimated": false},
              "sales": {
                "estimated": false,
                "products": [
                  {"productId": 501, "productName": "데일리 선크림 50ml", "detailViews": 2140, "orders": 150,
                   "confirmedQuantity": 135, "confirmedAmount": 2700000, "rewardRate": 10.0, "rewardAmount": 270000},
                  {"productId": 502, "productName": "톤업 선크림 30ml", "detailViews": 1720, "orders": 163,
                   "confirmedQuantity": 139, "confirmedAmount": 2780000, "rewardRate": 12.0, "rewardAmount": 333600}
                ],
                "contractRewardAmount": 603600,
                "adjustmentDelta": null,
                "highlights": {
                  "mostViewed": {"productId": 501, "productName": "데일리 선크림 50ml", "basis": "상세 조회 2,140회 · 전체의 55%"},
                  "bestConversion": {"productId": 502, "productName": "톤업 선크림 30ml", "basis": "조회 1,720회 중 163건 · 9.5%"},
                  "topReward": {"productId": 502, "productName": "톤업 선크림 30ml", "basis": "333,600원 · 계약 기준 판매 리워드의 55%"}
                }
              },
              "consumers": {
                "estimated": false, "buyerCount": 288, "newBuyerCount": 206, "repeatBuyerCount": 82,
                "followerBuyerCount": 130, "followerBuyerRate": 45.1,
                "distribution": {
                  "suppressed": false, "sampleSize": 288, "minimumSampleSize": 30,
                  "genders": {"female": 82.5, "male": 17.5, "femaleCount": 236, "maleCount": 50, "unknownCount": 2},
                  "ageGroups": [
                    {"label": "18–24세", "count": 96, "total": 33.3, "female": 29.2, "male": 4.2},
                    {"label": "25–34세", "count": 128, "total": 44.4, "female": 36.8, "male": 7.6},
                    {"label": "35–44세", "count": 44, "total": 15.3, "female": 11.1, "male": 4.2},
                    {"label": "45세 이상", "count": 16, "total": 5.6, "female": 4.2, "male": 1.4},
                    {"label": "기타", "count": 4, "total": 1.4, "female": 0.7, "male": 0.0}
                  ]
                }
              },
              "followers": {
                "follower": {"visitors": 2210, "postViewers": 1480, "orders": 140, "confirmedOrders": 132, "conversionRate": 6.0},
                "nonFollower": {"visitors": 2800, "postViewers": 1660, "orders": 173, "confirmedOrders": 159, "conversionRate": 5.7},
                "countAtEnd": 12480, "delta": 312, "followerVisitorRate": 44.1, "conversionEstimated": false
              },
              "reward": {
                "stage": "CONFIRMED", "confirmedSalesAmount": 5480000, "originalRewardAmount": 603600,
                "myRequest": null, "counterProposal": null, "adjustment": null,
                "finalRewardAmount": 603600, "fixedFeeAmount": 400000, "totalRewardAmount": 1003600,
                "payout": {"businessType": "INDIVIDUAL", "amount": 583682, "note": "비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고"},
                "settlementId": 26, "settlementStatusLabel": "지급 완료",
                "adjustmentThreadId": null, "adjustmentDeadlineAt": null
              },
              "recollaboration": {"marketId": 4, "threadId": 88}
            }
            """;

    static final String DETAIL_COLLECTING = """
            {
              "groupBuy": {"groupBuyId": 24, "groupBuyNumber": "GB-20260825-001", "groupBuyTitle": "수분 토너 공구",
                "brandName": "뷰티로그", "marketId": 7, "contractId": 75,
                "periodStartAt": "2026-08-25T10:00:00", "periodEndAt": "2026-08-31T23:59:00",
                "status": "COLLECTING", "statusLabel": "집계 중", "statusTone": "NEUTRAL"},
              "notice": {"tone": "NEUTRAL", "text": "미처리 반품 3건이 남아 있습니다. 아래 «예정» 표시 값은 처리 결과에 따라 줄어들 수 있습니다.",
                "link": null, "threadId": null},
              "rewardBar": {
                "salesReward": {"stage": "AFTER_SETTLEMENT", "amount": null, "originalAmount": 677400, "note": "확정 거래액 × 리워드율(상품별) · 예정"},
                "fixedFee": {"stage": "CONFIRMED", "amount": 300000, "originalAmount": null, "note": "브랜드 직접 지급"},
                "totalReward": {"stage": "AFTER_SETTLEMENT", "amount": null, "originalAmount": null, "note": "판매 리워드 + 고정 지급비"}
              },
              "kpi": {
                "myReward": {"stage": "AFTER_SETTLEMENT", "amount": null, "originalAmount": null, "note": "판매 리워드 + 고정 지급비"},
                "confirmedSales": {"value": 6120000, "basis": "구매확정 318건 기준", "estimated": true},
                "conversionRate": {"value": 6.4, "basis": "쇼룸 방문 4,980명 대비 구매확정", "estimated": true},
                "repeatRate": {"value": 24.1, "basis": "구매 소비자 315명 중 76명", "estimated": true},
                "followerDelta": {"delta": 205, "countAtEnd": 8730},
                "genderRatio": {"majorFirst": "FEMALE", "female": 79, "male": 21},
                "genderEstimated": true
              },
              "sales": {"estimated": true, "contractRewardAmount": 677400, "adjustmentDelta": null},
              "consumers": {"estimated": true},
              "followers": {"conversionEstimated": true},
              "reward": {
                "stage": "AFTER_SETTLEMENT", "confirmedSalesAmount": 6120000, "originalRewardAmount": 677400,
                "myRequest": null, "counterProposal": null, "adjustment": null,
                "finalRewardAmount": null, "fixedFeeAmount": 300000, "totalRewardAmount": null,
                "payout": {"businessType": "INDIVIDUAL", "amount": null, "note": "비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고"},
                "settlementId": null, "settlementStatusLabel": null,
                "adjustmentThreadId": null, "adjustmentDeadlineAt": null
              }
            }
            """;

    static final String DETAIL_ADJUSTING = """
            {
              "groupBuy": {"groupBuyId": 22, "groupBuyNumber": "GB-20260810-002", "groupBuyTitle": "비타민 세럼 공구",
                "brandName": "데일리랩", "marketId": 4, "contractId": 72,
                "periodStartAt": "2026-08-10T10:00:00", "periodEndAt": "2026-08-16T23:59:00",
                "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING"},
              "notice": {"tone": "WARNING",
                "text": "정산 조정 협의 중이에요. 리워드는 브랜드와 합의한 금액으로 확정되고, 합의 기한(09.10)까지 합의가 없으면 원래 금액으로 확정돼요. 판매 · 유입 · 소비자 지표는 확정값입니다.",
                "link": "ISSUE_THREAD", "threadId": 140},
              "rewardBar": {
                "salesReward": {"stage": "AFTER_AGREEMENT", "amount": null, "originalAmount": 1577040, "note": "확정 거래액 × 리워드율(상품별)"},
                "fixedFee": {"stage": "CONFIRMED", "amount": 0, "originalAmount": null, "note": "브랜드 직접 지급"},
                "totalReward": {"stage": "AFTER_AGREEMENT", "amount": null, "originalAmount": null, "note": "판매 리워드 + 고정 지급비"}
              },
              "sales": {"estimated": false, "contractRewardAmount": 1577040, "adjustmentDelta": null},
              "reward": {
                "stage": "AFTER_AGREEMENT", "confirmedSalesAmount": 10513600, "originalRewardAmount": 1577040,
                "myRequest": {"amount": 1620000, "at": "2026-09-03T11:20:00"},
                "counterProposal": {"amount": 1591512, "at": "2026-09-05T15:02:00"},
                "adjustment": null,
                "finalRewardAmount": null, "fixedFeeAmount": 0, "totalRewardAmount": null,
                "payout": {"businessType": "INDIVIDUAL", "amount": null, "note": "비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고"},
                "settlementId": 31, "settlementStatusLabel": "조정 협의",
                "adjustmentThreadId": 140, "adjustmentDeadlineAt": "2026-09-10T23:59:59"
              }
            }
            """;

    static final String DETAIL_AGREED = """
            {
              "groupBuy": {"groupBuyId": 22, "groupBuyNumber": "GB-20260810-002", "groupBuyTitle": "비타민 세럼 공구",
                "brandName": "데일리랩", "marketId": 4, "contractId": 72,
                "periodStartAt": "2026-08-10T10:00:00", "periodEndAt": "2026-08-16T23:59:00",
                "status": "SETTLED", "statusLabel": "정산 완료", "statusTone": "SUCCESS"},
              "notice": {"tone": "NEUTRAL",
                "text": "정산 조정이 합의되어 반영됐어요. 브랜드 제안 금액에 2026.09.08 양측이 동의해 판매 리워드가 1,577,040원 → 1,591,512원(+14,472원)으로 확정됐습니다.",
                "link": "ISSUE_THREAD_HISTORY", "threadId": 140},
              "rewardBar": {
                "salesReward": {"stage": "CONFIRMED", "amount": 1591512, "originalAmount": 1577040, "note": "원래 1,577,040원 + 조정 +14,472원"},
                "fixedFee": {"stage": "CONFIRMED", "amount": 0, "originalAmount": null, "note": "브랜드 직접 지급"},
                "totalReward": {"stage": "CONFIRMED", "amount": 1591512, "originalAmount": null, "note": "판매 리워드 + 고정 지급비 · 조정 합의 반영"}
              },
              "sales": {"estimated": false, "contractRewardAmount": 1577040, "adjustmentDelta": 14472},
              "reward": {
                "stage": "CONFIRMED", "confirmedSalesAmount": 10513600, "originalRewardAmount": 1577040,
                "myRequest": null, "counterProposal": null,
                "adjustment": {"delta": 14472, "agreedAt": "2026-09-08T16:40:00", "agreedProposalLabel": "브랜드 제안"},
                "finalRewardAmount": 1591512, "fixedFeeAmount": 0, "totalRewardAmount": 1591512,
                "payout": {"businessType": "INDIVIDUAL", "amount": 1538993, "note": "비사업자 · 3.3% 원천징수는 SHOWROOMZ가 신고"},
                "settlementId": 31, "settlementStatusLabel": "지급 완료",
                "adjustmentThreadId": 140, "adjustmentDeadlineAt": null
              }
            }
            """;

    static final String ERR_INVALID_MONTHS = """
            {"code": "INVALID_INPUT", "message": "months 는 3 · 6 · 12 중 하나입니다."}
            """;

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "인증 정보가 유효하지 않습니다."}
            """;

    static final String ERR_FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "접근 권한이 없습니다."}
            """;

    static final String ERR_NOT_FOUND = """
            {"code": "GROUP_BUY_NOT_FOUND", "message": "존재하지 않는 공구입니다."}
            """;

    static final String ERR_NOT_READY = """
            {"code": "PERFORMANCE_NOT_READY", "message": "공구가 종료되면 성과를 열람할 수 있습니다."}
            """;
}
