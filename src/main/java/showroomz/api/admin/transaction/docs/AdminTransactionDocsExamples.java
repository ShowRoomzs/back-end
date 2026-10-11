package showroomz.api.admin.transaction.docs;

/**
 * 어드민 거래 관리(06a 주문 · 06b 반품·교환 · 06c 환불 · 06d 예외) Swagger 예시 JSON. 값은 한 주문의 흐름으로 이어진다 —
 * 주문 {@code 20261003-000123}(orderId 5012) · 하위주문 {@code -01}(deliveryGroupId 1024 · 브랜드 「데일리랩」 marketId 17) ·
 * 접수 {@code CLM-3021} · 환불 {@code RFD-918}. 기준 시각은 2026-10-10 11:00 이다.
 *
 * <p>주문 상세는 응답이 크므로 머리 · 결제 · 하위주문 블록을 상수로 나눠 이어 붙인다(컴파일 타임 상수 결합이라 어노테이션에 쓸 수 있다).
 */
final class AdminTransactionDocsExamples {

    private AdminTransactionDocsExamples() {
    }

    // ── 06a 주문 목록 ─────────────────────────────────────────────────────────

    static final String ORDER_LIST = """
            {
              "content": [
                {
                  "orderId": 5012, "orderNumber": "20261003-000123", "paidAt": "2026-10-03T10:12:00",
                  "recipientName": "김민지", "totalAmount": 54800,
                  "groups": [
                    {
                      "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "brandName": "데일리랩",
                      "status": "PREPARING", "statusLabel": "상품준비중", "statusTone": "INFO",
                      "trackingAlert": null, "trackingAlertLabel": null,
                      "shipDueAt": "2026-10-07T23:59:59", "shipOverdue": true, "cancelRequested": false
                    },
                    {
                      "deliveryGroupId": 1025, "subOrderNumber": "20261003-000123-02", "brandName": "글로우코스",
                      "status": "SHIPPING", "statusLabel": "배송중", "statusTone": "INFO",
                      "trackingAlert": null, "trackingAlertLabel": null,
                      "shipDueAt": "2026-10-08T23:59:59", "shipOverdue": false, "cancelRequested": false
                    }
                  ],
                  "attentionCount": 1
                },
                {
                  "orderId": 5009, "orderNumber": "20261002-000098", "paidAt": "2026-10-02T21:40:00",
                  "recipientName": "박서윤", "totalAmount": 27200,
                  "groups": [
                    {
                      "deliveryGroupId": 1019, "subOrderNumber": "20261002-000098-01", "brandName": "데일리랩",
                      "status": "DELIVERED", "statusLabel": "배송완료", "statusTone": "SUCCESS",
                      "trackingAlert": null, "trackingAlertLabel": null,
                      "shipDueAt": "2026-10-07T23:59:59", "shipOverdue": false, "cancelRequested": false
                    }
                  ],
                  "attentionCount": 0
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 6, "totalResults": 120, "limit": 20, "hasNext": true}
            }
            """;

    static final String ORDER_LIST_DELIVERY_ISSUE = """
            {
              "content": [
                {
                  "orderId": 4877, "orderNumber": "20260905-000311", "paidAt": "2026-09-05T13:02:00",
                  "recipientName": "이도윤", "totalAmount": 31900,
                  "groups": [
                    {
                      "deliveryGroupId": 960, "subOrderNumber": "20260905-000311-01", "brandName": "오브제뷰티",
                      "status": "SHIPPING", "statusLabel": "배송중", "statusTone": "INFO",
                      "trackingAlert": "STALLED", "trackingAlertLabel": "추적 정지",
                      "shipDueAt": "2026-09-10T23:59:59", "shipOverdue": false, "cancelRequested": false
                    }
                  ],
                  "attentionCount": 1
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false}
            }
            """;

    static final String ORDER_LIST_EMPTY = """
            {
              "content": [],
              "pageInfo": {"currentPage": 1, "totalPages": 0, "totalResults": 0, "limit": 20, "hasNext": false}
            }
            """;

    static final String ORDER_SUMMARY = """
            {"tabCounts": {"ALL": 120, "DELIVERY_ISSUE": 3, "CANCEL": 7}}
            """;

    // ── 06a 주문 상세 — 조각 ─────────────────────────────────────────────────

    private static final String O_HEAD = """
            {
              "orderId": 5012, "orderNumber": "20261003-000123", "orderStatus": "PAID", "paidAt": "2026-10-03T10:12:00",
              "consumer": {"userId": 881, "name": "김민지", "email": "minji@example.com"},
              "recipient": {
                "name": "김민지", "phone": "010-2231-4412", "zipCode": "04524",
                "address": "서울 중구 세종대로 110", "detailAddress": "3층", "memo": "문 앞에 놓아 주세요"
              },
            """;

    private static final String O_PAY = """
              "payment": {
                "paymentId": "pay_20261003_5012", "status": "PAID", "methodLabel": "신한카드",
                "amount": 54800, "cancelledAmount": 0, "paidAt": "2026-10-03T10:12:00"
              },
              "inquiryCount": 1,
              "groups": [
            """;

    private static final String O_PAY_REFUNDED = """
              "payment": {
                "paymentId": "pay_20261003_5012", "status": "PAID", "methodLabel": "신한카드",
                "amount": 54800, "cancelledAmount": 30200, "paidAt": "2026-10-03T10:12:00"
              },
              "inquiryCount": 1,
              "groups": [
            """;

    private static final String O_TAIL = """
              ]
            }
            """;

    private static final String NO_CLAIM_NO_SETTLEMENT = """
                  "activeClaims": [],
            """;

    /** 상품준비중 · 발송 기한 경과 · 자동 알림 3회 → 대행 가능. */
    private static final String G_OVERDUE = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "PREPARING", "statusLabel": "상품준비중", "statusTone": "INFO",
                  "shipping": {
                    "shipDueAt": "2026-10-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": true, "overdueNoticeCount": 3,
                    "prepareStartedAt": "2026-10-04T09:30:00", "shippedAt": null, "carrier": null, "carrierLabel": null,
                    "trackingNumber": null, "trackingAlert": null, "trackingAlertLabel": null, "lastTrackingAt": null,
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": null, "deliveredSourceLabel": null
                  },
                  "purchaseConfirm": null, "cancel": null, "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 27200, "status": "PAID", "cancelTypeLabel": null}
                  ],
                  "refunds": [],
            """ + NO_CLAIM_NO_SETTLEMENT + """
                  "history": [
                    {"eventType": "PAID", "label": "결제완료 · 신규 진입", "actorType": "SYSTEM", "detail": null, "occurredAt": "2026-10-03T10:12:00"},
                    {"eventType": "PREPARE_STARTED", "label": "준비 시작 · 소비자 취소권 종료", "actorType": "SELLER", "detail": "발주서 다운로드", "occurredAt": "2026-10-04T09:30:00"}
                  ],
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": true, "canCancel": true, "canEnqueueRefund": false,
                    "canOpenDefectClaim": false, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    /** 같은 주문의 다른 브랜드 — 정상 배송중. */
    private static final String G_SHIPPING = """
                {
                  "deliveryGroupId": 1025, "subOrderNumber": "20261003-000123-02", "marketId": 22, "brandName": "글로우코스",
                  "groupBuyNumber": "GB-2610-015",
                  "status": "SHIPPING", "statusLabel": "배송중", "statusTone": "INFO",
                  "shipping": {
                    "shipDueAt": "2026-10-08T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 0,
                    "prepareStartedAt": "2026-10-05T10:00:00", "shippedAt": "2026-10-06T16:20:00", "carrier": "CJ", "carrierLabel": "CJ대한통운",
                    "trackingNumber": "640012345678", "trackingAlert": null, "trackingAlertLabel": null, "lastTrackingAt": "2026-10-09T22:10:00",
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": null, "deliveredSourceLabel": null
                  },
                  "purchaseConfirm": null, "cancel": null, "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70212, "productName": "수분 세럼", "optionName": "30ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 24600, "status": "PAID", "cancelTypeLabel": null}
                  ],
                  "refunds": [],
            """ + NO_CLAIM_NO_SETTLEMENT + """
                  "history": [
                    {"eventType": "PAID", "label": "결제완료 · 신규 진입", "actorType": "SYSTEM", "detail": null, "occurredAt": "2026-10-03T10:12:00"},
                    {"eventType": "PREPARE_STARTED", "label": "준비 시작 · 소비자 취소권 종료", "actorType": "SELLER", "detail": "개별", "occurredAt": "2026-10-05T10:00:00"},
                    {"eventType": "INVOICE_REGISTERED", "label": "송장 등록", "actorType": "SELLER", "detail": "CJ대한통운 640012345678", "occurredAt": "2026-10-06T16:20:00"}
                  ],
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": true,
                    "canOpenDefectClaim": false, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    /** 배송중 · 추적 정지 · 마지막 추적 + 28일 경과 → 분실 / 배송완료 판정 가능. */
    private static final String G_STALLED = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "SHIPPING", "statusLabel": "배송중", "statusTone": "INFO",
                  "shipping": {
                    "shipDueAt": "2026-09-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 0,
                    "prepareStartedAt": "2026-09-04T09:30:00", "shippedAt": "2026-09-05T15:00:00", "carrier": "HANJIN", "carrierLabel": "한진택배",
                    "trackingNumber": "512398760012", "trackingAlert": "STALLED", "trackingAlertLabel": "추적 정지",
                    "lastTrackingAt": "2026-09-08T07:40:00",
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": null, "deliveredSourceLabel": null
                  },
                  "purchaseConfirm": null, "cancel": null, "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 27200, "status": "PAID", "cancelTypeLabel": null}
                  ],
                  "refunds": [],
            """ + NO_CLAIM_NO_SETTLEMENT + """
                  "history": [
                    {"eventType": "INVOICE_REGISTERED", "label": "송장 등록", "actorType": "SELLER", "detail": "한진택배 512398760012", "occurredAt": "2026-09-05T15:00:00"},
                    {"eventType": "TRACKING_STALLED", "label": "추적 정지 감지", "actorType": "TRACKER", "detail": null, "occurredAt": "2026-09-15T07:40:00"}
                  ],
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": true,
                    "canOpenDefectClaim": false, "canMarkLost": true, "canMarkDelivered": true
                  }
                }
            """;

    /** 구매확정 · 정산 반영 · 정산 후 운영자 사유 환불로 차감. */
    private static final String G_CONFIRMED = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "CONFIRMED", "statusLabel": "구매확정", "statusTone": "SUCCESS",
                  "shipping": {
                    "shipDueAt": "2026-09-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 0,
                    "prepareStartedAt": "2026-09-04T09:30:00", "shippedAt": "2026-09-05T15:00:00", "carrier": "CJ", "carrierLabel": "CJ대한통운",
                    "trackingNumber": "640012340001", "trackingAlert": null, "trackingAlertLabel": null, "lastTrackingAt": "2026-09-07T13:10:00",
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": "2026-09-07T13:10:00", "deliveredSourceLabel": "자동 확인"
                  },
                  "purchaseConfirm": {"paused": false, "remainingDays": 0, "dueAt": null, "confirmedAt": "2026-09-14T13:10:00"},
                  "cancel": null, "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 2,
                     "returnedQuantity": 0, "price": 27200, "status": "PURCHASE_CONFIRMED", "cancelTypeLabel": null}
                  ],
                  "refunds": [
                    {"refundTaskId": 919, "refundNo": "RFD-919", "source": "OPERATOR_REASON", "origin": "OPERATOR", "originLabel": "운영자 사유",
                     "amount": 27200, "status": "PENDING", "lastError": null, "executedAt": null, "createdAt": "2026-10-09T16:05:00"}
                  ],
                  "activeClaims": [],
                  "history": [
                    {"eventType": "DELIVERED", "label": "배송완료", "actorType": "TRACKER", "detail": null, "occurredAt": "2026-09-07T13:10:00"},
                    {"eventType": "PURCHASE_CONFIRMED", "label": "구매확정 · D+7 자동", "actorType": "SYSTEM", "detail": null, "occurredAt": "2026-09-14T13:10:00"},
                    {"eventType": "REFUND_ENQUEUED_BY_OPERATOR", "label": "운영자 사유 환불 편입", "actorType": "ADMIN",
                     "detail": "RFD-919 위해성 리콜 · 27,200원 · 식약처 회수 공지 2026-10-08", "occurredAt": "2026-10-09T16:05:00"}
                  ],
                  "settlement": {
                    "settlementId": 41, "settlementNumber": "STL-2610-004", "status": "PAYOUT_SCHEDULED", "statusLabel": "지급 예정",
                    "confirmedAt": "2026-10-02T10:00:00", "settledAmount": 51200, "rewardAmount": 5664,
                    "clawbacks": []
                  },
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": true,
                    "canOpenDefectClaim": true, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    /** 분실 처리 후 — 취소(LOST) · 재고 원복 없음 · PG 자동 환불 완료. */
    private static final String G_LOST = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "CANCELLED", "statusLabel": "취소", "statusTone": "NEUTRAL",
                  "shipping": {
                    "shipDueAt": "2026-09-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 0,
                    "prepareStartedAt": "2026-09-04T09:30:00", "shippedAt": "2026-09-05T15:00:00", "carrier": "HANJIN", "carrierLabel": "한진택배",
                    "trackingNumber": "512398760012", "trackingAlert": null, "trackingAlertLabel": null,
                    "lastTrackingAt": "2026-09-08T07:40:00",
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": null, "deliveredSourceLabel": null
                  },
                  "purchaseConfirm": null,
                  "cancel": {"cancelledAt": "2026-10-10T11:00:00", "cancelTypeLabel": "배송 분실 · 운영자 처리", "reasonLabel": null,
                             "reasonDetail": "한진택배 조회 결과 분실 확인(접수번호 HJ-7731)"},
                  "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 27200, "status": "CANCELLED", "cancelTypeLabel": "배송 분실 · 운영자 처리"}
                  ],
                  "refunds": [
                    {"refundTaskId": 921, "refundNo": "RFD-921", "source": "LOST_IN_TRANSIT", "origin": "PG_AUTO", "originLabel": "PG 자동",
                     "amount": 30200, "status": "DONE", "lastError": null, "executedAt": "2026-10-10T11:00:02", "createdAt": "2026-10-10T11:00:00"}
                  ],
            """ + NO_CLAIM_NO_SETTLEMENT + """
                  "history": [
                    {"eventType": "TRACKING_STALLED", "label": "추적 정지 감지", "actorType": "TRACKER", "detail": null, "occurredAt": "2026-09-15T07:40:00"},
                    {"eventType": "LOST_RESOLVED", "label": "배송 분실 처리 · PG 자동 환불", "actorType": "ADMIN",
                     "detail": "추적 정지 28일 경과 · 항목 1건 · 한진택배 조회 결과 분실 확인(접수번호 HJ-7731)", "occurredAt": "2026-10-10T11:00:00"},
                    {"eventType": "REFUND_EXECUTED", "label": "환불 완료", "actorType": "SYSTEM", "detail": "RFD-921 30,200원", "occurredAt": "2026-10-10T11:00:02"}
                  ],
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": false,
                    "canOpenDefectClaim": false, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    /** 배송완료 — 출처와 이력만 바꿔 두 조치(추적 정지 배송완료 판정 · B3 정정)의 결과로 쓴다. */
    private static final String G_DELIVERED_HEAD = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "DELIVERED", "statusLabel": "배송완료", "statusTone": "SUCCESS",
                  "shipping": {
                    "shipDueAt": "2026-09-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 0,
                    "prepareStartedAt": "2026-09-04T09:30:00", "shippedAt": "2026-10-06T15:00:00", "carrier": "CJ", "carrierLabel": "CJ대한통운",
                    "trackingNumber": "640012340001", "trackingAlert": null, "trackingAlertLabel": null, "lastTrackingAt": "2026-10-07T09:10:00",
                    "returnDetectedAt": null, "returnCompletedAt": null,
            """;

    private static final String G_DELIVERED_BODY = """
                  "purchaseConfirm": {"paused": false, "remainingDays": 5, "dueAt": "2026-10-15T15:00:00", "confirmedAt": null},
                  "cancel": null, "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 27200, "status": "PAID", "cancelTypeLabel": null}
                  ],
                  "refunds": [],
            """ + NO_CLAIM_NO_SETTLEMENT;

    private static final String G_DELIVERED_ACTIONS = """
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": true, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": true,
                    "canOpenDefectClaim": true, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    private static final String G_MARK_DELIVERED = G_DELIVERED_HEAD + """
                    "deliveredAt": "2026-10-08T15:00:00", "deliveredSourceLabel": "운영자 처리 · 추적 정지"
                  },
            """ + G_DELIVERED_BODY + """
                  "history": [
                    {"eventType": "TRACKING_STALLED", "label": "추적 정지 감지", "actorType": "TRACKER", "detail": null, "occurredAt": "2026-09-15T07:40:00"},
                    {"eventType": "DELIVERED", "label": "배송완료", "actorType": "ADMIN",
                     "detail": "운영자 처리 · 추적 정지 28일 경과 · 소비자 1:1 문의로 수령 확인", "occurredAt": "2026-10-10T11:00:00"}
                  ],
            """ + G_DELIVERED_ACTIONS;

    private static final String G_CORRECTED = G_DELIVERED_HEAD + """
                    "deliveredAt": "2026-10-08T15:00:00", "deliveredSourceLabel": "운영자 정정"
                  },
            """ + G_DELIVERED_BODY + """
                  "history": [
                    {"eventType": "DELIVERED", "label": "배송완료", "actorType": "TRACKER", "detail": null, "occurredAt": "2026-10-07T09:10:00"},
                    {"eventType": "DELIVERED_AT_CORRECTED", "label": "배송완료일 정정", "actorType": "ADMIN",
                     "detail": "2026-10-07 → 2026-10-08 · 소비자 수령일 이의 · 택배사 확인", "occurredAt": "2026-10-10T11:00:00"}
                  ],
            """ + G_DELIVERED_ACTIONS;

    /** B4 대행 송장 등록 후 — 배송중. */
    private static final String G_SHIPPED_BY_ADMIN = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "SHIPPING", "statusLabel": "배송중", "statusTone": "INFO",
                  "shipping": {
                    "shipDueAt": "2026-10-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 3,
                    "prepareStartedAt": "2026-10-04T09:30:00", "shippedAt": "2026-10-10T11:00:00", "carrier": "CJ", "carrierLabel": "CJ대한통운",
                    "trackingNumber": "640012345690", "trackingAlert": null, "trackingAlertLabel": null, "lastTrackingAt": null,
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": null, "deliveredSourceLabel": null
                  },
                  "purchaseConfirm": null, "cancel": null, "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 27200, "status": "PAID", "cancelTypeLabel": null}
                  ],
                  "refunds": [],
            """ + NO_CLAIM_NO_SETTLEMENT + """
                  "history": [
                    {"eventType": "PREPARE_STARTED", "label": "준비 시작 · 소비자 취소권 종료", "actorType": "SELLER", "detail": "발주서 다운로드", "occurredAt": "2026-10-04T09:30:00"},
                    {"eventType": "INVOICE_REGISTERED", "label": "송장 등록", "actorType": "ADMIN",
                     "detail": "운영자 대행 · CJ대한통운 640012345690 · 자동 알림 3회 무응답 · 브랜드 유선 확인 후 송장 수령", "occurredAt": "2026-10-10T11:00:00"}
                  ],
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": true,
                    "canOpenDefectClaim": false, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    /** B4 · B5 대행 직권 취소 후 — 재고 원복 · PG 자동 환불(배송비 포함). */
    private static final String G_CANCELLED_BY_ADMIN = """
                {
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "marketId": 17, "brandName": "데일리랩",
                  "groupBuyNumber": "GB-2610-012",
                  "status": "CANCELLED", "statusLabel": "취소", "statusTone": "NEUTRAL",
                  "shipping": {
                    "shipDueAt": "2026-10-07T23:59:59", "shipDueBusinessDays": 3, "shipOverdue": false, "overdueNoticeCount": 3,
                    "prepareStartedAt": "2026-10-04T09:30:00", "shippedAt": null, "carrier": null, "carrierLabel": null,
                    "trackingNumber": null, "trackingAlert": null, "trackingAlertLabel": null, "lastTrackingAt": null,
                    "returnDetectedAt": null, "returnCompletedAt": null, "deliveredAt": null, "deliveredSourceLabel": null
                  },
                  "purchaseConfirm": null,
                  "cancel": {"cancelledAt": "2026-10-10T11:00:00", "cancelTypeLabel": "브랜드 직권 취소", "reasonLabel": "품절",
                             "reasonDetail": "브랜드 재고 소진으로 발송이 어려워 주문을 취소했습니다. 결제 금액은 전액 환불됩니다."},
                  "cancelRequest": null,
                  "items": [
                    {"orderProductId": 70211, "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                     "returnedQuantity": 0, "price": 27200, "status": "CANCELLED", "cancelTypeLabel": "브랜드 직권 취소"}
                  ],
                  "refunds": [
                    {"refundTaskId": 922, "refundNo": "RFD-922", "source": "SELLER_DIRECT_CANCEL", "origin": "PG_AUTO", "originLabel": "PG 자동",
                     "amount": 30200, "status": "DONE", "lastError": null, "executedAt": "2026-10-10T11:00:02", "createdAt": "2026-10-10T11:00:00"}
                  ],
            """ + NO_CLAIM_NO_SETTLEMENT + """
                  "history": [
                    {"eventType": "PREPARE_STARTED", "label": "준비 시작 · 소비자 취소권 종료", "actorType": "SELLER", "detail": "발주서 다운로드", "occurredAt": "2026-10-04T09:30:00"},
                    {"eventType": "CANCELLED_BY_SELLER", "label": "브랜드 직권 취소", "actorType": "ADMIN",
                     "detail": "운영자 대행 · 품절 · 브랜드 재고 소진으로 발송이 어려워 주문을 취소했습니다. 결제 금액은 전액 환불됩니다.", "occurredAt": "2026-10-10T11:00:00"},
                    {"eventType": "REFUND_EXECUTED", "label": "환불 완료", "actorType": "SYSTEM", "detail": "RFD-922 30,200원", "occurredAt": "2026-10-10T11:00:02"}
                  ],
                  "settlement": null,
                  "actions": {
                    "canCorrectDeliveredAt": false, "canRegisterShipment": false, "canCancel": false, "canEnqueueRefund": false,
                    "canOpenDefectClaim": false, "canMarkLost": false, "canMarkDelivered": false
                  }
                }
            """;

    // ── 06a 주문 상세 — 완성본 ───────────────────────────────────────────────

    static final String ORDER_DETAIL_OVERDUE = O_HEAD + O_PAY + G_OVERDUE + "," + G_SHIPPING + O_TAIL;
    static final String ORDER_DETAIL_STALLED = O_HEAD + O_PAY + G_STALLED + O_TAIL;
    static final String ORDER_DETAIL_CONFIRMED = O_HEAD + O_PAY + G_CONFIRMED + O_TAIL;
    static final String ORDER_AFTER_LOST = O_HEAD + O_PAY_REFUNDED + G_LOST + O_TAIL;
    static final String ORDER_AFTER_MARK_DELIVERED = O_HEAD + O_PAY + G_MARK_DELIVERED + O_TAIL;
    static final String ORDER_AFTER_CORRECT = O_HEAD + O_PAY + G_CORRECTED + O_TAIL;
    static final String ORDER_AFTER_SHIPMENT = O_HEAD + O_PAY + G_SHIPPED_BY_ADMIN + O_TAIL;
    static final String ORDER_AFTER_CANCEL = O_HEAD + O_PAY_REFUNDED + G_CANCELLED_BY_ADMIN + O_TAIL;
    static final String ORDER_AFTER_ENQUEUE = ORDER_DETAIL_CONFIRMED;

    static final String DEFECT_CLAIM_RESPONSE = """
            {"requestId": 1207, "claimIds": [3044]}
            """;

    // ── 06a 요청 본문 ────────────────────────────────────────────────────────

    static final String REQ_MARK_LOST = """
            {"reason": "한진택배 조회 결과 분실 확인(접수번호 HJ-7731)"}
            """;

    static final String REQ_MARK_DELIVERED = """
            {"deliveredAt": "2026-10-08T15:00:00", "reason": "소비자 1:1 문의로 수령 확인"}
            """;

    static final String REQ_CORRECT_DELIVERED_AT = """
            {"deliveredAt": "2026-10-08T15:00:00", "reason": "소비자 수령일 이의 · 택배사 확인"}
            """;

    static final String REQ_SHIPMENT = """
            {"carrier": "CJ", "trackingNumber": "6400-1234-5690", "note": "자동 알림 3회 무응답 · 브랜드 유선 확인 후 송장 수령"}
            """;

    static final String REQ_CANCEL_SOLD_OUT = """
            {"reasonCode": "SOLD_OUT", "consumerMessage": "브랜드 재고 소진으로 발송이 어려워 주문을 취소했습니다. 결제 금액은 전액 환불됩니다."}
            """;

    static final String REQ_CANCEL_DEFECT = """
            {"reasonCode": "DEFECT", "consumerMessage": "해당 제품에서 위해 성분이 확인되어 판매를 중단하고 주문을 취소했습니다."}
            """;

    static final String REQ_OPERATOR_REFUND = """
            {"reason": "RECALL", "amount": 27200, "detail": "식약처 회수 공지 2026-10-08 · 해당 제조번호 전량 환불"}
            """;

    static final String REQ_DEFECT_CLAIM = """
            {
              "items": [{"orderProductId": 70211, "quantity": 1}],
              "reasonCode": "DAMAGED_OR_DEFECTIVE",
              "detail": "구매확정 후 개봉하니 용기 펌프 파손 · 내용물 누출(1:1 문의 #4531)",
              "evidenceImageUrls": ["https://cdn.example.com/inquiries/4531/1.jpg", "https://cdn.example.com/inquiries/4531/2.jpg"]
            }
            """;

    // ── 06b 반품·교환 ────────────────────────────────────────────────────────

    /** 파트너 11 행({@code claim})은 필드가 많아 주요 값만 채우고, 빈 블록은 null 로 둔다. */
    static final String CLAIM_LIST = """
            {
              "content": [
                {
                  "claim": {
                    "claimId": 3021, "claimNumber": "CLM-3021", "type": "RETURN", "typeLabel": "반품",
                    "consumerName": "김민지", "productName": "데일리 선크림", "optionName": "50ml", "exchangeOptionName": null,
                    "productLabel": "데일리 선크림 50ml", "shipLabel": null, "quantity": 1,
                    "reasonCode": "DAMAGED_OR_DEFECTIVE", "reasonLabel": "배송 상품 파손 및 불량", "consumerAttachmentCount": 2,
                    "openedByOperator": false, "openReason": null,
                    "status": "RECEIVED", "statusLabel": "검수 대기", "stage": "INSPECTION", "statusTone": "NEUTRAL",
                    "requestedAt": "2026-09-29T14:22:05", "stageEnteredAt": "2026-10-02T09:00:00", "elapsedDays": 8, "overdue": true,
                    "collection": {"collectionId": 1188, "size": 1, "leadClaimNumber": "CLM-3021", "carrier": "EPOST", "carrierLabel": "우체국택배",
                                   "trackingNumber": "6091234567890", "lastTrackingLabel": "배달완료", "lastTrackingAt": "2026-10-01T16:40:00",
                                   "arrivedAt": "2026-10-01T16:40:00"},
                    "receivedAt": "2026-10-02T09:00:00", "inspectDueAt": "2026-10-06T23:59:59",
                    "reshipReason": null, "reshipReasonLabel": null, "reshipCarrier": null, "reshipCarrierLabel": null,
                    "reshipTrackingNumber": null, "reshipFee": null, "rejectReasonLabel": null, "sellerEvidenceCount": 0,
                    "rejectedAt": null, "storage": null, "outcome": null, "amount": null, "completedAt": null,
                    "actions": {"canConfirmReceipt": false, "canInspect": true, "canRegisterReshipment": false},
                    "orderItems": [{"productName": "데일리 선크림", "optionName": "50ml", "orderedQuantity": 1, "claimedQuantity": 1,
                                    "price": 27200, "itemStatusLabel": "반품 신청"}],
                    "orderSummary": "1개 항목 중 1개 신청"
                  },
                  "brandName": "데일리랩", "feeBearer": "SELLER", "feeBearerLabel": "브랜드 귀책",
                  "faultChangedToSeller": false, "disputeOpen": false, "disputedAt": null
                },
                {
                  "claim": {
                    "claimId": 3008, "claimNumber": "CLM-3008", "type": "RETURN", "typeLabel": "반품",
                    "consumerName": "박서윤", "productName": "수분 세럼", "optionName": "30ml", "exchangeOptionName": null,
                    "productLabel": "수분 세럼 30ml", "shipLabel": "수분 세럼 30ml", "quantity": 1,
                    "reasonCode": "CHANGE_OF_MIND", "reasonLabel": "단순 변심", "consumerAttachmentCount": 0,
                    "openedByOperator": false, "openReason": null,
                    "status": "REJECT_HOLD", "statusLabel": "반려 보류", "stage": "REJECT_HOLD", "statusTone": "NEUTRAL",
                    "requestedAt": "2026-09-20T10:05:00", "stageEnteredAt": "2026-09-30T11:00:00", "elapsedDays": 10, "overdue": false,
                    "collection": {"collectionId": 1150, "size": 1, "leadClaimNumber": "CLM-3008", "carrier": "CJ", "carrierLabel": "CJ대한통운",
                                   "trackingNumber": "640099887766", "lastTrackingLabel": "배달완료", "lastTrackingAt": "2026-09-27T15:00:00",
                                   "arrivedAt": "2026-09-27T15:00:00"},
                    "receivedAt": "2026-09-28T09:30:00", "inspectDueAt": "2026-09-30T23:59:59",
                    "reshipReason": "REJECT_RETURN", "reshipReasonLabel": "반려 반송", "reshipCarrier": null, "reshipCarrierLabel": null,
                    "reshipTrackingNumber": null,
                    "reshipFee": {"amount": 3000, "status": "PENDING", "statusLabel": "결제 대기", "dueAt": "2026-10-14T11:00:00"},
                    "rejectReasonLabel": "개봉·사용 흔적", "sellerEvidenceCount": 3, "rejectedAt": "2026-09-30T11:00:00",
                    "storage": {"noticeCount": 1, "lastNoticeAt": "2026-10-07T10:00:00", "storageDueAt": null, "phase": "NOTICE_PENDING"},
                    "outcome": null, "amount": null, "completedAt": null,
                    "actions": {"canConfirmReceipt": false, "canInspect": false, "canRegisterReshipment": false},
                    "orderItems": [{"productName": "수분 세럼", "optionName": "30ml", "orderedQuantity": 1, "claimedQuantity": 1,
                                    "price": 24600, "itemStatusLabel": "반품 신청"}],
                    "orderSummary": "1개 항목 중 1개 신청"
                  },
                  "brandName": "글로우코스", "feeBearer": "CONSUMER", "feeBearerLabel": "소비자 귀책",
                  "faultChangedToSeller": false, "disputeOpen": true, "disputedAt": "2026-10-03T19:12:00"
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false}
            }
            """;

    static final String CLAIM_SUMMARY = """
            {
              "kpi": {"collectWait": 3, "inspection": 2, "reship": 1, "overdue": 1},
              "tabCounts": {"ALL": 12, "COLLECT_WAIT": 3, "COLLECTING": 2, "INSPECTION": 2, "RESHIP": 1, "REJECT_HOLD": 1, "DONE": 3},
              "typeCounts": {"RETURN": 7, "EXCHANGE": 5},
              "disputeCount": 1
            }
            """;

    /** 반려 보류 · 소비자 이의 접수 → B2 인용 가능. {@code claim.summary}는 목록 행과 같아 주요 값만 채운다. */
    static final String CLAIM_DETAIL_DISPUTE = """
            {
              "claim": {
                "summary": {
                  "claimId": 3008, "claimNumber": "CLM-3008", "type": "RETURN", "typeLabel": "반품",
                  "consumerName": "박서윤", "productName": "수분 세럼", "optionName": "30ml", "quantity": 1,
                  "reasonCode": "CHANGE_OF_MIND", "reasonLabel": "단순 변심",
                  "status": "REJECT_HOLD", "statusLabel": "반려 보류", "stage": "REJECT_HOLD",
                  "requestedAt": "2026-09-20T10:05:00", "rejectedAt": "2026-09-30T11:00:00",
                  "reshipFee": {"amount": 3000, "status": "PENDING", "statusLabel": "결제 대기", "dueAt": "2026-10-14T11:00:00"}
                },
                "orderNumber": "20260918-000077", "deliveryGroupId": 988, "consumerPhone": "010-****-7781",
                "reasonDetail": null, "exchangeFeeCharged": null,
                "refund": {"itemAmount": 24600, "requestDeduction": 6000, "requestExpectedAmount": 18600, "basisLabel": "상품 금액 − 왕복 배송비"},
                "consumerAttachments": [],
                "sellerEvidences": ["https://cdn.example.com/claims/3008/e1.jpg", "https://cdn.example.com/claims/3008/e2.jpg",
                                    "https://cdn.example.com/claims/3008/e3.jpg"],
                "rejectDetail": "펌프 사용 흔적 · 잔량 약 70%",
                "rejection": {"legalBasis": "ART17_2_2", "legalBasisLabel": "전자상거래법 제17조②2호 · 사용·소비로 가치 현저히 감소",
                              "consumerMessage": "사용 흔적이 확인되어 반품이 어렵습니다.", "faultChangedToSeller": false,
                              "splitFromClaimNumber": null},
                "purchaseConfirm": {"paused": true, "remainingDays": 4, "dueAt": null},
                "result": null,
                "notices": [{"seq": 1, "notifiedAt": "2026-10-07T10:00:00", "channel": "앱 알림"}],
                "history": [
                  {"eventType": "REJECTED", "eventLabel": "검수 반려", "actorType": "SELLER", "actorLabel": "브랜드",
                   "detail": "개봉·사용 흔적", "occurredAt": "2026-09-30T11:00:00"}
                ],
                "actions": {"canConfirmReceipt": false, "canPass": false, "canReject": false, "canRegisterReshipment": false,
                            "canUpdateReshipment": false}
              },
              "brandName": "글로우코스", "feeBearer": "CONSUMER", "feeBearerLabel": "소비자 귀책",
              "canAcceptDispute": true, "disputeRefundAmount": 24600,
              "canRefundUnanswered": false, "unansweredRefundAmount": null,
              "dispute": {
                "inquiryId": 4527, "content": "개봉만 했고 사용하지 않았습니다. 받았을 때부터 펌프가 눌려 있었어요.",
                "imageUrls": ["https://cdn.example.com/inquiries/4527/1.jpg"],
                "createdAt": "2026-10-03T19:12:00", "answered": false, "answeredAt": null
              },
              "inspectNotice": {"count": 0, "lastAt": null},
              "inspectOverdueBusinessDays": null
            }
            """;

    /** 입고 · 검수 대기 · 기한 경과 · 자동 알림 3회 → 검수 무응답 환불 가능. */
    static final String CLAIM_DETAIL_UNANSWERED = """
            {
              "claim": {
                "summary": {
                  "claimId": 3021, "claimNumber": "CLM-3021", "type": "RETURN", "typeLabel": "반품",
                  "consumerName": "김민지", "productName": "데일리 선크림", "optionName": "50ml", "quantity": 1,
                  "reasonCode": "DAMAGED_OR_DEFECTIVE", "reasonLabel": "배송 상품 파손 및 불량",
                  "status": "RECEIVED", "statusLabel": "검수 대기", "stage": "INSPECTION", "overdue": true,
                  "requestedAt": "2026-09-29T14:22:05", "receivedAt": "2026-10-02T09:00:00", "inspectDueAt": "2026-10-06T23:59:59"
                },
                "orderNumber": "20260925-000204", "deliveryGroupId": 1002, "consumerPhone": "010-****-4412",
                "reasonDetail": "받자마자 용기 뚜껑이 깨져 있었습니다.", "exchangeFeeCharged": null,
                "refund": {"itemAmount": 27200, "requestDeduction": 0, "requestExpectedAmount": 27200, "basisLabel": "상품 금액(브랜드 귀책 · 차감 없음)"},
                "consumerAttachments": ["https://cdn.example.com/claims/3021/c1.jpg", "https://cdn.example.com/claims/3021/c2.jpg"],
                "sellerEvidences": [], "rejectDetail": null, "rejection": null,
                "purchaseConfirm": {"paused": true, "remainingDays": 6, "dueAt": null},
                "result": null, "notices": [],
                "history": [
                  {"eventType": "RECEIVED", "eventLabel": "입고 확인", "actorType": "SELLER", "actorLabel": "브랜드",
                   "detail": null, "occurredAt": "2026-10-02T09:00:00"}
                ],
                "actions": {"canConfirmReceipt": false, "canPass": true, "canReject": true, "canRegisterReshipment": false,
                            "canUpdateReshipment": false}
              },
              "brandName": "데일리랩", "feeBearer": "SELLER", "feeBearerLabel": "브랜드 귀책",
              "canAcceptDispute": false, "disputeRefundAmount": null,
              "canRefundUnanswered": true, "unansweredRefundAmount": 27200,
              "dispute": null,
              "inspectNotice": {"count": 3, "lastAt": "2026-10-09T15:00:00"},
              "inspectOverdueBusinessDays": 3
            }
            """;

    static final String REQ_DISPUTE_ACCEPT = """
            {"detail": "소비자 제출 사진상 수령 시점 펌프 눌림 확인 · 브랜드 반려 증빙으로는 사용 여부 단정 불가"}
            """;

    static final String REQ_CLAIM_REFUND = """
            {"detail": "검수 기한 3영업일 초과 · 자동 알림 3회 무응답 · 브랜드 유선 연락 불가"}
            """;

    static final String DISPUTE_ACCEPT_RESPONSE = """
            {"refundTaskId": 918, "refundNo": "RFD-918", "amount": 24600}
            """;

    static final String CLAIM_REFUND_RESPONSE = """
            {"refundTaskId": 925, "refundNo": "RFD-925", "amount": 27200}
            """;

    // ── 06c 환불 관리 ────────────────────────────────────────────────────────

    static final String REFUND_LIST_PENDING = """
            {
              "content": [
                {
                  "refundTaskId": 918, "refundNo": "RFD-918", "orderId": 4950, "orderNumber": "20260918-000077",
                  "deliveryGroupId": 988, "subOrderNumber": "20260918-000077-01", "brandName": "글로우코스",
                  "source": "OPERATOR_REASON", "route": "OPERATOR", "sourceLabel": "반려 이의 인용", "sourceRef": "CLM-3008",
                  "origin": "OPERATOR", "originLabel": "운영자 사유",
                  "reasonLabel": "반려 이의 인용", "reasonDetail": "소비자 제출 사진상 수령 시점 펌프 눌림 확인",
                  "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "신한카드", "paymentLabel": "카드 · 원래 부분",
                  "amount": 24600, "status": "PENDING", "statusLabel": "집행 대기", "statusNote": "편입 10.09 14:20 · 김운영",
                  "attempt": 0, "lastError": null, "displayAt": "2026-10-09T14:20:00", "createdAt": "2026-10-09T14:20:00",
                  "executedAt": null, "executable": true, "voidable": false, "manuallyCompletable": true
                },
                {
                  "refundTaskId": 919, "refundNo": "RFD-919", "orderId": 5012, "orderNumber": "20261003-000123",
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "brandName": "데일리랩",
                  "source": "OPERATOR_REASON", "route": "OPERATOR", "sourceLabel": "위해성 리콜", "sourceRef": null,
                  "origin": "OPERATOR", "originLabel": "운영자 사유",
                  "reasonLabel": "위해성 리콜", "reasonDetail": "식약처 회수 공지 2026-10-08 · 해당 제조번호 전량 환불",
                  "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "신한카드", "paymentLabel": "카드 · 원래 부분",
                  "amount": 27200, "status": "PENDING", "statusLabel": "집행 대기", "statusNote": "편입 10.09 16:05 · 김운영",
                  "attempt": 0, "lastError": null, "displayAt": "2026-10-09T16:05:00", "createdAt": "2026-10-09T16:05:00",
                  "executedAt": null, "executable": true, "voidable": true, "manuallyCompletable": true
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false}
            }
            """;

    static final String REFUND_LIST_FAILED = """
            {
              "content": [
                {
                  "refundTaskId": 902, "refundNo": "RFD-902", "orderId": 4811, "orderNumber": "20260901-000045",
                  "deliveryGroupId": 931, "subOrderNumber": "20260901-000045-01", "brandName": "오브제뷰티",
                  "source": "CLAIM_RETURN_PASSED", "route": "RETURN", "sourceLabel": "반품 검수 통과", "sourceRef": "CLM-2977",
                  "origin": "PG_AUTO", "originLabel": "PG 자동",
                  "reasonLabel": null, "reasonDetail": null,
                  "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "카카오페이", "paymentLabel": "간편결제 · 원래 부분",
                  "amount": 18600, "status": "FAILED", "statusLabel": "환불 실패",
                  "statusNote": "카드사 응답 지연 · 재시도 1회 실패",
                  "attempt": 2, "lastError": "카드사 응답 지연", "displayAt": "2026-10-08T09:12:00", "createdAt": "2026-10-07T18:30:00",
                  "executedAt": null, "executable": true, "voidable": false, "manuallyCompletable": true
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false}
            }
            """;

    static final String REFUND_LIST_DONE = """
            {
              "content": [
                {
                  "refundTaskId": 922, "refundNo": "RFD-922", "orderId": 5012, "orderNumber": "20261003-000123",
                  "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "brandName": "데일리랩",
                  "source": "SELLER_DIRECT_CANCEL", "route": "CANCEL", "sourceLabel": "운영자 대행 직권 취소", "sourceRef": null,
                  "origin": "PG_AUTO", "originLabel": "PG 자동",
                  "reasonLabel": null, "reasonDetail": null,
                  "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "신한카드", "paymentLabel": "카드 · 원래 부분",
                  "amount": 30200, "status": "DONE", "statusLabel": "환불 완료", "statusNote": null,
                  "attempt": 1, "lastError": null, "displayAt": "2026-10-10T11:00:02", "createdAt": "2026-10-10T11:00:00",
                  "executedAt": "2026-10-10T11:00:02", "executable": false, "voidable": false, "manuallyCompletable": false
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 3, "totalResults": 48, "limit": 20, "hasNext": true}
            }
            """;

    static final String REFUND_SUMMARY = """
            {
              "tabs": {
                "PENDING": {"count": 2, "amount": 51800, "oldestWaitingDays": null, "days": null, "byOrigin": null},
                "FAILED": {"count": 1, "amount": 18600, "oldestWaitingDays": 2, "days": null, "byOrigin": null},
                "DONE": {"count": 48, "amount": 1325400, "oldestWaitingDays": null, "days": 30, "byOrigin": {"PG_AUTO": 45, "OPERATOR": 3}}
              },
              "badge": 3
            }
            """;

    static final String REFUND_DETAIL_DISPUTE = """
            {
              "refundTaskId": 918, "refundNo": "RFD-918",
              "order": {"orderId": 4950, "orderNumber": "20260918-000077", "deliveryGroupId": 988,
                        "subOrderNumber": "20260918-000077-01", "brandName": "글로우코스"},
              "source": {"code": "OPERATOR_REASON", "route": "OPERATOR", "label": "반려 이의 인용", "ref": "CLM-3008",
                         "claimId": 3008, "cancelRequestId": null, "collectionId": 1150},
              "origin": "OPERATOR", "originLabel": "운영자 사유",
              "reason": {"code": "DISPUTE_ACCEPTED", "label": "반려 이의 인용",
                         "detail": "소비자 제출 사진상 수령 시점 펌프 눌림 확인",
                         "requestedBy": 7, "requestedByName": "김운영", "requestedAt": "2026-10-09T14:20:00"},
              "target": {"paymentId": "pay_20260918_4950", "pgTxId": "tosspay_tx_9a81", "paymentKind": "ORIGINAL", "methodLabel": "신한카드",
                         "paymentAmount": 52100, "cancelledAmount": 0, "cancellableAmount": 52100, "amount": 24600,
                         "partial": true, "paymentStatus": "PAID"},
              "additionalPayments": [
                {"chargeId": 311, "type": "REJECT_RESHIP", "typeLabel": "반려 재발송비", "amount": 3000, "status": "VOID", "statusLabel": "소멸",
                 "note": "재발송비 결제 요청은 인용 시 취소됨", "claimPaymentId": null, "claimPaymentStatus": null}
              ],
              "settlement": {"state": "BEFORE_SETTLEMENT", "stateLabel": "정산 전 · 정산 생성 시 반영", "settlementNumber": null, "clawback": null},
              "status": "PENDING", "statusLabel": "집행 대기", "attempt": 0, "autoMaxAttempts": 2,
              "failure": null, "execution": null,
              "executable": true, "voidable": false, "manuallyCompletable": true,
              "history": [
                {"at": "2026-10-09T14:20:00", "type": "REFUND_ENQUEUED_BY_OPERATOR", "label": "운영자 사유 환불 편입", "actor": "ADMIN",
                 "actorName": "김운영", "detail": "RFD-918 반려 이의 인용 · 24,600원"}
              ]
            }
            """;

    static final String REFUND_DETAIL_FAILED = """
            {
              "refundTaskId": 902, "refundNo": "RFD-902",
              "order": {"orderId": 4811, "orderNumber": "20260901-000045", "deliveryGroupId": 931,
                        "subOrderNumber": "20260901-000045-01", "brandName": "오브제뷰티"},
              "source": {"code": "CLAIM_RETURN_PASSED", "route": "RETURN", "label": "반품 검수 통과", "ref": "CLM-2977",
                         "claimId": null, "cancelRequestId": null, "collectionId": 1102},
              "origin": "PG_AUTO", "originLabel": "PG 자동",
              "reason": null,
              "target": {"paymentId": "pay_20260901_4811", "pgTxId": "kakaopay_tx_51c0", "paymentKind": "ORIGINAL", "methodLabel": "카카오페이",
                         "paymentAmount": 21600, "cancelledAmount": 0, "cancellableAmount": 21600, "amount": 18600,
                         "partial": true, "paymentStatus": "PAID"},
              "additionalPayments": [],
              "settlement": {"state": "SETTLED", "stateLabel": "정산 후 · 차감 CLW-0003", "settlementNumber": "STL-2609-011",
                             "clawback": {"clawbackNumber": "CLW-0003", "status": "PENDING", "statusLabel": "차감 예정"}},
              "status": "FAILED", "statusLabel": "환불 실패", "attempt": 2, "autoMaxAttempts": 2,
              "failure": {
                "message": "카드사 응답 지연", "code": "PG_TIMEOUT", "at": "2026-10-08T09:12:00",
                "attempts": [
                  {"at": "2026-10-07T18:30:05", "actorType": "SYSTEM", "message": "카드사 응답 지연"},
                  {"at": "2026-10-08T09:12:00", "actorType": "SYSTEM", "message": "카드사 응답 지연"}
                ]
              },
              "execution": null,
              "executable": true, "voidable": false, "manuallyCompletable": true,
              "history": [
                {"at": "2026-10-07T18:30:05", "type": "REFUND_FAILED", "label": "환불 실패 · 운영자 확인", "actor": "SYSTEM",
                 "actorName": null, "detail": "RFD-902 카드사 응답 지연"}
              ]
            }
            """;

    static final String REQ_REFUND_VOID = """
            {"reason": "오편입 · 금액 재산정"}
            """;

    static final String REQ_REFUND_MANUAL = """
            {"pgCancellationId": "tosspay_cancel_7f3a", "note": "포트원 콘솔에서 10.09 14:20 부분 취소"}
            """;

    static final String REFUND_AFTER_VOID = """
            {
              "refundTaskId": 919, "refundNo": "RFD-919", "orderId": 5012, "orderNumber": "20261003-000123",
              "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "brandName": "데일리랩",
              "source": "OPERATOR_REASON", "route": "OPERATOR", "sourceLabel": "위해성 리콜", "sourceRef": null,
              "origin": "OPERATOR", "originLabel": "운영자 사유",
              "reasonLabel": "위해성 리콜", "reasonDetail": "식약처 회수 공지 2026-10-08 · 해당 제조번호 전량 환불",
              "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "신한카드", "paymentLabel": "카드 · 원래 부분",
              "amount": 27200, "status": "VOID", "statusLabel": "취소됨", "statusNote": "편입 철회",
              "attempt": 0, "lastError": null, "displayAt": "2026-10-10T11:00:00", "createdAt": "2026-10-09T16:05:00",
              "executedAt": null, "executable": false, "voidable": false, "manuallyCompletable": false
            }
            """;

    static final String REFUND_AFTER_MANUAL = """
            {
              "refundTaskId": 902, "refundNo": "RFD-902", "orderId": 4811, "orderNumber": "20260901-000045",
              "deliveryGroupId": 931, "subOrderNumber": "20260901-000045-01", "brandName": "오브제뷰티",
              "source": "CLAIM_RETURN_PASSED", "route": "RETURN", "sourceLabel": "반품 검수 통과", "sourceRef": "CLM-2977",
              "origin": "PG_AUTO", "originLabel": "PG 자동",
              "reasonLabel": null, "reasonDetail": null,
              "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "카카오페이", "paymentLabel": "간편결제 · 원래 부분",
              "amount": 18600, "status": "DONE", "statusLabel": "환불 완료", "statusNote": "집행 김운영 · 재확인",
              "attempt": 2, "lastError": null, "displayAt": "2026-10-10T11:00:00", "createdAt": "2026-10-07T18:30:00",
              "executedAt": "2026-10-10T11:00:00", "executable": false, "voidable": false, "manuallyCompletable": false
            }
            """;

    static final String REFUND_EXECUTE_DONE = """
            {
              "outcome": "DONE",
              "refund": {
                "refundTaskId": 918, "refundNo": "RFD-918", "orderId": 4950, "orderNumber": "20260918-000077",
                "deliveryGroupId": 988, "subOrderNumber": "20260918-000077-01", "brandName": "글로우코스",
                "source": "OPERATOR_REASON", "route": "OPERATOR", "sourceLabel": "반려 이의 인용", "sourceRef": "CLM-3008",
                "origin": "OPERATOR", "originLabel": "운영자 사유",
                "reasonLabel": "반려 이의 인용", "reasonDetail": "소비자 제출 사진상 수령 시점 펌프 눌림 확인",
                "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "신한카드", "paymentLabel": "카드 · 원래 부분",
                "amount": 24600, "status": "DONE", "statusLabel": "환불 완료", "statusNote": "집행 김운영 · 재확인",
                "attempt": 1, "lastError": null, "displayAt": "2026-10-10T11:00:03", "createdAt": "2026-10-09T14:20:00",
                "executedAt": "2026-10-10T11:00:03", "executable": false, "voidable": false, "manuallyCompletable": false
              }
            }
            """;

    static final String REFUND_EXECUTE_FAILED = """
            {
              "outcome": "FAILED",
              "refund": {
                "refundTaskId": 902, "refundNo": "RFD-902", "orderId": 4811, "orderNumber": "20260901-000045",
                "deliveryGroupId": 931, "subOrderNumber": "20260901-000045-01", "brandName": "오브제뷰티",
                "source": "CLAIM_RETURN_PASSED", "route": "RETURN", "sourceLabel": "반품 검수 통과", "sourceRef": "CLM-2977",
                "origin": "PG_AUTO", "originLabel": "PG 자동",
                "reasonLabel": null, "reasonDetail": null,
                "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "카카오페이", "paymentLabel": "간편결제 · 원래 부분",
                "amount": 18600, "status": "FAILED", "statusLabel": "환불 실패", "statusNote": "카드사 응답 지연 · 재시도 2회 실패",
                "attempt": 3, "lastError": "카드사 응답 지연", "displayAt": "2026-10-10T11:00:05", "createdAt": "2026-10-07T18:30:00",
                "executedAt": null, "executable": true, "voidable": false, "manuallyCompletable": true
              }
            }
            """;

    static final String REFUND_EXECUTE_SKIPPED = """
            {
              "outcome": "SKIPPED",
              "refund": {
                "refundTaskId": 919, "refundNo": "RFD-919", "orderId": 5012, "orderNumber": "20261003-000123",
                "deliveryGroupId": 1024, "subOrderNumber": "20261003-000123-01", "brandName": "데일리랩",
                "source": "OPERATOR_REASON", "route": "OPERATOR", "sourceLabel": "위해성 리콜", "sourceRef": null,
                "origin": "OPERATOR", "originLabel": "운영자 사유",
                "reasonLabel": "위해성 리콜", "reasonDetail": "식약처 회수 공지 2026-10-08 · 해당 제조번호 전량 환불",
                "paymentKind": "ORIGINAL", "partial": true, "paymentMethodLabel": "신한카드", "paymentLabel": "카드 · 원래 부분",
                "amount": 27200, "status": "PENDING", "statusLabel": "집행 대기", "statusNote": "편입 10.09 16:05 · 김운영",
                "attempt": 0, "lastError": null, "displayAt": "2026-10-09T16:05:00", "createdAt": "2026-10-09T16:05:00",
                "executedAt": null, "executable": true, "voidable": true, "manuallyCompletable": true
              }
            }
            """;

    // ── 06d 예외 관리 ────────────────────────────────────────────────────────

    static final String EXCEPTION_LIST_DELAY = """
            {
              "asOf": "2026-10-10T11:00:00",
              "page": {
                "content": [
                  {
                    "tab": "DELAY", "kind": "SHIP_OVERDUE", "kindLabel": "발송 기한 경과",
                    "targetNumber": "20261003-000123", "subOrderNumber": "20261003-000123-01",
                    "orderId": 5012, "deliveryGroupId": 1024, "claimId": null, "marketId": 17, "brandName": "데일리랩",
                    "dueAt": "2026-10-07T23:59:59", "dueBasisLabel": "공구 마감 + 3영업일(주문 시점 값)",
                    "basisAt": "2026-10-07T23:59:59", "basisLabel": null,
                    "elapsedHours": 59, "elapsedBusinessDays": 3, "elapsedLabel": "3영업일", "overdue": true,
                    "noticeCount": 3, "lastNoticeAt": "2026-10-10T10:00:00", "nextNoticeAt": null,
                    "actOnBehalfAvailable": true,
                    "nextStepLabel": "자동 알림 3회 무응답 · 대행 가능", "nextStepNote": "송장 대행 · 직권 취소 — 주문 상세",
                    "invoice": null, "handlerLabel": null,
                    "link": {"type": "ORDER", "orderId": 5012, "deliveryGroupId": 1024, "claimId": null}
                  },
                  {
                    "tab": "DELAY", "kind": "INSPECT_OVERDUE", "kindLabel": "검수 지연",
                    "targetNumber": "CLM-3021", "subOrderNumber": "20260925-000204-01",
                    "orderId": 4931, "deliveryGroupId": 1002, "claimId": 3021, "marketId": 17, "brandName": "데일리랩",
                    "dueAt": "2026-10-06T23:59:59", "dueBasisLabel": "입고 + 2영업일",
                    "basisAt": "2026-10-06T23:59:59", "basisLabel": null,
                    "elapsedHours": 83, "elapsedBusinessDays": 3, "elapsedLabel": "3영업일", "overdue": true,
                    "noticeCount": 3, "lastNoticeAt": "2026-10-09T15:00:00", "nextNoticeAt": null,
                    "actOnBehalfAvailable": true,
                    "nextStepLabel": "자동 알림 3회 무응답 · 운영자 환불 가능", "nextStepNote": "운영자 사유 환불 편입 — 클레임 상세(검수는 대신하지 않는다)",
                    "invoice": null, "handlerLabel": null,
                    "link": {"type": "CLAIM", "orderId": 4931, "deliveryGroupId": 1002, "claimId": 3021}
                  },
                  {
                    "tab": "DELAY", "kind": "SHIP_OVERDUE", "kindLabel": "발송 기한 경과",
                    "targetNumber": "20261004-000140", "subOrderNumber": "20261004-000140-02",
                    "orderId": 5020, "deliveryGroupId": 1033, "claimId": null, "marketId": 31, "brandName": "오브제뷰티",
                    "dueAt": "2026-10-08T23:59:59", "dueBasisLabel": "공구 마감 + 3영업일(주문 시점 값)",
                    "basisAt": "2026-10-08T23:59:59", "basisLabel": null,
                    "elapsedHours": 35, "elapsedBusinessDays": 2, "elapsedLabel": "2영업일", "overdue": true,
                    "noticeCount": 1, "lastNoticeAt": "2026-10-09T10:00:00", "nextNoticeAt": "2026-10-12T10:00:00",
                    "actOnBehalfAvailable": false,
                    "nextStepLabel": "자동 알림 대기 · 2회차 10.12", "nextStepNote": null,
                    "invoice": null, "handlerLabel": null,
                    "link": {"type": "ORDER", "orderId": 5020, "deliveryGroupId": 1033, "claimId": null}
                  }
                ],
                "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 3, "limit": 20, "hasNext": false}
              }
            }
            """;

    static final String EXCEPTION_LIST_DELIVERY = """
            {
              "asOf": "2026-10-10T11:00:00",
              "page": {
                "content": [
                  {
                    "tab": "DELIVERY", "kind": "TRACKING_STALLED", "kindLabel": "추적 정지",
                    "targetNumber": "20260905-000311", "subOrderNumber": "20260905-000311-01",
                    "orderId": 4877, "deliveryGroupId": 960, "claimId": null, "marketId": 31, "brandName": "오브제뷰티",
                    "dueAt": null, "dueBasisLabel": null,
                    "basisAt": "2026-09-08T07:40:00", "basisLabel": "집화 후 7일",
                    "elapsedHours": 771, "elapsedBusinessDays": null, "elapsedLabel": "32일 무갱신", "overdue": true,
                    "noticeCount": null, "lastNoticeAt": null, "nextNoticeAt": null,
                    "actOnBehalfAvailable": true,
                    "nextStepLabel": null, "nextStepNote": null,
                    "invoice": {"carrier": "HANJIN", "carrierLabel": "한진택배", "trackingNumber": "512398760012"},
                    "handlerLabel": "소비자·브랜드가 택배사 조회 · 28일 경과 — 운영자 판정(분실 · 배송완료) 주문 상세",
                    "link": {"type": "ORDER", "orderId": 4877, "deliveryGroupId": 960, "claimId": null}
                  },
                  {
                    "tab": "DELIVERY", "kind": "PICKUP_UNCONFIRMED", "kindLabel": "집화 확인 필요",
                    "targetNumber": "20261006-000188", "subOrderNumber": "20261006-000188-01",
                    "orderId": 5041, "deliveryGroupId": 1050, "claimId": null, "marketId": 17, "brandName": "데일리랩",
                    "dueAt": null, "dueBasisLabel": null,
                    "basisAt": "2026-10-08T17:00:00", "basisLabel": "등록 후 24시간",
                    "elapsedHours": 42, "elapsedBusinessDays": null, "elapsedLabel": "42시간", "overdue": true,
                    "noticeCount": null, "lastNoticeAt": null, "nextNoticeAt": null,
                    "actOnBehalfAvailable": false,
                    "nextStepLabel": null, "nextStepNote": null,
                    "invoice": {"carrier": "CJ", "carrierLabel": "CJ대한통운", "trackingNumber": "640012349999"},
                    "handlerLabel": "브랜드 확인 · 시스템 알림",
                    "link": {"type": "ORDER", "orderId": 5041, "deliveryGroupId": 1050, "claimId": null}
                  }
                ],
                "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false}
              }
            }
            """;

    static final String EXCEPTION_SUMMARY = """
            {
              "asOf": "2026-10-10T11:00:00",
              "tabCounts": {"DELAY": 3, "DELIVERY": 4},
              "kindCounts": {
                "SHIP_OVERDUE": 2, "INSPECT_OVERDUE": 1, "RESHIP_DELAYED": 0,
                "PICKUP_UNCONFIRMED": 1, "TRACKING_STALLED": 1, "RETURNING": 2, "COLLECTION_UNSCANNED": 0
              },
              "actOnBehalfCount": 2,
              "badge": 7,
              "badgeScope": "ALL"
            }
            """;

    // ── 오류 ─────────────────────────────────────────────────────────────────

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_NOT_BLANK = """
            {"code": "INVALID_INPUT", "message": "공백일 수 없습니다"}
            """;

    static final String ERR_PAGE_SIZE = """
            {"code": "INVALID_INPUT", "message": "페이지 크기는 1~100 사이로 입력해 주세요."}
            """;

    static final String ERR_KIND_NOT_IN_TAB = """
            {"code": "INVALID_INPUT", "message": "그 유형은 이 탭에 없습니다."}
            """;

    static final String ERR_DELIVERED_AT_RANGE = """
            {"code": "INVALID_INPUT", "message": "수령일은 발송 이후 · 지금 이전이어야 합니다."}
            """;

    static final String ERR_CLAIM_BOUND_REASON = """
            {"code": "INVALID_INPUT", "message": "반려 이의 인용 · 검수 무응답 환불은 반품·교환 상세에서 편입합니다."}
            """;

    static final String ERR_DEFECT_REASON = """
            {"code": "INVALID_INPUT", "message": "구매확정 후 하자는 파손·불량 · 오배송만 열 수 있습니다."}
            """;

    static final String ERR_DEFECT_DUPLICATE_ITEM = """
            {"code": "INVALID_INPUT", "message": "같은 상품을 두 번 선택할 수 없습니다."}
            """;

    static final String ERR_CLAIM_REASON_DETAIL_REQUIRED = """
            {"code": "CLAIM_REASON_DETAIL_REQUIRED", "message": "하자 내용과 증빙을 모두 입력해 주세요."}
            """;

    static final String ERR_INVOICE_FORMAT_INVALID = """
            {"code": "INVOICE_FORMAT_INVALID", "message": "송장번호 형식이 올바르지 않습니다. 다시 확인해 주세요."}
            """;

    static final String ERR_UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "인증 정보가 유효하지 않습니다."}
            """;

    static final String ERR_FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "접근 권한이 없습니다."}
            """;

    static final String ERR_ORDER_NOT_FOUND = """
            {"code": "ORDER_NOT_FOUND", "message": "주문을 찾을 수 없습니다."}
            """;

    static final String ERR_ORDER_GROUP_NOT_FOUND = """
            {"code": "ORDER_GROUP_NOT_FOUND", "message": "존재하지 않는 주문입니다."}
            """;

    static final String ERR_ORDER_PRODUCT_NOT_FOUND = """
            {"code": "ORDER_PRODUCT_NOT_FOUND", "message": "주문 상품을 찾을 수 없습니다."}
            """;

    static final String ERR_PAYMENT_NOT_FOUND = """
            {"code": "PAYMENT_NOT_FOUND", "message": "결제 정보를 찾을 수 없습니다."}
            """;

    static final String ERR_CLAIM_NOT_FOUND = """
            {"code": "CLAIM_NOT_FOUND", "message": "반품·교환 요청을 찾을 수 없습니다."}
            """;

    static final String ERR_REFUND_TASK_NOT_FOUND = """
            {"code": "REFUND_TASK_NOT_FOUND", "message": "환불 건을 찾을 수 없습니다."}
            """;

    static final String ERR_ORDER_STATE_CHANGED = """
            {"code": "ORDER_STATE_CHANGED", "message": "주문 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."}
            """;

    static final String ERR_NOT_DELIVERED = """
            {"code": "ORDER_STATE_CHANGED", "message": "배송완료 상태에서만 정정할 수 있습니다."}
            """;

    static final String ERR_STALLED_NOT_RESOLVABLE_LOST = """
            {"code": "ORDER_STATE_CHANGED", "message": "추적 정지 28일이 지난 배송중 건만 분실로 판정할 수 있습니다."}
            """;

    static final String ERR_STALLED_NOT_RESOLVABLE_DELIVERED = """
            {"code": "ORDER_STATE_CHANGED", "message": "추적 정지 28일이 지난 배송중 건만 배송완료로 판정할 수 있습니다."}
            """;

    static final String ERR_REFUND_BEFORE_SHIPMENT = """
            {"code": "ORDER_STATE_CHANGED", "message": "발송 전 주문은 직권 취소로 환불합니다."}
            """;

    static final String ERR_REFUND_RETURNING = """
            {"code": "ORDER_STATE_CHANGED", "message": "반송 중인 주문은 반송 완료 시 자동 환불됩니다."}
            """;

    static final String ERR_ACT_ON_BEHALF_NOT_ALLOWED = """
            {"code": "ORDER_ACT_ON_BEHALF_NOT_ALLOWED", "message": "대행 조건(발송 기한 경과 · 자동 알림 무응답)을 충족하지 않았습니다."}
            """;

    static final String ERR_INVOICE_DUPLICATE = """
            {"code": "INVOICE_DUPLICATE", "message": "이미 다른 주문에 등록된 송장번호입니다."}
            """;

    static final String ERR_CANCEL_REQUEST_PENDING_EXISTS = """
            {"code": "CANCEL_REQUEST_PENDING_EXISTS", "message": "검토 중인 취소 요청이 있습니다. 요청을 먼저 처리해 주세요."}
            """;

    static final String ERR_REFUND_AMOUNT_EXCEEDED = """
            {"code": "REFUND_AMOUNT_EXCEEDED", "message": "환불액이 취소 가능 잔액(24,900원)보다 큽니다."}
            """;

    static final String ERR_CLAIM_NOT_ELIGIBLE = """
            {"code": "CLAIM_NOT_ELIGIBLE", "message": "반품·교환을 요청할 수 없는 상품입니다."}
            """;

    static final String ERR_CLAIM_NOT_ELIGIBLE_3M = """
            {"code": "CLAIM_NOT_ELIGIBLE", "message": "배송완료 후 3개월이 지나 열 수 없습니다."}
            """;

    static final String ERR_CLAIM_QUANTITY_EXCEEDED = """
            {"code": "CLAIM_QUANTITY_EXCEEDED", "message": "요청할 수 있는 수량을 초과했습니다."}
            """;

    static final String ERR_DISPUTE_NOT_ACCEPTABLE = """
            {"code": "CLAIM_STATE_CHANGED", "message": "반려 보류 · 재발송 대기 중인 반품만 이의를 인용할 수 있습니다."}
            """;

    static final String ERR_UNANSWERED_NOT_REFUNDABLE = """
            {"code": "CLAIM_STATE_CHANGED", "message": "검수 대기 중이고 자동 알림이 대행 조건 횟수에 닿은 건만 운영자 환불로 닫을 수 있습니다."}
            """;

    static final String ERR_CLAIM_STATE_CHANGED = """
            {"code": "CLAIM_STATE_CHANGED", "message": "요청 상태가 변경되었습니다. 새로고침 후 다시 확인해 주세요."}
            """;

    static final String ERR_REFUND_TASK_NOT_EXECUTABLE = """
            {"code": "REFUND_TASK_NOT_EXECUTABLE", "message": "집행할 수 없는 환불 건입니다. 새로고침 후 다시 확인해 주세요."}
            """;

    static final String ERR_REFUND_TASK_NOT_VOIDABLE = """
            {"code": "REFUND_TASK_NOT_VOIDABLE", "message": "철회할 수 없는 환불 건입니다. 집행 전 운영자 사유 환불만 철회할 수 있습니다."}
            """;
}
