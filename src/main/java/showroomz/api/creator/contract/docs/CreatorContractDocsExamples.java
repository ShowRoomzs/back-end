package showroomz.api.creator.contract.docs;

/** Swagger UI에서 계약 API의 응답 형태를 바로 확인하기 위한 예시. */
final class CreatorContractDocsExamples {

    private CreatorContractDocsExamples() {
    }

    static final String LIST = """
            {
              "content": [{
                "contractId": 128, "contractNumber": "CTR-20260813-001",
                "title": "여름 수분 세럼 공구", "brandName": "퓨어랩", "itemCount": 2,
                "startAt": "2026-09-01T00:00:00", "endAt": "2026-09-17T23:59:59",
                "receivedAt": "2026-08-13T10:00:00", "status": "SIGNING",
                "statusLabel": "서명 진행중", "statusTone": "INFO",
                "deadline": {"type": "DEADLINE", "deadlineAt": "2026-08-20T23:59:59", "tone": "WARNING"}
              }],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false}
            }
            """;

    static final String LIST_EMPTY = """
            {
              "content": [],
              "pageInfo": {"currentPage": 1, "totalPages": 0, "totalResults": 0, "limit": 20, "hasNext": false}
            }
            """;

    static final String SUMMARY = """
            {
              "tabCounts": {"ALL": 8, "SIGNING": 2, "CONCLUSION_PENDING": 1, "CONCLUDED": 2, "CLOSED": 3},
              "actionRequiredCount": 1
            }
            """;

    static final String DETAIL = """
            {
              "contractId": 128, "contractNumber": "CTR-20260813-001", "title": "여름 수분 세럼 공구",
              "status": "SIGNING", "statusLabel": "서명 진행중", "statusTone": "INFO",
              "receivedAt": "2026-08-13T10:00:00",
              "brand": {"marketId": 31, "name": "퓨어랩", "threadId": 4012, "connected": true},
              "period": {"startAt": "2026-09-01T00:00:00", "endAt": "2026-09-17T23:59:59", "days": 17},
              "stepper": {"reviewApprovedAt": "2026-08-13T09:30:00", "signatureRequestedAt": "2026-08-13T10:00:00", "signedCount": 0, "concludedAt": null},
              "signature": {"deadlineAt": "2026-08-20T23:59:59", "brandSignedAt": null, "creatorSignedAt": null, "asOf": "2026-08-13T10:00:00"},
              "payout": {"fixedFeeAmount": 1200000, "fixedFeeTrigger": "POST_REGISTERED", "fixedFeeTriggerLabel": "공구 게시물 등록 후",
                "rewardRates": [{"productName": "수분진정 세럼", "rate": 15.0}],
                "settlementTiming": "GROUP_BUY_ENDED", "platformGuaranteed": false,
                "disputeChannel": {"threadId": 4012}},
              "content": {"feedCount": 1, "reelsCount": 0, "storyCount": 2, "dueDate": "2026-08-31",
                "secondaryUseAllowed": false, "secondaryUsePeriodType": null, "secondaryUseMonths": null,
                "preReview": false, "note": null, "obligationAlive": true},
              "items": [{"contractItemId": 501, "productId": 21, "productName": "수분진정 세럼",
                "regularPrice": 35000, "groupBuyPrice": 28000, "myRewardRate": 15.0,
                "expectedUnitReward": 4200, "brandSupplyQuantity": 300,
                "options": [{"variantId": 301, "variantName": "단품", "regularPrice": 35000, "optionExtraPrice": 0,
                             "salePrice": 28000, "brandSupplyQuantity": 200},
                            {"variantId": 302, "variantName": "2개 세트", "regularPrice": 65000, "optionExtraPrice": 30000,
                             "salePrice": 58000, "brandSupplyQuantity": 100}]}],
              "fixedFee": {"amount": 1200000, "trigger": "POST_REGISTERED", "triggerLabel": "공구 게시물 등록 후", "paymentState": "NOT_YET"},
              "settlement": {"platformFeeRate": 2, "pgFeeRate": null, "withholdingType": null, "withholdingLabel": null},
              "closure": {"closedAt": null, "actorType": null, "reasonCode": null, "reasonLabel": null, "memo": null},
              "groupBuy": {"groupBuyId": null, "awaitingBrandCreation": false},
              "documents": [{"documentType": "GENERATED_DRAFT", "documentTypeLabel": "계약서 생성본",
                "downloadUrl": "https://example.com/contract-draft.pdf"}],
              "permissions": {"canDecline": true, "canRequestResend": true, "canOpenThread": true,
                "canDownloadDocuments": false, "canOpenGroupBuy": false},
              "history": [], "navigation": {"prevContractId": null, "nextContractId": null}
            }
            """;

    static final String CLAUSES = """
            {
              "clauseVersionId": 1, "versionNumber": "1.0", "effectiveDate": "2026-08-01",
              "clauses": [{"code": "PRICE_POLICY", "summaryTitle": "가격 정책",
                "summaryDescription": "공구 가격과 판매 조건", "fullTitle": "제1조 가격 정책",
                "fullBody": "계약에 확정된 가격 조건을 적용합니다."}]
            }
            """;

    static final String DOCUMENT = """
            {
              "documentType": "SIGNED_PDF", "documentTypeLabel": "서명 완료 계약서",
              "downloadUrl": "https://example.com/signed-contract.pdf",
              "originalName": "CTR-20260813-001_서명완료.pdf", "sizeBytes": 1200000,
              "contentType": "application/pdf", "uploadedAt": "2026-08-21T11:00:00"
            }
            """;

    static final String DECLINE_REQUEST = """
            {"reasonCode": "SCHEDULE_MISMATCH", "memo": "이번 일정에는 참여하기 어렵습니다."}
            """;

    static final String RESEND = """
            {"resendRequestId": 42, "requestedAt": "2026-08-17T11:00:00", "alreadyRequested": false}
            """;
}
