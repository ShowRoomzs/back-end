package showroomz.api.creator.groupbuy.docs;

/**
 * 스튜디오 공구 Swagger 응답 예제. 상세 응답({@code CreatorGroupBuyDetailResponse})을 조회 1개 + 실행 7개가 함께 쓰므로
 * 상태별 예제를 한곳에 모은다. 어노테이션 값이라 컴파일 타임 상수(텍스트 블록 연결)로만 만든다.
 *
 * <p>시나리오 하나(공구 41 · 브랜드 「글로우랩」 · 쇼룸 「민지의 쇼룸」)를 상태별로 이어 그렸다. 판매 모듈·정산 모듈 연동 전이라
 * {@code sales} · {@code orderClosure} · {@code payout.salesReward} · {@code settlement.confirmedReward}는 실제 응답처럼 null로 둔다.
 */
final class CreatorGroupBuyDocsExamples {

    private CreatorGroupBuyDocsExamples() {
    }

    // ── 목록 · 요약 ──────────────────────────────────────────────────────────

    static final String LIST = """
            {
              "content": [
                {
                  "groupBuyId": 52,
                  "groupBuyNumber": "GB-20260810-052",
                  "title": "가을 립 틴트 공구",
                  "brandName": "모노코스",
                  "itemCount": 3,
                  "startAt": "2026-08-25T10:00:00",
                  "endAt": "2026-08-31T23:55:00",
                  "postStatus": "NOT_WRITTEN",
                  "postStatusLabel": "미작성",
                  "postStatusTone": "NEUTRAL",
                  "status": "PREPARING",
                  "statusLabel": "준비중",
                  "statusTone": "NEUTRAL",
                  "actionRequired": true
                },
                {
                  "groupBuyId": 41,
                  "groupBuyNumber": "GB-20260803-041",
                  "title": "여름 수분 세럼 공구",
                  "brandName": "글로우랩",
                  "itemCount": 2,
                  "startAt": "2026-08-14T10:00:00",
                  "endAt": "2026-08-20T23:55:00",
                  "postStatus": "EXPOSED",
                  "postStatusLabel": "노출중",
                  "postStatusTone": "SUCCESS",
                  "status": "IN_PROGRESS",
                  "statusLabel": "진행중",
                  "statusTone": "SUCCESS",
                  "actionRequired": true
                },
                {
                  "groupBuyId": 38,
                  "groupBuyNumber": "GB-20260728-038",
                  "title": "선크림 앵콜 공구",
                  "brandName": "선데이랩",
                  "itemCount": 1,
                  "startAt": "2026-08-12T10:00:00",
                  "endAt": "2026-08-18T23:55:00",
                  "postStatus": "EXPOSED",
                  "postStatusLabel": "노출중",
                  "postStatusTone": "SUCCESS",
                  "status": "SUSPENSION_SCHEDULED",
                  "statusLabel": "중단 예정",
                  "statusTone": "WARNING",
                  "actionRequired": false
                },
                {
                  "groupBuyId": 27,
                  "groupBuyNumber": "GB-20260702-027",
                  "title": "클렌징 오일 공구",
                  "brandName": "글로우랩",
                  "itemCount": 2,
                  "startAt": "2026-07-15T10:00:00",
                  "endAt": "2026-07-21T23:55:00",
                  "postStatus": "CLOSED",
                  "postStatusLabel": "종료",
                  "postStatusTone": "NEUTRAL",
                  "status": "ENDED",
                  "statusLabel": "종료",
                  "statusTone": "INFO",
                  "actionRequired": true
                }
              ],
              "pageInfo": {
                "currentPage": 1,
                "totalPages": 1,
                "totalResults": 4,
                "limit": 20,
                "hasNext": false
              }
            }
            """;

    static final String LIST_EMPTY = """
            {
              "content": [],
              "pageInfo": {
                "currentPage": 1,
                "totalPages": 0,
                "totalResults": 0,
                "limit": 20,
                "hasNext": false
              }
            }
            """;

    static final String SUMMARY = """
            {
              "tabCounts": {
                "ALL": 11,
                "PREPARING": 3,
                "READY": 1,
                "IN_PROGRESS": 4,
                "ENDED": 3
              },
              "actionRequiredCount": 3
            }
            """;

    static final String SUMMARY_EMPTY = """
            {
              "tabCounts": {
                "ALL": 0,
                "PREPARING": 0,
                "READY": 0,
                "IN_PROGRESS": 0,
                "ENDED": 0
              },
              "actionRequiredCount": 0
            }
            """;

    // ── 상세 조각 ─────────────────────────────────────────────────────────────
    // 상태와 무관한 블록(brand · contract · items · fixedFee)과 이력 꼬리, navigation을 조각으로 두고 이어 붙인다.

    private static final String REFS = """
              "brand": {"marketId": 7, "name": "글로우랩", "pairThreadId": 305},
              "contract": {"contractId": 12, "contractNumber": "CTR-20260728-012", "concludedAt": "2026-08-03T15:30:00", "contentDueDate": "2026-08-18", "concludedSignerName": "민지의 쇼룸"},
              "items": [
                {"productId": 101, "productName": "글로우 수분 세럼 50ml", "groupBuyPrice": 28000, "myRewardRate": 15.0, "unitReward": 4200,
                  "options": [{"variantId": 301, "variantName": "단품", "salePrice": 28000},
                              {"variantId": 302, "variantName": "2개 세트", "salePrice": 66000}]},
                {"productId": 102, "productName": "글로우 수분 크림 60ml", "groupBuyPrice": 32000, "myRewardRate": 12.5, "unitReward": 4000,
                  "options": [{"variantId": 311, "variantName": null, "salePrice": 32000}]}
              ],
              "fixedFee": {"amount": 300000, "trigger": "POST_REGISTERED", "triggerLabel": "공구 게시물 등록 후", "displayText": "고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급"},
            """;

    private static final String PAYOUT_OPEN = """
              "payout": {"fixedFeeAmount": 300000, "salesReward": null, "platformGuaranteed": false, "disputeChannel": {"threadId": 305}},
            """;

    private static final String SUMMARY_LIVE = """
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "IN_PROGRESS", "statusLabel": "진행중", "statusTone": "SUCCESS", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
            """;

    private static final String POST_EXPOSED = """
              "post": {"status": "EXPOSED", "statusLabel": "노출중", "statusTone": "SUCCESS", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": "2026-08-06T14:02:00", "expectedReviewDate": null, "reviewedAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "closedAt": null, "lastEditedAt": null, "rejection": null, "hidden": null},
            """;

    private static final String POST_CLOSED = """
              "post": {"status": "CLOSED", "statusLabel": "종료", "statusTone": "NEUTRAL", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": "2026-08-06T14:02:00", "expectedReviewDate": null, "reviewedAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "closedAt": "2026-08-20T23:55:00", "lastEditedAt": null, "rejection": null, "hidden": null},
            """;

    /** 준비중의 이력 꼬리 — 브랜드 물량 확인 · 공구 생성. 배열을 닫는다. */
    private static final String HISTORY_PREPARING_TAIL = """
                {"eventType": "STOCK_CONFIRMED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": null, "occurredAt": "2026-08-04T11:20:00"},
                {"eventType": "CREATED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-03T15:30:00"}
              ],
            """;

    /** 진행중 이후의 이력 꼬리 — 오픈 · 오픈 승인 · 게시물 제출 · 물량 확인 · 생성. 배열을 닫는다. */
    private static final String HISTORY_LIVE_TAIL = """
                {"eventType": "OPENED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-14T10:00:32"},
                {"eventType": "OPEN_APPROVED", "actorType": "ADMIN", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-07T10:15:00"},
                {"eventType": "POST_SUBMITTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-06T14:02:00"},
            """ + HISTORY_PREPARING_TAIL;

    /** 목록 조건(tab · keyword · sort)을 넘긴 상세 조회 */
    private static final String NAV_LIST = """
              "navigation": {"prevGroupBuyId": 52, "nextGroupBuyId": 38}
            }
            """;

    /** 목록 조건 없이 조회했거나 실행 API의 응답 — 이웃을 계산하지 않는다 */
    private static final String NAV_NONE = """
              "navigation": {"prevGroupBuyId": null, "nextGroupBuyId": null}
            }
            """;

    // ── 준비중 ───────────────────────────────────────────────────────────────

    /** B1 게시물 작성 필요(작성중) — 기준 시각 08.05 */
    private static final String PREPARING_WRITING_BODY = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "PREPARING", "statusLabel": "준비중", "statusTone": "NEUTRAL", "createdAt": "2026-08-03T15:30:00", "readyAt": null, "openedAt": null, "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 0, "daysUntilStart": 9, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": {
                "gates": [
                  {"key": "STOCK_CONFIRMED", "actorType": "SELLER", "state": "DONE", "tone": "NEUTRAL", "doneAt": "2026-08-04T11:20:00"},
                  {"key": "POST_SUBMITTED", "actorType": "CREATOR", "state": "MY_TURN", "tone": "WARNING", "doneAt": null},
                  {"key": "OPEN_APPROVED", "actorType": "ADMIN", "state": "WAITING", "tone": "NEUTRAL", "doneAt": null}
                ],
                "registrationDeadline": "2026-08-10",
                "registrationOverdue": false,
                "reviewSlaBusinessDays": 3
              },
              "post": {"status": "WRITING", "statusLabel": "작성중", "statusTone": "NEUTRAL", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이…", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": null, "expectedReviewDate": null, "reviewedAt": null, "openedAt": null, "closedAt": null, "lastEditedAt": null, "rejection": null, "hidden": null},
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": true, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
            """ + HISTORY_PREPARING_TAIL;

    static final String DETAIL_PREPARING_WRITING = PREPARING_WRITING_BODY + NAV_LIST;

    static final String SAVE_DRAFT_RESULT = PREPARING_WRITING_BODY + NAV_NONE;

    /** B2 승인대기 — 기준 시각 08.06 14:02 */
    private static final String PREPARING_PENDING_BODY = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "PREPARING", "statusLabel": "준비중", "statusTone": "NEUTRAL", "createdAt": "2026-08-03T15:30:00", "readyAt": null, "openedAt": null, "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 0, "daysUntilStart": 8, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": {
                "gates": [
                  {"key": "STOCK_CONFIRMED", "actorType": "SELLER", "state": "DONE", "tone": "NEUTRAL", "doneAt": "2026-08-04T11:20:00"},
                  {"key": "POST_SUBMITTED", "actorType": "CREATOR", "state": "DONE", "tone": "NEUTRAL", "doneAt": "2026-08-06T14:02:00"},
                  {"key": "OPEN_APPROVED", "actorType": "ADMIN", "state": "IN_REVIEW", "tone": "NEUTRAL", "doneAt": null}
                ],
                "registrationDeadline": "2026-08-10",
                "registrationOverdue": false,
                "reviewSlaBusinessDays": 3
              },
              "post": {"status": "PENDING_APPROVAL", "statusLabel": "승인대기", "statusTone": "INFO", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": "2026-08-06T14:02:00", "expectedReviewDate": "2026-08-11", "reviewedAt": null, "openedAt": null, "closedAt": null, "lastEditedAt": null, "rejection": null, "hidden": null},
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "POST_SUBMITTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-06T14:02:00"},
            """ + HISTORY_PREPARING_TAIL;

    static final String DETAIL_PREPARING_PENDING = PREPARING_PENDING_BODY + NAV_LIST;

    static final String SUBMIT_POST_RESULT = PREPARING_PENDING_BODY + NAV_NONE;

    /** B3 반려 — 기준 시각 08.08. 게이트 ②가 다시 내 차례가 된다 */
    static final String DETAIL_PREPARING_REJECTED = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "PREPARING", "statusLabel": "준비중", "statusTone": "NEUTRAL", "createdAt": "2026-08-03T15:30:00", "readyAt": null, "openedAt": null, "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 0, "daysUntilStart": 6, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": {
                "gates": [
                  {"key": "STOCK_CONFIRMED", "actorType": "SELLER", "state": "DONE", "tone": "NEUTRAL", "doneAt": "2026-08-04T11:20:00"},
                  {"key": "POST_SUBMITTED", "actorType": "CREATOR", "state": "MY_TURN", "tone": "WARNING", "doneAt": null},
                  {"key": "OPEN_APPROVED", "actorType": "ADMIN", "state": "WAITING", "tone": "NEUTRAL", "doneAt": null}
                ],
                "registrationDeadline": "2026-08-10",
                "registrationOverdue": false,
                "reviewSlaBusinessDays": 3
              },
              "post": {"status": "REJECTED", "statusLabel": "반려", "statusTone": "WARNING", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "일주일 쓰면 잔주름이 사라져요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": "2026-08-06T14:02:00", "expectedReviewDate": null, "reviewedAt": "2026-08-07T16:30:00", "openedAt": null, "closedAt": null, "lastEditedAt": null, "rejection": {"code": "AD_EFFECT_ASSERTION", "detail": "「잔주름이 사라져요」는 효과를 단정하는 표현입니다. 사용감 위주로 고쳐 주세요.", "rejectedAt": "2026-08-07T16:30:00"}, "hidden": null},
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": true, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "OPEN_REJECTED", "actorType": "ADMIN", "actorDisplayName": null, "detail": "표시광고법 위반 문구 — 효과 단정", "occurredAt": "2026-08-07T16:30:00"},
                {"eventType": "POST_SUBMITTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-06T14:02:00"},
            """ + HISTORY_PREPARING_TAIL + NAV_LIST;

    // ── 진행중 ───────────────────────────────────────────────────────────────

    /** B6 연장 응답 대기 — 기준 시각 08.18 09:00 */
    static final String DETAIL_EXTENSION_PENDING = """
            {
            """ + SUMMARY_LIVE + """
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 5, "daysUntilStart": null, "daysUntilEnd": 2, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": null,
            """ + POST_EXPOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": {"status": "PENDING", "days": 7, "reason": "재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", "beforeEndAt": "2026-08-20T23:55:00", "afterEndAt": "2026-08-27T23:55:00", "beforeTotalDays": 7, "afterTotalDays": 14, "requestedAt": "2026-08-17T16:40:00", "respondDeadlineAt": "2026-08-20T23:55:00", "respondedAt": null, "responseActorType": null, "rejectReasonCode": null, "rejectReasonLabel": null, "rejectMemo": null},
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": true, "canRespondExtension": true, "canRequestSuspension": true, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "EXTENSION_REQUESTED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": "7일 · 재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", "occurredAt": "2026-08-17T16:40:00"},
            """ + HISTORY_LIVE_TAIL + NAV_LIST;

    /** C1 연장 수락 결과 — 종료일이 08.27로 바뀐다 */
    static final String ACCEPT_EXTENSION_RESULT = """
            {
            """ + SUMMARY_LIVE + """
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-27T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 14, "elapsedDays": 5, "daysUntilStart": null, "daysUntilEnd": 9, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": null,
            """ + POST_EXPOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": {"status": "ACCEPTED", "days": 7, "reason": "재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", "beforeEndAt": "2026-08-20T23:55:00", "afterEndAt": "2026-08-27T23:55:00", "beforeTotalDays": 7, "afterTotalDays": 14, "requestedAt": "2026-08-17T16:40:00", "respondDeadlineAt": null, "respondedAt": "2026-08-18T09:12:00", "responseActorType": "CREATOR", "rejectReasonCode": null, "rejectReasonLabel": null, "rejectMemo": null},
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": true, "canRespondExtension": false, "canRequestSuspension": true, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "EXTENSION_ACCEPTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "종료일 08.27 23:55로 변경", "occurredAt": "2026-08-18T09:12:00"},
                {"eventType": "EXTENSION_REQUESTED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": "7일 · 재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", "occurredAt": "2026-08-17T16:40:00"},
            """ + HISTORY_LIVE_TAIL + NAV_NONE;

    /** C2 연장 거절 결과 — 종료일은 그대로다 */
    static final String REJECT_EXTENSION_RESULT = """
            {
            """ + SUMMARY_LIVE + """
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 5, "daysUntilStart": null, "daysUntilEnd": 2, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": null,
            """ + POST_EXPOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": {"status": "REJECTED", "days": 7, "reason": "재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", "beforeEndAt": "2026-08-20T23:55:00", "afterEndAt": "2026-08-27T23:55:00", "beforeTotalDays": 7, "afterTotalDays": 14, "requestedAt": "2026-08-17T16:40:00", "respondDeadlineAt": null, "respondedAt": "2026-08-18T09:12:00", "responseActorType": "CREATOR", "rejectReasonCode": "NEXT_SCHEDULE_BOOKED", "rejectReasonLabel": "다음 일정이 잡혀 있음", "rejectMemo": "8월 넷째 주에 다른 공구가 잡혀 있어요."},
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": true, "canRespondExtension": false, "canRequestSuspension": true, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "EXTENSION_REJECTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "다음 일정이 잡혀 있음", "occurredAt": "2026-08-18T09:12:00"},
                {"eventType": "EXTENSION_REQUESTED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": "7일 · 재고가 추가 입고되어 판매 기간을 늘리고 싶습니다", "occurredAt": "2026-08-17T16:40:00"},
            """ + HISTORY_LIVE_TAIL + NAV_NONE;

    /** B5a 숨김 중 게시물 수정 결과 — 숨김은 풀리지 않는다. 기준 시각 08.17 11:05 */
    private static final String POST_HIDDEN_EDITED_BODY = """
            {
            """ + SUMMARY_LIVE + """
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 4, "daysUntilStart": null, "daysUntilEnd": 3, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": null,
              "post": {"status": "HIDDEN", "statusLabel": "숨김", "statusTone": "WARNING", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 제 피부 기준으로 오후까지 촉촉함이 유지됐어요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": "2026-08-06T14:02:00", "expectedReviewDate": null, "reviewedAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "closedAt": null, "lastEditedAt": "2026-08-17T11:05:00", "rejection": null, "hidden": {"code": "AD_MEDICAL_CLAIM", "detail": "「피부염 개선」은 의료적 효능 표현입니다. 해당 문구를 삭제해 주세요.", "hiddenAt": "2026-08-16T15:20:00", "hiddenDays": 2}},
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": true, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "POST_HIDDEN", "actorType": "ADMIN", "actorDisplayName": null, "detail": "표시광고법 위반 문구 — 의료적 효능 표현", "occurredAt": "2026-08-16T15:20:00"},
            """ + HISTORY_LIVE_TAIL;

    static final String DETAIL_POST_HIDDEN = POST_HIDDEN_EDITED_BODY + NAV_LIST;

    static final String EDIT_POST_RESULT = POST_HIDDEN_EDITED_BODY + NAV_NONE;

    /** C7 중단 요청 결과 — 공구 상태는 진행중 그대로. 기준 시각 08.17 20:10 */
    static final String REQUEST_SUSPENSION_RESULT = """
            {
            """ + SUMMARY_LIVE + """
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 4, "daysUntilStart": null, "daysUntilEnd": 3, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": null,
            """ + POST_EXPOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": {"type": "SUSPEND", "requesterType": "CREATOR", "mine": true, "reasonCode": "DELIVERY_FAILURE", "reasonLabel": "배송 지연 · 미발송이 계속됨", "memo": "시작 4일째인데 첫날 주문 18건이 아직 발송되지 않았고 배송 문의가 계속 들어옵니다.", "requestedAt": "2026-08-17T20:10:00"},
              "adminSuspension": null,
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": true, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "SUSPENSION_REQUESTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "배송 지연 · 미발송이 계속됨", "occurredAt": "2026-08-17T20:10:00"},
            """ + HISTORY_LIVE_TAIL + NAV_NONE;

    // ── 중단 예정 ─────────────────────────────────────────────────────────────

    /** B13 직권 중단 예고 — 판매는 계속된다. 기준 시각 08.18 10:00 */
    static final String DETAIL_SUSPENSION_SCHEDULED = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "SUSPENSION_SCHEDULED", "statusLabel": "중단 예정", "statusTone": "WARNING", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 5, "daysUntilStart": null, "daysUntilEnd": 2, "startOverdue": false},
            """ + REFS + PAYOUT_OPEN + """
              "readiness": null,
            """ + POST_EXPOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": {"kind": "NOTICE", "reasonClause": "ART17_2_IP_DEFECT", "noticeBody": "용기 파손 신고가 같은 로트에서 반복 접수되었습니다. 브랜드 소명을 받은 뒤 집행 여부를 판단합니다.", "noticedAt": "2026-08-17T18:00:00", "executeScheduledAt": "2026-08-20T18:00:00", "businessDaysUntilExecution": 2},
              "closure": null,
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "SUSPENSION_NOTICED", "actorType": "ADMIN", "actorDisplayName": null, "detail": "제17조① 2호 지식재산권 침해·중대 하자 · 집행 예정 08.20 18:00 · 소명 기한 08.19", "occurredAt": "2026-08-17T18:00:00"},
            """ + HISTORY_LIVE_TAIL + NAV_LIST;

    // ── 종결 ─────────────────────────────────────────────────────────────────

    private static final String SUMMARY_ENDED = """
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "ENDED", "statusLabel": "종료", "statusTone": "INFO", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": "2026-08-20T23:55:00", "closeType": "COMPLETED", "closeTypeLabel": "기간 종료", "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 7, "daysUntilStart": null, "daysUntilEnd": null, "startOverdue": false},
            """;

    private static final String CLOSURE_COMPLETED = """
              "closure": {"closeType": "COMPLETED", "endedAt": "2026-08-20T23:55:00", "source": null, "requester": null, "decisionReason": null, "decidedAt": null, "adminBasis": null},
            """;

    private static final String TARGETS = """
                  "myTarget": {"party": "BRAND", "duties": ["ORDER_DELIVERY", "FIXED_FEE_PAYMENT"], "counts": null},
                  "theirTarget": {"party": "CREATOR", "duties": ["SHOWROOM_POST", "FEED", "STORY"], "counts": {"feed": 1, "reels": 0, "story": 3}},
                  "dueAt": "2026-08-23T23:55:00",
                  "autoConfirmOnTimeout": false,
                  "onHold": false,
                  "threadId": null,
                  "resolvedAt": null
                }
              },
            """;

    private static final String HISTORY_ENDED_TAIL = """
                {"eventType": "FULFILLMENT_CONFIRMED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": null, "occurredAt": "2026-08-21T14:00:00"},
                {"eventType": "ENDED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-20T23:55:00"},
            """ + HISTORY_LIVE_TAIL;

    /** B7 종료 · 이행 확인 대기 — 브랜드는 이미 확인했다. 기준 시각 08.22 10:00 */
    static final String DETAIL_ENDED_CHECK_PENDING = """
            {
            """ + SUMMARY_ENDED + REFS + """
              "payout": null,
              "readiness": null,
            """ + POST_CLOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
            """ + CLOSURE_COMPLETED + """
              "settlement": null,
              "afterEnd": {
                "fulfillment": {
                  "mine": null,
                  "theirs": {"result": "FULFILLED", "reason": null, "checkedAt": "2026-08-21T14:00:00", "auto": false},
            """ + TARGETS + """
              "permissions": {"canWritePost": false, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": true, "canOpenPairThread": true},
              "history": [
            """ + HISTORY_ENDED_TAIL + NAV_LIST;

    /** C5 이행 확인 결과 */
    static final String CHECK_FULFILLMENT_RESULT = """
            {
            """ + SUMMARY_ENDED + REFS + """
              "payout": null,
              "readiness": null,
            """ + POST_CLOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
            """ + CLOSURE_COMPLETED + """
              "settlement": null,
              "afterEnd": {
                "fulfillment": {
                  "mine": {"result": "FULFILLED", "reason": null, "checkedAt": "2026-08-22T10:30:00", "auto": false},
                  "theirs": {"result": "FULFILLED", "reason": null, "checkedAt": "2026-08-21T14:00:00", "auto": false},
            """ + TARGETS + """
              "permissions": {"canWritePost": false, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "FULFILLMENT_CONFIRMED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-22T10:30:00"},
            """ + HISTORY_ENDED_TAIL + NAV_NONE;

    /** 정산완료 — 확정 리워드는 정산 모듈 연동 전이라 null. 기준 시각 09.10 */
    static final String DETAIL_SETTLED = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "SETTLED", "statusLabel": "정산완료", "statusTone": "SUCCESS", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": "2026-08-20T23:55:00", "closeType": "COMPLETED", "closeTypeLabel": "기간 종료", "settledAt": "2026-09-05T15:00:00"},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 7, "daysUntilStart": null, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS + """
              "payout": null,
              "readiness": null,
            """ + POST_CLOSED + """
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
            """ + CLOSURE_COMPLETED + """
              "settlement": {"settledAt": "2026-09-05T15:00:00", "fixedFeeAmount": 300000, "confirmedReward": null, "totalBeforeDeduction": null},
              "afterEnd": {
                "fulfillment": {
                  "mine": {"result": "FULFILLED", "reason": null, "checkedAt": "2026-08-22T10:30:00", "auto": false},
                  "theirs": {"result": "FULFILLED", "reason": null, "checkedAt": "2026-08-21T14:00:00", "auto": false},
            """ + TARGETS + """
              "permissions": {"canWritePost": false, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "SETTLED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-09-05T15:00:00"},
                {"eventType": "SALES_FINALIZED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": "구매확정 308건", "occurredAt": "2026-08-28T00:10:00"},
                {"eventType": "FULFILLMENT_CONFIRMED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-22T10:30:00"},
            """ + HISTORY_ENDED_TAIL + NAV_LIST;

    /** 내 중단 요청이 승인되어 중단 — afterEnd·payout이 없다. 기준 시각 08.19 */
    static final String DETAIL_SUSPENDED_BY_MY_REQUEST = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "SUSPENDED", "statusLabel": "중단", "statusTone": "DANGER", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": "2026-08-18T14:00:00", "closeType": "SUSPENDED", "closeTypeLabel": "중단", "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 5, "daysUntilStart": null, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS + """
              "payout": null,
              "readiness": null,
              "post": {"status": "CLOSED", "statusLabel": "종료", "statusTone": "NEUTRAL", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "sellerInfoAutoAttached": true, "submittedAt": "2026-08-06T14:02:00", "expectedReviewDate": null, "reviewedAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "closedAt": "2026-08-18T14:00:00", "lastEditedAt": null, "rejection": null, "hidden": null},
              "sales": null,
              "orderClosure": null,
              "extension": null,
              "activeRequest": null,
              "adminSuspension": null,
              "closure": {"closeType": "SUSPENDED", "endedAt": "2026-08-18T14:00:00", "source": "REQUEST", "requester": {"type": "CREATOR", "mine": true, "name": "민지의 쇼룸", "reasonCode": "DELIVERY_FAILURE", "reasonLabel": "배송 지연 · 미발송이 계속됨", "requestedAt": "2026-08-17T20:10:00"}, "decisionReason": "미발송 18건을 확인해 소비자 보호를 위해 중단을 승인합니다. 발송 대기 주문은 브랜드가 모두 처리해야 합니다.", "decidedAt": "2026-08-18T14:00:00", "adminBasis": null},
              "settlement": null,
              "afterEnd": null,
              "permissions": {"canWritePost": false, "canEditPost": false, "canRespondExtension": false, "canRequestSuspension": false, "canCheckFulfillment": false, "canOpenPairThread": true},
              "history": [
                {"eventType": "SUSPENDED", "actorType": "ADMIN", "actorDisplayName": null, "detail": "배송 지연 · 미발송이 계속됨", "occurredAt": "2026-08-18T14:00:00"},
                {"eventType": "SUSPENSION_REQUESTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "배송 지연 · 미발송이 계속됨", "occurredAt": "2026-08-17T20:10:00"},
            """ + HISTORY_LIVE_TAIL + NAV_LIST;

    // ── 에러 ─────────────────────────────────────────────────────────────────

    static final String ERR_NOT_FOUND = """
            {"code": "GROUP_BUY_NOT_FOUND", "message": "존재하지 않는 공구입니다."}
            """;

    static final String ERR_NOT_OWNED = """
            {"code": "GROUP_BUY_NOT_OWNED_BY_CREATOR", "message": "존재하지 않는 공구입니다."}
            """;

    static final String ERR_CREATOR_NOT_FOUND = """
            {"code": "CREATOR_NOT_FOUND", "message": "존재하지 않는 크리에이터입니다."}
            """;

    static final String ERR_INVALID_INPUT = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_ACTION_NOT_ALLOWED = """
            {"code": "GROUP_BUY_ACTION_NOT_ALLOWED", "message": "지금 상태에서는 요청할 수 없습니다."}
            """;

    static final String ERR_STATUS_CONFLICT = """
            {"code": "GROUP_BUY_STATUS_CONFLICT", "message": "공구 상태가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."}
            """;

    static final String ERR_POST_UNDER_REVIEW = """
            {"code": "GROUP_BUY_POST_UNDER_REVIEW", "message": "검토 중에는 게시물을 수정할 수 없습니다."}
            """;

    static final String ERR_POST_NOT_WRITABLE = """
            {"code": "GROUP_BUY_POST_NOT_WRITABLE", "message": "지금은 게시물을 작성할 수 없습니다."}
            """;

    static final String ERR_POST_NOT_EDITABLE = """
            {"code": "GROUP_BUY_POST_NOT_EDITABLE", "message": "지금 상태에서는 게시물을 수정할 수 없습니다."}
            """;

    static final String ERR_POST_EMPTY_DRAFT = """
            {"code": "GROUP_BUY_POST_EMPTY_DRAFT", "message": "제목이나 본문 중 하나는 입력해 주세요."}
            """;

    static final String ERR_POST_REQUIRED_FIELD = """
            {"code": "GROUP_BUY_POST_REQUIRED_FIELD", "message": "제목과 본문을 모두 입력해 주세요."}
            """;

    static final String ERR_POST_TOO_LONG = """
            {"code": "GROUP_BUY_POST_TOO_LONG", "message": "제목은 40자, 본문은 2,000자까지 입력할 수 있습니다."}
            """;

    static final String ERR_EXTENSION_NOT_PENDING = """
            {"code": "GROUP_BUY_EXTENSION_NOT_PENDING", "message": "응답할 연장 요청이 없습니다."}
            """;

    static final String ERR_EXTENSION_RESPONSE_CLOSED = """
            {"code": "GROUP_BUY_EXTENSION_RESPONSE_CLOSED", "message": "공구 종료 시각이 지나 연장 요청에 응답할 수 없습니다."}
            """;

    static final String ERR_REASON_MEMO_REQUIRED = """
            {"code": "GROUP_BUY_REASON_MEMO_REQUIRED", "message": "기타 사유를 선택하면 메모를 입력해야 합니다."}
            """;

    static final String ERR_REQUEST_ALREADY_PENDING = """
            {"code": "GROUP_BUY_REQUEST_ALREADY_PENDING", "message": "검토 중인 요청이 있어 추가로 요청할 수 없습니다."}
            """;

    static final String ERR_FULFILLMENT_ALREADY_CHECKED = """
            {"code": "GROUP_BUY_FULFILLMENT_ALREADY_CHECKED", "message": "이미 이행 확인을 제출했습니다."}
            """;

    static final String ERR_FULFILLMENT_REASON_REQUIRED = """
            {"code": "GROUP_BUY_FULFILLMENT_REASON_REQUIRED", "message": "미이행 사유를 입력해 주세요."}
            """;

    static final String ERR_THREAD_UNAVAILABLE = """
            {"code": "GROUP_BUY_THREAD_UNAVAILABLE", "message": "지금은 스레드를 열 수 없습니다. 운영자에게 문의해 주세요."}
            """;
}
