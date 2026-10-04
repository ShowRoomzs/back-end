package showroomz.api.admin.groupbuy.docs;

/**
 * 어드민 공구 Swagger 예제. 상세 응답({@code AdminGroupBuyDetailResponse})이 화면 13종의 배경이라 상태별 예제를 한곳에 모은다.
 * 어노테이션 값이라 컴파일 타임 상수(텍스트 블록 연결)로만 만든다.
 *
 * <p>시나리오 둘을 상태별로 이어 그렸다 — 예제끼리는 서로 독립된 스냅샷이다.
 * <ul>
 *   <li>공구 41 「여름 수분 세럼 공구」 · 브랜드 「글로우랩」 · 쇼룸 「민지의 쇼룸」 — 오픈 승인 · 숨김 · 요청 판정 · 종료</li>
 *   <li>공구 45 「비타민 앰플 공구」 · 브랜드 「퓨어셀」 — 직권 중단 통지 · 소명 · 집행(고정 지급비 없는 계약)</li>
 * </ul>
 * 정산 모듈 연동 전이라 정산 단계는 {@code DERIVED}이고 확정 리워드는 null이다. 판매 수치는 예제마다 필요한 칸만 채웠다.
 */
final class AdminGroupBuyDocsExamples {

    private AdminGroupBuyDocsExamples() {
    }

    // ── 목록 · 요약 (기준 시각 2026-09-09 18:00) ─────────────────────────────

    static final String LIST = """
            {
              "content": [
                {
                  "groupBuyId": 53,
                  "groupBuyNumber": "GB-20260902-053",
                  "title": "가을 립 틴트 공구",
                  "creatorName": "하루 쇼룸",
                  "brandName": "모노코스",
                  "itemCount": 3,
                  "startAt": "2026-09-15T10:00:00",
                  "endAt": "2026-09-21T23:55:00",
                  "postStatus": "PENDING_APPROVAL",
                  "postStatusLabel": "승인대기",
                  "postStatusTone": "INFO",
                  "status": "PREPARING",
                  "statusLabel": "준비중",
                  "statusTone": "NEUTRAL",
                  "actionRequired": true
                },
                {
                  "groupBuyId": 50,
                  "groupBuyNumber": "GB-20260827-050",
                  "title": "톤업 선크림 공구",
                  "creatorName": "소연 쇼룸",
                  "brandName": "선데이랩",
                  "itemCount": 1,
                  "startAt": "2026-09-03T10:00:00",
                  "endAt": "2026-09-16T23:55:00",
                  "postStatus": "EXPOSED",
                  "postStatusLabel": "노출중",
                  "postStatusTone": "SUCCESS",
                  "status": "IN_PROGRESS",
                  "statusLabel": "진행중",
                  "statusTone": "SUCCESS",
                  "actionRequired": true
                },
                {
                  "groupBuyId": 45,
                  "groupBuyNumber": "GB-20260820-045",
                  "title": "비타민 앰플 공구",
                  "creatorName": "민지의 쇼룸",
                  "brandName": "퓨어셀",
                  "itemCount": 1,
                  "startAt": "2026-09-01T10:00:00",
                  "endAt": "2026-09-21T23:55:00",
                  "postStatus": "EXPOSED",
                  "postStatusLabel": "노출중",
                  "postStatusTone": "SUCCESS",
                  "status": "SUSPENSION_SCHEDULED",
                  "statusLabel": "중단 예정",
                  "statusTone": "WARNING",
                  "actionRequired": true
                },
                {
                  "groupBuyId": 48,
                  "groupBuyNumber": "GB-20260825-048",
                  "title": "쿨링 토너패드 공구",
                  "creatorName": "민지의 쇼룸",
                  "brandName": "글로우랩",
                  "itemCount": 2,
                  "startAt": "2026-09-12T10:00:00",
                  "endAt": "2026-09-18T23:55:00",
                  "postStatus": "SCHEDULED",
                  "postStatusLabel": "예약",
                  "postStatusTone": "INFO",
                  "status": "READY",
                  "statusLabel": "준비완료",
                  "statusTone": "INFO",
                  "actionRequired": false
                },
                {
                  "groupBuyId": 41,
                  "groupBuyNumber": "GB-20260803-041",
                  "title": "여름 수분 세럼 공구",
                  "creatorName": "민지의 쇼룸",
                  "brandName": "글로우랩",
                  "itemCount": 2,
                  "startAt": "2026-08-14T10:00:00",
                  "endAt": "2026-08-20T23:55:00",
                  "postStatus": "CLOSED",
                  "postStatusLabel": "종료",
                  "postStatusTone": "NEUTRAL",
                  "status": "ENDED",
                  "statusLabel": "종료",
                  "statusTone": "INFO",
                  "actionRequired": false
                }
              ],
              "pageInfo": {"currentPage": 1, "totalPages": 1, "totalResults": 5, "limit": 20, "hasNext": false}
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
              "queues": {"OPEN_REVIEW": 1, "SUSPEND_REQUEST": 0, "EARLY_CLOSE_REQUEST": 1, "APPEAL_REVIEW": 1},
              "actionRequiredCount": 3,
              "tabCounts": {"ALL": 5, "ACTION_REQUIRED": 3, "PREPARING": 1, "READY": 1, "IN_PROGRESS": 2, "ENDED": 1, "SETTLED": 0, "SUSPENDED": 0},
              "nearestDeadlines": [
                {"queue": "OPEN_REVIEW", "groupBuyId": 53, "title": "가을 립 틴트 공구", "dueAt": "2026-09-11T23:59:59", "daysLeft": 2},
                {"queue": "APPEAL_REVIEW", "groupBuyId": 45, "title": "비타민 앰플 공구", "dueAt": "2026-09-11T10:00:00", "daysLeft": 2}
              ],
              "settlementWatch": {
                "watchingCount": 1,
                "overdueCount": 0,
                "nearest": {"groupBuyId": 41, "title": "여름 수분 세럼 공구", "dueAt": "2026-09-19", "reached": false}
              }
            }
            """;

    static final String SUMMARY_EMPTY = """
            {
              "queues": {"OPEN_REVIEW": 0, "SUSPEND_REQUEST": 0, "EARLY_CLOSE_REQUEST": 0, "APPEAL_REVIEW": 0},
              "actionRequiredCount": 0,
              "tabCounts": {"ALL": 0, "ACTION_REQUIRED": 0, "PREPARING": 0, "READY": 0, "IN_PROGRESS": 0, "ENDED": 0, "SETTLED": 0, "SUSPENDED": 0},
              "nearestDeadlines": [],
              "settlementWatch": {"watchingCount": 0, "overdueCount": 0, "nearest": null}
            }
            """;

    // ── 상세 조각 · 공구 41 ──────────────────────────────────────────────────
    // 상태와 무관한 블록(brand · creator · contract · items · fixedFee)과 이력 꼬리, navigation을 조각으로 두고 이어 붙인다.

    private static final String REFS_41 = """
              "brand": {"marketId": 7, "name": "글로우랩", "pairThreadId": 305},
              "creator": {"creatorId": 9, "name": "민지의 쇼룸", "accountId": "minji"},
              "contract": {"contractId": 12, "contractNumber": "CTR-20260728-012", "concludedAt": "2026-08-03T15:30:00"},
              "items": [
                {"productId": 101, "productName": "글로우 수분 세럼 50ml", "regularPrice": 38000, "groupBuyPrice": 28000, "rewardRate": 15.0, "expectedUnitReward": 4200, "minQuantity": 200,
                  "options": [{"variantId": 301, "variantName": "단품", "salePrice": 28000, "minQuantity": 120},
                              {"variantId": 302, "variantName": "2개 세트", "salePrice": 66000, "minQuantity": 80}]},
                {"productId": 102, "productName": "글로우 수분 크림 60ml", "regularPrice": 42000, "groupBuyPrice": 32000, "rewardRate": 12.5, "expectedUnitReward": 4000, "minQuantity": 150,
                  "options": [{"variantId": 311, "variantName": null, "salePrice": 32000, "minQuantity": 150}]}
              ],
              "fixedFee": {"amount": 300000, "trigger": "POST_REGISTERED", "triggerLabel": "공구 게시물 등록 후", "displayText": "고정 지급비 300,000원 · 지급 시점: 공구 게시물 등록 후 · 브랜드 직접 지급"},
            """;

    private static final String SUMMARY_41_LIVE = """
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "IN_PROGRESS", "statusLabel": "진행중", "statusTone": "SUCCESS", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 5, "daysUntilStart": null, "daysUntilEnd": 2, "startOverdue": false},
            """;

    private static final String POST_41_EXPOSED = """
              "post": {"status": "EXPOSED", "statusLabel": "노출중", "statusTone": "SUCCESS", "postNumber": "POST-GB-20260806-104", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "submittedAt": "2026-08-06T14:02:00", "reviewedAt": "2026-08-07T10:15:00", "reviewedByName": "김운영", "lastEditedAt": null, "editCount": 0, "latestRevisionNo": 1, "rejection": null, "hidden": null, "closedBy": null},
            """;

    /** 진행중 상세의 머리 — groupBuy부터 sales까지. */
    private static final String HEAD_41_LIVE = """
            {
            """ + SUMMARY_41_LIVE + REFS_41 + """
              "readiness": null,
              "openReview": null,
            """ + POST_41_EXPOSED + """
              "sales": null,
            """;

    /** 준비중 이력 꼬리 — 브랜드 물량 확인 · 공구 생성. 배열을 닫는다. */
    private static final String HISTORY_41_PREPARING_TAIL = """
                {"eventType": "STOCK_CONFIRMED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": null, "occurredAt": "2026-08-04T11:20:00", "synthetic": false, "revisionNo": null},
                {"eventType": "CREATED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-03T15:30:00", "synthetic": false, "revisionNo": null}
              ],
            """;

    /** 진행중 이후 이력 꼬리 — 오픈 · 오픈 승인(운영자 실명) · 게시물 제출 · 물량 확인 · 생성. 배열을 닫는다. */
    private static final String HISTORY_41_LIVE_TAIL = """
                {"eventType": "OPENED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-14T10:00:32", "synthetic": false, "revisionNo": null},
                {"eventType": "OPEN_APPROVED", "actorType": "ADMIN", "actorDisplayName": "김운영", "detail": null, "occurredAt": "2026-08-07T10:15:00", "synthetic": false, "revisionNo": null},
                {"eventType": "POST_SUBMITTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-06T14:02:00", "synthetic": false, "revisionNo": null},
            """ + HISTORY_41_PREPARING_TAIL;

    /** 목록 조건(tab · keyword · sort)을 넘긴 상세 조회 — 목록 기준 이전·다음 공구 */
    private static final String NAV_LIST = """
              "navigation": {"prevGroupBuyId": 53, "nextGroupBuyId": 38}
            }
            """;

    /** 목록 조건 없이 조회 — 이웃을 계산하지 않는다 */
    private static final String NAV_NONE = """
              "navigation": {"prevGroupBuyId": null, "nextGroupBuyId": null}
            }
            """;

    // ── B1 오픈 승인 대기 (기준 시각 08-10 09:00) ──────────────────────────────

    static final String DETAIL_OPEN_REVIEW = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "PREPARING", "statusLabel": "준비중", "statusTone": "NEUTRAL", "createdAt": "2026-08-03T15:30:00", "readyAt": null, "openedAt": null, "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 0, "daysUntilStart": 4, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS_41 + """
              "readiness": {
                "gates": [
                  {"key": "STOCK_CONFIRMED", "actorType": "SELLER", "done": true, "doneAt": "2026-08-04T11:20:00", "doneByName": "글로우랩", "state": "DONE", "tone": "SUCCESS"},
                  {"key": "POST_SUBMITTED", "actorType": "CREATOR", "done": true, "doneAt": "2026-08-06T14:02:00", "doneByName": "민지의 쇼룸", "state": "DONE", "tone": "SUCCESS"},
                  {"key": "OPEN_APPROVED", "actorType": "ADMIN", "done": false, "doneAt": null, "doneByName": null, "state": "MY_TURN", "tone": "WARNING"}
                ]
              },
              "openReview": {"submittedAt": "2026-08-06T14:02:00", "slaBusinessDays": 3, "dueAt": "2026-08-11T23:59:59", "daysLeft": 1, "startAt": "2026-08-14T10:00:00", "overdue": false},
              "post": {"status": "PENDING_APPROVAL", "statusLabel": "승인대기", "statusTone": "INFO", "postNumber": "POST-GB-20260806-104", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "submittedAt": "2026-08-06T14:02:00", "reviewedAt": null, "reviewedByName": null, "lastEditedAt": null, "editCount": 0, "latestRevisionNo": 1, "rejection": null, "hidden": null, "closedBy": null},
              "sales": null,
              "activeRequest": null,
              "extension": null,
              "adminSuspension": null,
              "afterEnd": null,
              "closure": null,
              "permissions": {"canApproveOpen": true, "canRejectOpen": true, "canHidePost": false, "canUnhidePost": false, "canNoticeSuspension": false, "noticeUnavailableReason": "STATUS", "canEmergencySuspend": false, "canExecuteSuspension": false, "canWithdrawSuspension": false, "canApproveRequest": false, "canRejectRequest": false, "canOpenIssue": false, "canConfirmSettlement": false},
              "history": [
                {"eventType": "POST_SUBMITTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-06T14:02:00", "synthetic": false, "revisionNo": null},
            """ + HISTORY_41_PREPARING_TAIL + NAV_LIST;

    // ── B2b 게시물 숨김 중 · 숨김 뒤 인플루언서 수정 (기준 시각 08-18 11:00) ─────────────

    static final String DETAIL_POST_HIDDEN = """
            {
            """ + SUMMARY_41_LIVE + REFS_41 + """
              "readiness": null,
              "openReview": null,
              "post": {"status": "HIDDEN", "statusLabel": "숨김", "statusTone": "WARNING", "postNumber": "POST-GB-20260806-104", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요. 2주 동안 써 보니 제 피부에는 당김이 덜했어요(개인 사용 후기).", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "submittedAt": "2026-08-06T14:02:00", "reviewedAt": "2026-08-07T10:15:00", "reviewedByName": "김운영", "lastEditedAt": "2026-08-16T18:40:00", "editCount": 2, "latestRevisionNo": 3, "rejection": null,
                "hidden": {"code": "AD_EFFECT_ASSERTION", "label": "표시광고법 위반 문구 — 효과 단정", "detail": "본문 마지막 문장 「3일 만에 잔주름까지 사라지는 걸 직접 확인했어요」는 효과를 단정하는 표현입니다. 해당 문장을 삭제하거나 개인 사용 후기로 고쳐 주세요.", "hiddenAt": "2026-08-16T10:20:00", "hiddenByName": "김운영", "hiddenDays": 3, "revisionNo": 2, "ordersSinceHidden": null},
                "closedBy": null},
              "sales": null,
              "activeRequest": null,
              "extension": null,
              "adminSuspension": null,
              "afterEnd": null,
              "closure": null,
              "permissions": {"canApproveOpen": false, "canRejectOpen": false, "canHidePost": false, "canUnhidePost": true, "canNoticeSuspension": false, "noticeUnavailableReason": "NO_WINDOW_BEFORE_END", "canEmergencySuspend": true, "canExecuteSuspension": false, "canWithdrawSuspension": false, "canApproveRequest": false, "canRejectRequest": false, "canOpenIssue": false, "canConfirmSettlement": false},
              "history": [
                {"eventType": "POST_EDITED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "2회차 · 재승인 없음", "occurredAt": "2026-08-16T18:40:00", "synthetic": true, "revisionNo": 3},
                {"eventType": "POST_HIDDEN", "actorType": "ADMIN", "actorDisplayName": "김운영", "detail": "표시광고법 위반 문구 — 효과 단정", "occurredAt": "2026-08-16T10:20:00", "synthetic": false, "revisionNo": null},
                {"eventType": "POST_EDITED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "1회차 · 재승인 없음", "occurredAt": "2026-08-15T21:05:00", "synthetic": true, "revisionNo": 2},
            """ + HISTORY_41_LIVE_TAIL + NAV_LIST;

    // ── B3 중단 요청 검토 (기준 시각 08-18 15:00) ─────────────────────────────────

    static final String DETAIL_SUSPEND_REQUEST = HEAD_41_LIVE + """
              "activeRequest": {
                "requestId": 57,
                "type": "SUSPEND",
                "typeLabel": "공구 중단",
                "requesterType": "CREATOR",
                "requesterName": "민지의 쇼룸",
                "reasonCode": "DELIVERY_FAILURE",
                "reasonLabel": "배송 지연 · 미발송이 계속됨",
                "memo": "08.15 주문분 18건이 아직 발송되지 않았고, 브랜드 답변이 이틀째 없습니다. 팔로워 문의가 계속 들어와 추천을 이어가기 어렵습니다.",
                "statusAtRequest": "IN_PROGRESS",
                "requestedAt": "2026-08-17T20:10:00",
                "elapsed": "18h",
                "decisionBasis": {"ordersAtRequest": null, "ordersNow": null, "ordersSinceRequest": null, "quantityNow": null, "amountNow": null, "inquiries": {"total": null, "defectRelated": null}, "preparedQuantity": null, "sellThroughRate": null, "soldOutInquiriesSinceRequest": null, "originalEndAt": null, "endsImmediatelyIfApproved": null}
              },
              "extension": {"status": "REJECTED", "days": 3, "reason": "초반 반응이 좋아 주말까지 판매를 이어가고 싶습니다.", "beforeEndAt": "2026-08-20T23:55:00", "afterEndAt": "2026-08-23T23:55:00", "beforeTotalDays": 7, "afterTotalDays": 10, "requestedAt": "2026-08-15T09:30:00", "respondDeadlineAt": "2026-08-20T23:55:00", "respondedAt": "2026-08-16T12:00:00", "responseActorType": "CREATOR", "rejectReasonCode": "NEXT_SCHEDULE_BOOKED", "rejectReasonLabel": "다음 일정이 잡혀 있음", "rejectMemo": "08.22부터 다른 브랜드 공구가 잡혀 있어 연장이 어렵습니다."},
              "adminSuspension": null,
              "afterEnd": null,
              "closure": null,
              "permissions": {"canApproveOpen": false, "canRejectOpen": false, "canHidePost": true, "canUnhidePost": false, "canNoticeSuspension": false, "noticeUnavailableReason": "REQUEST_PENDING", "canEmergencySuspend": true, "canExecuteSuspension": false, "canWithdrawSuspension": false, "canApproveRequest": true, "canRejectRequest": true, "canOpenIssue": false, "canConfirmSettlement": false},
              "history": [
                {"eventType": "SUSPENSION_REQUESTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "배송 지연 · 미발송이 계속됨", "occurredAt": "2026-08-17T20:10:00", "synthetic": false, "revisionNo": null},
                {"eventType": "EXTENSION_REJECTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "다음 일정이 잡혀 있음", "occurredAt": "2026-08-16T12:00:00", "synthetic": false, "revisionNo": null},
                {"eventType": "EXTENSION_REQUESTED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": null, "occurredAt": "2026-08-15T09:30:00", "synthetic": false, "revisionNo": null},
            """ + HISTORY_41_LIVE_TAIL + NAV_LIST;

    // ── B4 조기 마감 요청 검토 (기준 시각 08-18 15:00) ──────────────────────────────

    static final String DETAIL_EARLY_CLOSE_REQUEST = HEAD_41_LIVE + """
              "activeRequest": {
                "requestId": 58,
                "type": "EARLY_CLOSE",
                "typeLabel": "조기 마감",
                "requesterType": "SELLER",
                "requesterName": "글로우랩",
                "reasonCode": "STOCK_OUT",
                "reasonLabel": "재고 소진",
                "memo": "세럼 준비 물량이 오늘 오전 모두 소진됐습니다. 추가 생산은 9월 말이라 조기 마감을 요청드립니다.",
                "statusAtRequest": "IN_PROGRESS",
                "requestedAt": "2026-08-18T09:10:00",
                "elapsed": "5h",
                "decisionBasis": {"ordersAtRequest": null, "ordersNow": 318, "ordersSinceRequest": null, "quantityNow": 350, "amountNow": 13515000, "inquiries": null, "preparedQuantity": 350, "sellThroughRate": 100, "soldOutInquiriesSinceRequest": 6, "originalEndAt": "2026-08-20T23:55:00", "endsImmediatelyIfApproved": true}
              },
              "extension": null,
              "adminSuspension": null,
              "afterEnd": null,
              "closure": null,
              "permissions": {"canApproveOpen": false, "canRejectOpen": false, "canHidePost": true, "canUnhidePost": false, "canNoticeSuspension": false, "noticeUnavailableReason": "REQUEST_PENDING", "canEmergencySuspend": true, "canExecuteSuspension": false, "canWithdrawSuspension": false, "canApproveRequest": true, "canRejectRequest": true, "canOpenIssue": false, "canConfirmSettlement": false},
              "history": [
                {"eventType": "EARLY_CLOSE_REQUESTED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": "재고 소진", "occurredAt": "2026-08-18T09:10:00", "synthetic": false, "revisionNo": null},
            """ + HISTORY_41_LIVE_TAIL + NAV_LIST;

    // ── B5 종료 · 이행 이견 · 정산 차단 (기준 시각 08-24 10:00) ─────────────────────

    static final String DETAIL_ENDED = """
            {
              "groupBuy": {"groupBuyId": 41, "groupBuyNumber": "GB-20260803-041", "title": "여름 수분 세럼 공구", "status": "ENDED", "statusLabel": "종료", "statusTone": "INFO", "createdAt": "2026-08-03T15:30:00", "readyAt": "2026-08-07T10:15:00", "openedAt": "2026-08-14T10:00:32", "endedAt": "2026-08-20T23:55:00", "closeType": "COMPLETED", "closeTypeLabel": "기간 종료", "settledAt": null},
              "timeline": {"startAt": "2026-08-14T10:00:00", "endAt": "2026-08-20T23:55:00", "originalEndAt": "2026-08-20T23:55:00", "totalDays": 7, "elapsedDays": 7, "daysUntilStart": null, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS_41 + """
              "readiness": null,
              "openReview": null,
              "post": {"status": "CLOSED", "statusLabel": "종료", "statusTone": "NEUTRAL", "postNumber": "POST-GB-20260806-104", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "disclosureText": "유료 광고 포함 · 글로우랩으로부터 대가를 받아 진행하는 공동구매입니다", "submittedAt": "2026-08-06T14:02:00", "reviewedAt": "2026-08-07T10:15:00", "reviewedByName": "김운영", "lastEditedAt": null, "editCount": 0, "latestRevisionNo": 1, "rejection": null, "hidden": null, "closedBy": "COMPLETED"},
              "sales": null,
              "activeRequest": null,
              "extension": null,
              "adminSuspension": null,
              "afterEnd": {
                "fulfillment": {
                  "brandToCreator": {"result": "FULFILLED", "reason": null, "checkedAt": "2026-08-21T14:00:00", "checkedByName": "글로우랩", "auto": false},
                  "creatorToBrand": {"result": "UNFULFILLED", "reason": "08.15 주문분 18건이 종료 후에도 미발송 상태입니다. 팔로워 환불 문의가 계속 들어오고 있습니다.", "checkedAt": "2026-08-22T10:30:00", "checkedByName": "민지의 쇼룸", "auto": false},
                  "targets": {
                    "brandToCreator": {"duties": ["SHOWROOM_POST", "FEED", "STORY"], "counts": {"feed": 1, "reels": 0, "story": 3}},
                    "creatorToBrand": {"duties": ["ORDER_DELIVERY"], "counts": null}
                  },
                  "dueAt": "2026-08-23T23:55:00",
                  "duePassed": true,
                  "autoConfirmOnTimeout": false,
                  "threadId": 412,
                  "agreedAt": null,
                  "resolutionNote": null,
                  "resolvedAt": null,
                  "onHold": true
                },
                "settlement": {
                  "stage": "WAITING",
                  "stageSource": "DERIVED",
                  "blockers": ["UNCLOSED_ORDERS", "FULFILLMENT_DISPUTE"],
                  "watch": {"dueAt": "2026-09-19", "elapsedDays": 4, "reached": false},
                  "preview": {"provisionalSalesAmount": 13515000, "rewardRates": [15, 12.5], "rewardAmount": null}
                },
                "orderClosure": {"totalCount": 312, "closedCount": 294, "unclosedCount": 18, "unclosed": [{"stage": "SHIPPING", "label": "배송중", "count": 12}, {"stage": "DELIVERED", "label": "배송완료", "count": 6}], "purchaseConfirmedCount": 275, "refundedCount": 19},
                "openIssue": null
              },
              "closure": null,
              "permissions": {"canApproveOpen": false, "canRejectOpen": false, "canHidePost": false, "canUnhidePost": false, "canNoticeSuspension": false, "noticeUnavailableReason": "STATUS", "canEmergencySuspend": false, "canExecuteSuspension": false, "canWithdrawSuspension": false, "canApproveRequest": false, "canRejectRequest": false, "canOpenIssue": true, "canConfirmSettlement": false},
              "history": [
                {"eventType": "FULFILLMENT_DISPUTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-22T10:30:00", "synthetic": false, "revisionNo": null},
                {"eventType": "FULFILLMENT_CONFIRMED", "actorType": "SELLER", "actorDisplayName": "글로우랩", "detail": null, "occurredAt": "2026-08-21T14:00:00", "synthetic": false, "revisionNo": null},
                {"eventType": "ENDED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-20T23:55:00", "synthetic": false, "revisionNo": null},
            """ + HISTORY_41_LIVE_TAIL + NAV_LIST;

    // ── 상세 조각 · 공구 45 ──────────────────────────────────────────────────

    /** 고정 지급비가 없는 계약 — fixedFee 네 칸이 모두 null이다. */
    private static final String REFS_45 = """
              "brand": {"marketId": 11, "name": "퓨어셀", "pairThreadId": 331},
              "creator": {"creatorId": 9, "name": "민지의 쇼룸", "accountId": "minji"},
              "contract": {"contractId": 19, "contractNumber": "CTR-20260818-019", "concludedAt": "2026-08-20T11:00:00"},
              "items": [
                {"productId": 131, "productName": "퓨어셀 비타민C 앰플 30ml", "regularPrice": 45000, "groupBuyPrice": 33000, "rewardRate": 10.0, "expectedUnitReward": 3300, "minQuantity": 300,
                  "options": [{"variantId": 401, "variantName": null, "salePrice": 33000, "minQuantity": 300}]}
              ],
              "fixedFee": {"amount": null, "trigger": null, "triggerLabel": null, "displayText": null},
            """;

    private static final String NOTICE_BODY_45 =
            "상세페이지와 공구 게시물에 「기미 완치」 표현이 노출되어 화장품법 제13조(부당한 표시·광고 금지) 위반 소지가 있습니다. "
                    + "09.10까지 해당 표현을 삭제하고 소명 자료를 제출해 주세요.";

    /** 공구 45 이력 꼬리 — 통지부터 생성까지. 배열을 닫는다. */
    private static final String HISTORY_45_NOTICED_TAIL = """
                {"eventType": "SUSPENSION_NOTICED", "actorType": "ADMIN", "actorDisplayName": "김운영", "detail": "제17조① 1호 법령 위반 · 집행 예정 09.11 10:00 · 소명 기한 09.10", "occurredAt": "2026-09-07T11:00:00", "synthetic": false, "revisionNo": null},
                {"eventType": "OPENED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-09-01T10:00:15", "synthetic": false, "revisionNo": null},
                {"eventType": "OPEN_APPROVED", "actorType": "ADMIN", "actorDisplayName": "김운영", "detail": null, "occurredAt": "2026-08-26T10:05:00", "synthetic": false, "revisionNo": null},
                {"eventType": "POST_SUBMITTED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": null, "occurredAt": "2026-08-24T15:00:00", "synthetic": false, "revisionNo": null},
                {"eventType": "STOCK_CONFIRMED", "actorType": "SELLER", "actorDisplayName": "퓨어셀", "detail": null, "occurredAt": "2026-08-21T09:40:00", "synthetic": false, "revisionNo": null},
                {"eventType": "CREATED", "actorType": "SYSTEM", "actorDisplayName": null, "detail": null, "occurredAt": "2026-08-20T11:00:00", "synthetic": false, "revisionNo": null}
              ],
            """;

    private static final String HISTORY_45_APPEALED_TAIL = """
                {"eventType": "APPEAL_SUBMITTED", "actorType": "SELLER", "actorDisplayName": "퓨어셀", "detail": null, "occurredAt": "2026-09-09T16:20:00", "synthetic": false, "revisionNo": null},
                {"eventType": "POST_EDITED", "actorType": "CREATOR", "actorDisplayName": "민지의 쇼룸", "detail": "1회차 · 재승인 없음", "occurredAt": "2026-09-08T13:10:00", "synthetic": true, "revisionNo": 2},
            """ + HISTORY_45_NOTICED_TAIL;

    // ── B2c 직권 중단 통지 중 · 소명 검토 (기준 시각 09-11 10:30) ────────────────────

    static final String DETAIL_APPEAL_REVIEW = """
            {
              "groupBuy": {"groupBuyId": 45, "groupBuyNumber": "GB-20260820-045", "title": "비타민 앰플 공구", "status": "SUSPENSION_SCHEDULED", "statusLabel": "중단 예정", "statusTone": "WARNING", "createdAt": "2026-08-20T11:00:00", "readyAt": "2026-08-26T10:05:00", "openedAt": "2026-09-01T10:00:15", "endedAt": null, "closeType": null, "closeTypeLabel": null, "settledAt": null},
              "timeline": {"startAt": "2026-09-01T10:00:00", "endAt": "2026-09-21T23:55:00", "originalEndAt": "2026-09-21T23:55:00", "totalDays": 21, "elapsedDays": 11, "daysUntilStart": null, "daysUntilEnd": 10, "startOverdue": false},
            """ + REFS_45 + """
              "readiness": null,
              "openReview": null,
              "post": {"status": "EXPOSED", "statusLabel": "노출중", "statusTone": "SUCCESS", "postNumber": "POST-GB-20260824-118", "title": "비타민C 앰플, 한 달 써 본 솔직 후기", "content": "아침마다 두 방울씩 한 달 썼어요. 피부 톤이 한결 맑아 보였어요(개인 사용 후기). 끈적임 없이 흡수가 빨라요.", "disclosureText": "유료 광고 포함 · 퓨어셀로부터 대가를 받아 진행하는 공동구매입니다", "submittedAt": "2026-08-24T15:00:00", "reviewedAt": "2026-08-26T10:05:00", "reviewedByName": "김운영", "lastEditedAt": "2026-09-08T13:10:00", "editCount": 1, "latestRevisionNo": 2, "rejection": null, "hidden": null, "closedBy": null},
              "sales": null,
              "activeRequest": null,
              "extension": null,
              "adminSuspension": {
                "adminSuspensionId": 23,
                "kind": "NOTICE",
                "clause": "ART17_1_LAW",
                "clauseLabel": "제17조① 1호 법령 위반",
                "noticeBody": \"""" + NOTICE_BODY_45 + """
            ",
                "noticedAt": "2026-09-07T11:00:00",
                "noticedByName": "김운영",
                "elapsedDays": 4,
                "executeScheduledAt": "2026-09-11T10:00:00",
                "appealDeadlineAt": "2026-09-10T23:59:59",
                "noticeRevisionNo": 1,
                "salesSinceNotice": null,
                "appeal": {
                  "content": "게시물 문구는 인플루언서가 작성한 것으로, 브랜드는 09.08 오전 수정을 요청해 같은 날 반영됐습니다. 상세페이지 문구는 09.08 14시에 삭제했습니다(캡처 첨부).",
                  "submittedAt": "2026-09-09T16:20:00",
                  "submittedByName": "박지현",
                  "attachments": [
                    {"attachmentId": 71, "name": "상세페이지_수정_캡처.png", "contentType": "image/png", "sizeBytes": 482113},
                    {"attachmentId": 72, "name": "인플루언서_수정요청_내역.pdf", "contentType": "application/pdf", "sizeBytes": 1204551}
                  ]
                },
                "appealDeadlinePassed": true
              },
              "afterEnd": null,
              "closure": null,
              "permissions": {"canApproveOpen": false, "canRejectOpen": false, "canHidePost": true, "canUnhidePost": false, "canNoticeSuspension": false, "noticeUnavailableReason": "STATUS", "canEmergencySuspend": true, "canExecuteSuspension": true, "canWithdrawSuspension": true, "canApproveRequest": false, "canRejectRequest": false, "canOpenIssue": false, "canConfirmSettlement": false},
              "history": [
            """ + HISTORY_45_APPEALED_TAIL + NAV_NONE;

    // ── B6 직권 중단 집행으로 종결 (기준 시각 09-11 11:00) ───────────────────────────

    private static final String EXECUTION_NOTE_45 =
            "소명 자료를 확인했습니다. 게시물 문구는 09.08 수정됐으나 09.10 기준 상세페이지에 동일 표현이 남아 있어 위반이 해소되지 않았습니다. "
                    + "통지한 대로 중단을 집행합니다.";

    static final String DETAIL_SUSPENDED_BY_NOTICE = """
            {
              "groupBuy": {"groupBuyId": 45, "groupBuyNumber": "GB-20260820-045", "title": "비타민 앰플 공구", "status": "SUSPENDED", "statusLabel": "중단", "statusTone": "DANGER", "createdAt": "2026-08-20T11:00:00", "readyAt": "2026-08-26T10:05:00", "openedAt": "2026-09-01T10:00:15", "endedAt": "2026-09-11T10:40:00", "closeType": "SUSPENDED", "closeTypeLabel": "중단", "settledAt": null},
              "timeline": {"startAt": "2026-09-01T10:00:00", "endAt": "2026-09-21T23:55:00", "originalEndAt": "2026-09-21T23:55:00", "totalDays": 21, "elapsedDays": 11, "daysUntilStart": null, "daysUntilEnd": null, "startOverdue": false},
            """ + REFS_45 + """
              "readiness": null,
              "openReview": null,
              "post": {"status": "CLOSED", "statusLabel": "종료", "statusTone": "NEUTRAL", "postNumber": "POST-GB-20260824-118", "title": "비타민C 앰플, 한 달 써 본 솔직 후기", "content": "아침마다 두 방울씩 한 달 썼어요. 피부 톤이 한결 맑아 보였어요(개인 사용 후기). 끈적임 없이 흡수가 빨라요.", "disclosureText": "유료 광고 포함 · 퓨어셀로부터 대가를 받아 진행하는 공동구매입니다", "submittedAt": "2026-08-24T15:00:00", "reviewedAt": "2026-08-26T10:05:00", "reviewedByName": "김운영", "lastEditedAt": "2026-09-08T13:10:00", "editCount": 1, "latestRevisionNo": 2, "rejection": null, "hidden": null, "closedBy": "SUSPENDED"},
              "sales": null,
              "activeRequest": null,
              "extension": null,
              "adminSuspension": null,
              "afterEnd": {"fulfillment": null, "settlement": null, "orderClosure": null, "openIssue": null},
              "closure": {
                "closeType": "SUSPENDED",
                "closeTypeLabel": "중단",
                "endedAt": "2026-09-11T10:40:00",
                "source": "ADMIN_NOTICE",
                "reasonLabel": "제17조① 1호 법령 위반",
                "reasonDetail": \"""" + NOTICE_BODY_45 + """
            ",
                "requester": null,
                "decidedByName": "김운영",
                "decisionReason": \"""" + EXECUTION_NOTE_45 + """
            ",
                "acceptedOrderCount": null,
                "adminBasis": {"kind": "NOTICE", "clause": "ART17_1_LAW", "emergencyReason": null, "basisLabel": "제17조① 1호 법령 위반", "body": \"""" + NOTICE_BODY_45 + "\", \"executionNote\": \"" + EXECUTION_NOTE_45 + """
            "}
              },
              "permissions": {"canApproveOpen": false, "canRejectOpen": false, "canHidePost": false, "canUnhidePost": false, "canNoticeSuspension": false, "noticeUnavailableReason": "STATUS", "canEmergencySuspend": false, "canExecuteSuspension": false, "canWithdrawSuspension": false, "canApproveRequest": false, "canRejectRequest": false, "canOpenIssue": true, "canConfirmSettlement": false},
              "history": [
                {"eventType": "SUSPENDED_BY_ADMIN", "actorType": "ADMIN", "actorDisplayName": "김운영", "detail": "제17조① 1호 법령 위반", "occurredAt": "2026-09-11T10:40:00", "synthetic": false, "revisionNo": null},
            """ + HISTORY_45_APPEALED_TAIL + NAV_NONE;

    // ── 게시물 판본 · 통지 선택지 · 첨부 ────────────────────────────────────────

    /** 공구 41 — 승인 판(1) · 숨김 기준 판(2) · 숨김 뒤 수정한 최신 판(3). 해제 전이라 unhiddenBasis가 없다. */
    static final String POST_REVISIONS = """
            [
              {"revisionNo": 1, "kind": "SUBMITTED", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요.", "createdAt": "2026-08-06T14:02:00", "approved": true, "hiddenBasis": false, "unhiddenBasis": false, "noticeBasis": false, "latest": false},
              {"revisionNo": 2, "kind": "EDITED", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요. 3일 만에 잔주름까지 사라지는 걸 직접 확인했어요!", "createdAt": "2026-08-15T21:05:00", "approved": false, "hiddenBasis": true, "unhiddenBasis": false, "noticeBasis": false, "latest": false},
              {"revisionNo": 3, "kind": "EDITED", "title": "여름 수분 세럼, 제가 쓰던 그 조합", "content": "건조한 여름에도 속당김 없이 쓰던 세럼·크림 조합이에요. 세럼 두 방울 뒤 크림으로 마무리하면 오후까지 촉촉해요. 2주 동안 써 보니 제 피부에는 당김이 덜했어요(개인 사용 후기).", "createdAt": "2026-08-16T18:40:00", "approved": false, "hiddenBasis": false, "unhiddenBasis": false, "noticeBasis": false, "latest": true}
            ]
            """;

    /**
     * 공구 45 · 통지일 09-07(월) — 소명 기한 하한 09-10(목) 23:59:59. 09-08~09-10은 3영업일 전이거나 소명 기한과 같은 날이라 잠긴다.
     * 주말(09-12 · 09-13 · 09-19 · 09-20)은 칩에 없다.
     */
    static final String NOTICE_OPTIONS = """
            {
              "today": "2026-09-07",
              "appealDeadline": {"default": "2026-09-10T23:59:59", "min": "2026-09-10T23:59:59"},
              "executionDates": [
                {"date": "2026-09-08", "selectable": false},
                {"date": "2026-09-09", "selectable": false},
                {"date": "2026-09-10", "selectable": false},
                {"date": "2026-09-11", "selectable": true},
                {"date": "2026-09-14", "selectable": true},
                {"date": "2026-09-15", "selectable": true},
                {"date": "2026-09-16", "selectable": true},
                {"date": "2026-09-17", "selectable": true},
                {"date": "2026-09-18", "selectable": true},
                {"date": "2026-09-21", "selectable": true}
              ],
              "latestExecutionBefore": "2026-09-21T23:55:00",
              "available": true,
              "unavailableReason": null
            }
            """;

    /** 공구 41 · 08-18(화) — 소명 기한 하한(08-21)이 종료(08-20 23:55) 뒤라 통지할 창이 없다. 긴급 중단만 남는다. */
    static final String NOTICE_OPTIONS_NO_WINDOW = """
            {
              "today": "2026-08-18",
              "appealDeadline": {"default": "2026-08-21T23:59:59", "min": "2026-08-21T23:59:59"},
              "executionDates": [
                {"date": "2026-08-19", "selectable": false},
                {"date": "2026-08-20", "selectable": false}
              ],
              "latestExecutionBefore": "2026-08-20T23:55:00",
              "available": false,
              "unavailableReason": "NO_WINDOW_BEFORE_END"
            }
            """;

    static final String ATTACHMENT_URL = """
            {
              "attachmentId": 71,
              "url": "https://{bucket}.s3.ap-northeast-2.amazonaws.com/uploads/group-buy/appeal/23/5c1e9a7b-2f4d-4e8a-9b61-0d3c7f2a8e14.png?response-content-disposition=attachment...&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=300&...",
              "fileName": "상세페이지_수정_캡처.png",
              "expiresInSeconds": 300
            }
            """;

    // ── 판정 결과(ActionResponse) ────────────────────────────────────────────

    static final String ACTION_APPROVE_READY = """
            {"groupBuyId": 41, "status": "READY", "statusLabel": "준비완료", "postStatus": "SCHEDULED", "postStatusLabel": "예약", "openAt": "2026-08-14T10:00:00", "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_APPROVE_WAITING_STOCK = """
            {"groupBuyId": 41, "status": "PREPARING", "statusLabel": "준비중", "postStatus": "SCHEDULED", "postStatusLabel": "예약", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_REJECT_OPEN = """
            {"groupBuyId": 41, "status": "PREPARING", "statusLabel": "준비중", "postStatus": "REJECTED", "postStatusLabel": "반려", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_HIDE = """
            {"groupBuyId": 41, "status": "IN_PROGRESS", "statusLabel": "진행중", "postStatus": "HIDDEN", "postStatusLabel": "숨김", "openAt": null, "revisionAdvanced": false, "clauseCaution": null}
            """;

    static final String ACTION_HIDE_REVISION_ADVANCED = """
            {"groupBuyId": 41, "status": "IN_PROGRESS", "statusLabel": "진행중", "postStatus": "HIDDEN", "postStatusLabel": "숨김", "openAt": null, "revisionAdvanced": true, "clauseCaution": null}
            """;

    static final String ACTION_HIDE_READY = """
            {"groupBuyId": 48, "status": "READY", "statusLabel": "준비완료", "postStatus": "HIDDEN", "postStatusLabel": "숨김", "openAt": "2026-09-12T10:00:00", "revisionAdvanced": false, "clauseCaution": null}
            """;

    static final String ACTION_UNHIDE = """
            {"groupBuyId": 41, "status": "IN_PROGRESS", "statusLabel": "진행중", "postStatus": "EXPOSED", "postStatusLabel": "노출중", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_NOTICE = """
            {"groupBuyId": 45, "status": "SUSPENSION_SCHEDULED", "statusLabel": "중단 예정", "postStatus": "EXPOSED", "postStatusLabel": "노출중", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_NOTICE_CLAUSE3 = """
            {"groupBuyId": 45, "status": "SUSPENSION_SCHEDULED", "statusLabel": "중단 예정", "postStatus": "EXPOSED", "postStatusLabel": "노출중", "openAt": null, "revisionAdvanced": null, "clauseCaution": "C2_POST_ALTERATION"}
            """;

    static final String ACTION_SUSPENDED_45 = """
            {"groupBuyId": 45, "status": "SUSPENDED", "statusLabel": "중단", "postStatus": "CLOSED", "postStatusLabel": "종료", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_WITHDRAW = """
            {"groupBuyId": 45, "status": "IN_PROGRESS", "statusLabel": "진행중", "postStatus": "EXPOSED", "postStatusLabel": "노출중", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_APPROVE_SUSPEND = """
            {"groupBuyId": 41, "status": "SUSPENDED", "statusLabel": "중단", "postStatus": "CLOSED", "postStatusLabel": "종료", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_APPROVE_EARLY_CLOSE = """
            {"groupBuyId": 41, "status": "ENDED", "statusLabel": "종료", "postStatus": "CLOSED", "postStatusLabel": "종료", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ACTION_REJECT_REQUEST = """
            {"groupBuyId": 41, "status": "IN_PROGRESS", "statusLabel": "진행중", "postStatus": "EXPOSED", "postStatusLabel": "노출중", "openAt": null, "revisionAdvanced": null, "clauseCaution": null}
            """;

    static final String ISSUE_OPENED = """
            {"issueId": 9, "threadId": 418}
            """;

    // ── 요청 바디 ────────────────────────────────────────────────────────────

    static final String REQ_OPEN_REJECT = """
            {
              "reasonCode": "AD_EFFECT_ASSERTION",
              "detail": "본문 두 번째 문장 「3일 만에 잔주름까지 사라져요」는 효과를 단정하는 표현입니다. 해당 문장을 삭제하거나 「제 피부에는 ~했어요」처럼 개인 사용 후기로 고쳐 주세요."
            }
            """;

    static final String REQ_OPEN_REJECT_PRICE = """
            {
              "reasonCode": "CONTRACT_PRICE_MISMATCH",
              "detail": "본문의 세럼 가격이 26,000원으로 적혀 있습니다. 계약 공구가는 28,000원입니다. 가격 문장을 계약과 같게 고쳐 주세요."
            }
            """;

    static final String REQ_POST_HIDE = """
            {
              "reasonCode": "AD_EFFECT_ASSERTION",
              "detail": "본문 마지막 문장 「3일 만에 잔주름까지 사라지는 걸 직접 확인했어요」는 효과를 단정하는 표현입니다. 해당 문장을 삭제하거나 개인 사용 후기로 고쳐 주세요.",
              "observedRevisionNo": 2
            }
            """;

    static final String REQ_POST_UNHIDE = """
            {"expectedRevisionNo": 3}
            """;

    static final String REQ_NOTICE = """
            {
              "clause": "ART17_1_LAW",
              "executeScheduledAt": "2026-09-11T10:00:00",
              "appealDeadlineAt": "2026-09-10T23:59:59",
              "noticeBody": \"""" + NOTICE_BODY_45 + """
            "
            }
            """;

    static final String REQ_EXECUTE = """
            {"executionNote": \"""" + EXECUTION_NOTE_45 + """
            "}
            """;

    static final String REQ_WITHDRAW = """
            {
              "reasonCode": "RECTIFIED",
              "detail": "상세페이지와 게시물의 「기미 완치」 표현이 모두 삭제된 것을 09.10 확인했습니다. 공구는 원래 일정대로 09.21까지 진행됩니다."
            }
            """;

    static final String REQ_EMERGENCY = """
            {
              "emergencyReason": "CONSUMER_HARM",
              "body": "앰플 사용 후 피부 발진·화끈거림 신고가 24시간 동안 14건 접수되었습니다. 소비자 위해 방지를 위해 판매를 즉시 중단합니다. 브랜드는 성분 시험 성적서를 이슈 스레드로 제출해 주세요."
            }
            """;

    static final String REQ_APPROVE_SUSPEND = """
            {"decisionReason": "미발송 18건을 확인했습니다. 소비자 보호를 위해 중단을 승인합니다. 접수된 주문은 브랜드가 모두 발송하거나 환불 처리해야 합니다."}
            """;

    static final String REQ_APPROVE_EARLY_CLOSE = """
            {"decisionReason": "재고 소진과 재입고 문의 6건을 확인해 조기 마감을 승인합니다. 승인 시각이 종료 시각이며, 판매 리워드는 종료 시점까지의 판매분으로 정산됩니다."}
            """;

    static final String REQ_REJECT_REQUEST = """
            {"decisionReason": "현재 확인된 미발송은 3건이고 브랜드가 08.19까지 발송을 약속했습니다. 08.19 이후에도 미발송이 남으면 주문번호와 함께 다시 요청해 주세요."}
            """;

    static final String REQ_ISSUE = """
            {
              "issueType": "CONTENT_FULFILLMENT",
              "content": "스토리 3건 중 1건이 게시되지 않았다는 브랜드 측 주장과 인플루언서 측 이행 주장이 다릅니다. 양측은 게시 URL과 게시 일시를 이 스레드에 남겨 주세요."
            }
            """;

    // ── 에러 ─────────────────────────────────────────────────────────────────

    static final String ERR_NOT_FOUND = """
            {"code": "GROUP_BUY_NOT_FOUND", "message": "존재하지 않는 공구입니다."}
            """;

    static final String ERR_ATTACHMENT_NOT_FOUND = """
            {"code": "GROUP_BUY_APPEAL_ATTACHMENT_NOT_FOUND", "message": "존재하지 않는 소명 첨부입니다."}
            """;

    static final String ERR_INVALID_ENUM = """
            {"code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다."}
            """;

    static final String ERR_NOT_NULL = """
            {"code": "INVALID_INPUT", "message": "널이어서는 안됩니다"}
            """;

    static final String ERR_SIZE_1000 = """
            {"code": "INVALID_INPUT", "message": "크기가 0에서 1000 사이여야 합니다"}
            """;

    static final String ERR_SIZE_2000 = """
            {"code": "INVALID_INPUT", "message": "크기가 0에서 2000 사이여야 합니다"}
            """;

    static final String ERR_REJECT_DETAIL_REQUIRED = """
            {"code": "GROUP_BUY_REJECT_DETAIL_REQUIRED", "message": "인플루언서에게 전달할 설명을 입력해 주세요."}
            """;

    static final String ERR_DECISION_REASON_REQUIRED = """
            {"code": "GROUP_BUY_DECISION_REASON_REQUIRED", "message": "양측에 전달할 사유를 입력해 주세요."}
            """;

    static final String ERR_OPEN_REVIEW_NOT_PENDING = """
            {"code": "GROUP_BUY_OPEN_REVIEW_NOT_PENDING", "message": "오픈 승인 대기 중인 게시물이 아닙니다."}
            """;

    static final String ERR_POST_NOT_HIDEABLE = """
            {"code": "GROUP_BUY_POST_NOT_HIDEABLE", "message": "지금 상태에서는 게시물을 숨길 수 없습니다."}
            """;

    static final String ERR_POST_ALREADY_HIDDEN = """
            {"code": "GROUP_BUY_POST_ALREADY_HIDDEN", "message": "이미 숨김 처리된 게시물입니다."}
            """;

    static final String ERR_POST_NOT_HIDDEN = """
            {"code": "GROUP_BUY_POST_NOT_HIDDEN", "message": "숨김 상태인 게시물이 아닙니다."}
            """;

    static final String ERR_POST_CHANGED_SINCE_VIEW = """
            {"code": "GROUP_BUY_POST_CHANGED_SINCE_VIEW", "message": "확인하신 뒤 게시물이 수정되었습니다. 최신 본문을 확인한 후 다시 시도해 주세요."}
            """;

    static final String ERR_STATUS_CONFLICT = """
            {"code": "GROUP_BUY_STATUS_CONFLICT", "message": "공구 상태가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요."}
            """;

    static final String ERR_REQUEST_DECIDE_FIRST = """
            {"code": "GROUP_BUY_REQUEST_DECIDE_FIRST", "message": "검토 중인 요청을 먼저 판정해 주세요."}
            """;

    static final String ERR_SUSPENSION_ALREADY_NOTICED = """
            {"code": "GROUP_BUY_SUSPENSION_ALREADY_NOTICED", "message": "이미 진행 중인 직권 중단 통지가 있습니다."}
            """;

    static final String ERR_SUSPENSION_SCHEDULE_INVALID = """
            {"code": "GROUP_BUY_SUSPENSION_SCHEDULE_INVALID", "message": "집행 예정 일시·소명 기한이 규정을 충족하지 않습니다."}
            """;

    static final String ERR_SUSPENSION_NOT_NOTICED = """
            {"code": "GROUP_BUY_SUSPENSION_NOT_NOTICED", "message": "진행 중인 직권 중단 통지가 없습니다."}
            """;

    static final String ERR_SUSPENSION_EXECUTION_NOT_DUE = """
            {"code": "GROUP_BUY_SUSPENSION_EXECUTION_NOT_DUE", "message": "집행 예정 일시가 되지 않았습니다."}
            """;

    static final String ERR_CHANGE_REQUEST_NOT_PENDING = """
            {"code": "GROUP_BUY_CHANGE_REQUEST_NOT_PENDING", "message": "검토 중인 요청이 아닙니다."}
            """;

    static final String ERR_ISSUE_ALREADY_OPEN = """
            {"code": "GROUP_BUY_ISSUE_ALREADY_OPEN", "message": "이미 진행 중인 이슈 스레드가 있습니다."}
            """;

    static final String ERR_ACTION_NOT_ALLOWED = """
            {"code": "GROUP_BUY_ACTION_NOT_ALLOWED", "message": "지금 상태에서는 요청할 수 없습니다."}
            """;

    static final String ERR_THREAD_UNAVAILABLE = """
            {"code": "GROUP_BUY_THREAD_UNAVAILABLE", "message": "지금은 스레드를 열 수 없습니다. 운영자에게 문의해 주세요."}
            """;

    static final String ERR_SETTLEMENT_NOT_READY = """
            {"code": "GROUP_BUY_SETTLEMENT_NOT_READY", "message": "정산 확인 조건이 충족되지 않았습니다."}
            """;
}
