package showroomz.api.seller.performance.docs;

/**
 * 파트너센터 성과 관리(12) Swagger 예시 JSON — 시안 rev.4 의 「수분 앰플 공구」(× 데일리뷰티랩 · 지급 완료)다. 금액은 정산 행 값 그대로이고
 * 비율은 % 값(소수 1자리) · ROAS 는 % 정수다.
 */
final class SellerPerformanceDocsExamples {

    private SellerPerformanceDocsExamples() {
    }

    static final String LIST = """
            {
              "content": [
                {
                  "groupBuyId": 61, "groupBuyTitle": "데일리 선크림 공구",
                  "influencer": {"creatorId": 12, "showroomName": "데일리뷰티랩"},
                  "periodStart": "2026-08-01T10:00:00", "periodEnd": "2026-08-07T23:59:00",
                  "status": "AGGREGATING", "statusLabel": "집계 중", "statusTone": "NEUTRAL",
                  "visitors": {"value": null, "state": "PROJECTED"}, "conversionRate": {"value": null, "state": "PROJECTED"},
                  "confirmedSales": {"value": null, "state": "PROJECTED"}, "payout": {"value": null, "state": "PROJECTED"},
                  "confirmedQuantity": {"value": null, "state": "PROJECTED"}, "confirmRate": {"value": null, "state": "PROJECTED"},
                  "detailAvailable": true
                },
                {
                  "groupBuyId": 58, "groupBuyTitle": "수분 토너 공구",
                  "influencer": {"creatorId": 9, "showroomName": "뷰티로그"},
                  "periodStart": "2026-07-25T10:00:00", "periodEnd": "2026-07-31T23:59:00",
                  "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                  "visitors": {"value": 3120, "state": "FINAL"}, "conversionRate": {"value": 4.9, "state": "FINAL"},
                  "confirmedSales": {"value": 2940000, "state": "FINAL"},
                  "payout": {"value": null, "state": "NEGOTIATING", "original": 2531340, "basis": "리워드 합의 후 확정"},
                  "confirmedQuantity": {"value": 147, "state": "FINAL"}, "confirmRate": {"value": 92.0, "state": "FINAL"},
                  "detailAvailable": true
                },
                {
                  "groupBuyId": 55, "groupBuyTitle": "수분 앰플 공구",
                  "influencer": {"creatorId": 12, "showroomName": "데일리뷰티랩"},
                  "periodStart": "2026-07-18T10:00:00", "periodEnd": "2026-07-25T23:59:00",
                  "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS",
                  "visitors": {"value": 5010, "state": "FINAL"}, "conversionRate": {"value": 5.8, "state": "FINAL"},
                  "confirmedSales": {"value": 5480000, "state": "FINAL"},
                  "payout": {"value": 4712800, "state": "FINAL", "basis": "PG·리워드·리워드 부가세 차감 후 실지급 · 정산 관리 기준"},
                  "confirmedQuantity": {"value": 274, "state": "FINAL"}, "confirmRate": {"value": 93.0, "state": "FINAL"},
                  "detailAvailable": true
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 3, "limit": 20, "hasNext": false},
              "listPeriod": {"from": "2026-07-18", "to": "2026-08-07"}
            }
            """;

    static final String SUMMARY = """
            {
              "population": {"count": 2, "statuses": ["PAYOUT_SCHEDULED", "PAID"], "breakdown": {"PAYOUT_SCHEDULED": 1, "PAID": 1}},
              "confirmedSales": {"value": 9190000, "state": "FINAL", "basis": "정산 2건 합산(지급 예정 1 · 지급 완료 1)"},
              "visitors": {"value": 8640, "state": "FINAL", "basis": "2건 누적 · 고유 방문"},
              "avgAov": {"value": 18755, "state": "FINAL", "basis": "확정 거래액 ÷ 확정 주문 490건"},
              "avgConversionRate": {"value": 5.7, "state": "FINAL", "basis": "방문 8,640명 대비 구매확정 490건"},
              "avgRoas": {"value": 550, "state": "FINAL", "basis": "확정 거래액 ÷ 총 집행비 1,669,000원"},
              "genderRatio": {"female": 80, "male": 20, "basis": "정산 2건 합산(지급 예정 1 · 지급 완료 1)"},
              "avgConfirmRate": {"value": 91.4, "state": "FINAL", "basis": "주문 536건 중 490건 확정"},
              "avgRepurchaseRate": {"value": 29.0, "state": "FINAL", "basis": "구매확정 490명 중 재구매 142명"},
              "statusCounts": {"AGGREGATING": 3, "REVIEWING": 0, "ADJUSTING": 1, "PAYOUT_SCHEDULED": 1, "PAID": 1}
            }
            """;

    static final String SUMMARY_EMPTY = """
            {
              "population": {"count": 0, "statuses": ["PAYOUT_SCHEDULED", "PAID"], "breakdown": {"PAYOUT_SCHEDULED": 0, "PAID": 0}},
              "confirmedSales": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "visitors": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "avgAov": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "avgConversionRate": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "avgRoas": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "genderRatio": {"female": null, "male": null, "basis": "집계된 공구 없음"},
              "avgConfirmRate": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "avgRepurchaseRate": {"value": null, "state": "FINAL", "basis": "집계된 공구 없음"},
              "statusCounts": {"AGGREGATING": 2, "REVIEWING": 0, "ADJUSTING": 0, "PAYOUT_SCHEDULED": 0, "PAID": 0}
            }
            """;

    static final String DETAIL = """
            {
              "groupBuyId": 55, "groupBuyTitle": "수분 앰플 공구",
              "influencer": {"creatorId": 12, "showroomName": "데일리뷰티랩"}, "connectionId": 40,
              "periodStart": "2026-07-18T10:00:00", "periodEnd": "2026-07-25T23:59:00",
              "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS", "aggregating": null,
              "contract": {"contractId": 70, "fixedFeeAmount": 400000, "fixedFeeTriggerLabel": "공구 게시물 등록 후",
                "fixedFeeText": "고정 지급비 400,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급", "rewardRateLabel": "10%",
                "reward": {"value": 548000, "state": "FINAL"}, "totalSpend": {"value": 948000, "state": "FINAL", "basis": "고정 지급비 + 리워드"},
                "periodDays": 8},
              "kpi": {
                "confirmedSales": {"value": 5480000, "state": "FINAL", "basis": "구매확정 291건 기준"},
                "payout": {"value": 4712800, "state": "FINAL", "basis": "PG·리워드·리워드 부가세 차감 후 실지급 · 정산 관리 기준"},
                "conversionRate": {"value": 5.8, "state": "FINAL", "basis": "쇼룸 방문 5,010명 대비 구매확정"},
                "confirmRate": {"value": 93.0, "state": "FINAL", "basis": "주문 313건 중 291건 확정"},
                "confirmedQuantity": {"value": 274, "state": "FINAL", "basis": "주문 수량 326개"}, "orderedQuantity": 326,
                "aov": {"value": 18832, "state": "FINAL", "basis": "확정 거래액 ÷ 확정 주문 291건"},
                "roas": {"value": 578, "state": "FINAL", "basis": "확정 거래액 ÷ 총 집행비 948,000원"},
                "repurchaseRate": {"value": 30.2, "state": "FINAL", "basis": "구매확정 291명 중 재구매 88명"}
              },
              "traffic": {"visitorCount": 5010, "visitCount": 6120,
                "sources": [{"source": "INTERNAL", "label": "쇼룸 내부 탐색", "count": 1880, "ratio": 37.5},
                            {"source": "PROFILE_LINK", "label": "프로필 쇼룸 링크", "count": 1640, "ratio": 32.7},
                            {"source": "POST_LINK", "label": "공구 게시물 링크", "count": 770, "ratio": 15.4},
                            {"source": "PRODUCT_LINK", "label": "상품 상세 직접 링크", "count": 720, "ratio": 14.4}],
                "post": {"postId": 301, "impressionCount": 4290, "uniqueViewerCount": 3140,
                  "productViewCount": {"value": 1062, "state": "FINAL", "basis": "조회 대비 24.8%"},
                  "orderCount": {"value": 152, "state": "FINAL", "basis": "상세 대비 14.3%"}, "likeCount": 312,
                  "shareCount": {"value": null, "state": "NOT_AVAILABLE", "basis": "집계 준비 중"}},
                "funnel": {"visitors": 5010, "orders": 313, "notOrdered": 4697, "orderRate": 6.2, "shipped": 305, "delivered": 303,
                  "confirmed": {"value": 291, "state": "FINAL"}, "shippedRate": 97.4, "deliveredRate": 96.8, "confirmedRate": 93.0,
                  "cancelled": 8, "returned": 13, "deliveryException": 1,
                  "conversionRate": {"value": 5.8, "state": "FINAL", "basis": "방문 대비 최종 구매전환율"}}},
              "consumers": {"estimated": false, "confirmedOrderCount": 291, "confirmedBuyerCount": 291,
                "newBuyerCount": {"value": 203, "state": "FINAL"}, "repurchaseBuyerCount": {"value": 88, "state": "FINAL"},
                "followerShare": {"value": 44.0, "state": "FINAL"},
                "acquisitionCost": {"value": 4670, "state": "FINAL", "basis": "총 집행비 948,000원 ÷ 신규 소비자 203명"},
                "gender": {"female": 239, "male": 52, "unknown": 0, "femaleRatio": 82.1, "maleRatio": 17.9},
                "ageBands": [{"band": "TWENTIES", "label": "20대", "female": 104, "male": 15, "total": 119, "ratio": 40.9, "merged": false}]},
              "cost": {"editable": true, "lockedReason": null,
                "items": [{"contractItemId": 81, "productName": "수분 앰플 30ml", "confirmedQuantity": {"value": 220, "state": "FINAL"}, "unitCost": 7200, "subtotal": 1584000}],
                "packagingCost": 219200, "shippingCost": 918000, "returnDisposalCost": 92000, "etcCost": 0,
                "totalCost": {"value": 3088600, "state": "FINAL"},
                "profit": {"value": 1224200, "state": "FINAL", "basis": "정산액 − 총 원가 − 고정 지급비"},
                "profitRate": {"value": 22.3, "state": "FINAL", "basis": "확정 거래액 대비"}, "updatedAt": "2026-08-06T14:20:00"},
              "settlement": {"settlementId": 26, "status": "PAID", "statusLabel": "지급 완료", "statusTone": "SUCCESS",
                "payout": {"value": 4712800, "state": "FINAL"}, "reviewDueAt": "2026-07-31T23:59:59",
                "payoutDueDate": "2026-08-05", "paidAt": "2026-08-05T10:00:00", "adjustment": null}
            }
            """;

    static final String DETAIL_AGGREGATING = """
            {
              "groupBuyId": 61, "groupBuyTitle": "데일리 선크림 공구",
              "influencer": {"creatorId": 12, "showroomName": "데일리뷰티랩"}, "connectionId": 40,
              "periodStart": "2026-08-01T10:00:00", "periodEnd": "2026-08-07T23:59:00",
              "status": "AGGREGATING", "statusLabel": "집계 중", "statusTone": "NEUTRAL",
              "aggregating": {"unclosedCount": 4, "openReturnCount": 3,
                "unclosedStages": [{"stage": "RETURNING", "label": "반송중", "count": 3}, {"stage": "SHIPPING", "label": "배송중", "count": 1}]},
              "contract": {"contractId": 74, "fixedFeeAmount": 300000, "fixedFeeTriggerLabel": "공구 게시물 등록 후",
                "fixedFeeText": "고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급", "rewardRateLabel": "10%",
                "reward": {"value": 612000, "state": "PROJECTED", "basis": "상품별 리워드율 × 확정 수량"},
                "totalSpend": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "정산 후 확정"}, "periodDays": 7},
              "kpi": {
                "confirmedSales": {"value": 6120000, "state": "PROJECTED", "basis": "구매확정 예정 318건 기준"},
                "payout": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "반품 처리가 끝나면 산출됩니다"},
                "conversionRate": {"value": 6.4, "state": "PROJECTED", "basis": "쇼룸 방문 4,980명 대비 구매확정"},
                "roas": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "확정 거래액이 확정되면 산출됩니다"}
              },
              "breakdown": {"pendingReturnCount": 3},
              "consumers": {"estimated": true,
                "acquisitionCost": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "총 집행비가 확정되면 산출됩니다"}},
              "cost": {"editable": false, "lockedReason": "AGGREGATING",
                "totalCost": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "확정 수량이 확정되면 산출됩니다"},
                "profit": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "정산액이 확정되면 산출됩니다"},
                "profitRate": {"value": null, "state": "PENDING_SETTLEMENT", "basis": "정산액이 확정되면 산출됩니다"}},
              "settlement": null
            }
            """;

    static final String DETAIL_ADJUSTING = """
            {
              "groupBuyId": 58, "groupBuyTitle": "수분 토너 공구",
              "influencer": {"creatorId": 9, "showroomName": "뷰티로그"}, "connectionId": 37,
              "periodStart": "2026-07-25T10:00:00", "periodEnd": "2026-07-31T23:59:00",
              "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING", "aggregating": null,
              "contract": {"contractId": 71, "fixedFeeAmount": null, "fixedFeeTriggerLabel": null, "fixedFeeText": null, "rewardRateLabel": "10%",
                "reward": {"value": null, "state": "NEGOTIATING", "original": 294000, "basis": "조정 협의 중"},
                "totalSpend": {"value": null, "state": "NEGOTIATING", "original": 294000, "basis": "합의 후 변동"}, "periodDays": 7},
              "kpi": {
                "confirmedSales": {"value": 2940000, "state": "FINAL", "basis": "구매확정 152건 기준"},
                "payout": {"value": null, "state": "NEGOTIATING", "original": 2531340, "basis": "리워드 합의 후 확정"},
                "roas": {"value": null, "state": "NEGOTIATING", "original": 1000, "basis": "총 집행비가 리워드 합의로 바뀜"}
              },
              "cost": {"editable": true, "lockedReason": null,
                "profit": {"value": null, "state": "NEGOTIATING", "original": 812340, "basis": "정산액 확정 후 산출"},
                "profitRate": {"value": null, "state": "NEGOTIATING", "original": 27.6, "basis": "정산액 확정 후 산출"}},
              "settlement": {"settlementId": 29, "status": "ADJUSTING", "statusLabel": "조정 협의", "statusTone": "WARNING",
                "payout": {"value": null, "state": "NEGOTIATING", "original": 2531340, "basis": "리워드 합의 후 확정"},
                "reviewDueAt": "2026-08-07T23:59:59", "payoutDueDate": null, "paidAt": null,
                "adjustment": {"adjustmentId": 12, "threadId": 140, "status": "OPEN", "statusLabel": "협의 중", "requesterType": "CREATOR",
                  "deadlineAt": "2026-08-21T23:59:59", "remainingBusinessDays": 6, "originalRewardAmount": 294000,
                  "myLatestAmount": null, "counterpartLatestAmount": 330000, "turn": "MY_TURN", "turnLabel": "내 응답 필요"}}
            }
            """;

    static final String COST_REQUEST = """
            {
              "items": [{"contractItemId": 81, "unitCost": 7200}, {"contractItemId": 82, "unitCost": 5100}],
              "packagingCost": 219200, "shippingCost": 918000, "returnDisposalCost": 92000, "etcCost": 0
            }
            """;

    static final String ERR_NOT_FOUND = """
            {"code": "GROUP_BUY_NOT_FOUND", "message": "존재하지 않는 공구입니다."}
            """;

    static final String ERR_COST_LOCKED = """
            {"code": "PERFORMANCE_COST_LOCKED", "message": "집계가 끝나면 원가를 입력할 수 있습니다."}
            """;

    static final String ERR_COST_ITEM_MISMATCH = """
            {"code": "PERFORMANCE_COST_ITEM_MISMATCH", "message": "계약 상품 목록과 원가 항목이 일치하지 않습니다."}
            """;

    static final String ERR_COST_AMOUNT = """
            {"code": "INVALID_INPUT", "message": "부자재비은(는) 0원 이상 10억 원 이하로 입력해 주세요."}
            """;

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "size 는 1 ~ 100 이어야 합니다."}
            """;

    static final String ERR_UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "인증 정보가 유효하지 않습니다."}
            """;

    static final String ERR_FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "접근 권한이 없습니다."}
            """;
}
