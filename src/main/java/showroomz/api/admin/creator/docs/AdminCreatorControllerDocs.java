package showroomz.api.admin.creator.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.creator.dto.AdminCreatorDto.*;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PageResponse;

@Tag(name = "Admin - Influencer Member", description = "관리자 인플루언서 회원 관리 API")
public interface AdminCreatorControllerDocs {

    @Operation(summary = "인플루언서 회원 목록",
            description = """
                    ui-admin-16 rev.7 · 55 계획서 M1 — 브랜드 목록(`GET /v1/admin/brands`)과 같은 골격입니다.

                    - `tab`: `ALL`(기본) · `ACTIVE` · `SUSPENDED` · `WITHDRAWAL_PENDING` · `WITHDRAWN` — **건수가 서로 겹치지 않습니다**.
                      탈퇴 승인 대기 행은 활성 · 정지 탭에서 빠지고(정지 중에 신청했어도 대기 탭), 원래 상태는 `status` 에 그대로 있습니다.
                    - **전체 탭은 탈퇴 승인 대기 행이 맨 위에 고정**되고(고정 행끼리는 아래 정렬), 그 밖은 `sort` 를 따릅니다.
                    - `keyword`: `INF-`로 시작하면 회원번호 정확 일치(접두사 뒤가 숫자가 아니면 0건) · 그 외 **쇼룸명 또는 업무용 이메일** 부분 일치(대소문자 무시)
                    - `sort`: `RECENT_JOINED`(기본 · 가입 최신순) · `MEMBER_NO`(회원번호순)
                    - 행: 쇼룸명 · 인스타 바로가기(`instagramUrl` · 미등록 · 탈퇴면 null) · 업무용 이메일 · 사업자 여부 · 가입일 · 연결 브랜드(연결중 쌍) · 표시 상태
                    - `summary` 는 탭 5종 건수(모두 검색어 범위)이고, 대기 탭에서만 `longestPendingDays`(「최장 대기 N일」 · 날짜 차)가 있습니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ListResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "creatorId": 3021, "memberNo": "INF-3021", "showroomName": "하린의 뷰티랩",
                                          "instagramUrl": "https://instagram.com/harin.beautylab", "businessEmail": "harin@beautylab.kr",
                                          "businessType": "BUSINESS", "businessTypeLabel": "사업자", "joinedAt": "2026-04-02T10:00:00",
                                          "connectedBrandCount": 4, "status": "ACTIVE", "statusLabel": "활성",
                                          "listStatus": "WITHDRAWAL_PENDING", "listStatusLabel": "탈퇴 승인 대기", "listStatusTone": "WARNING",
                                          "withdrawalPending": true, "withdrawalRequestedAt": "2026-10-20T09:30:00" },
                                        { "creatorId": 3018, "memberNo": "INF-3018", "showroomName": "지민의 데일리",
                                          "instagramUrl": null, "businessEmail": "jimin@daily.kr",
                                          "businessType": "INDIVIDUAL", "businessTypeLabel": "비사업자", "joinedAt": "2026-07-11T15:20:00",
                                          "connectedBrandCount": 0, "status": "ACTIVE", "statusLabel": "활성",
                                          "listStatus": "ACTIVE", "listStatusLabel": "활성", "listStatusTone": "SUCCESS",
                                          "withdrawalPending": false, "withdrawalRequestedAt": null }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false },
                                      "summary": { "total": 18, "active": 14, "suspended": 1, "withdrawalPending": 2, "withdrawn": 1,
                                                   "longestPendingDays": null }
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ListResponse list(@Parameter(description = "탭 — ALL(기본) · ACTIVE · SUSPENDED · WITHDRAWAL_PENDING · WITHDRAWN", example = "ALL")
                      showroomz.api.admin.common.AdminMemberListTab tab,
                      @Parameter(description = "`INF-3018`(회원번호 정확 일치) 또는 쇼룸명 · 업무용 이메일(부분 일치)", example = "하린")
                      String keyword,
                      @Parameter(description = "정렬 — RECENT_JOINED(기본) · MEMBER_NO", example = "RECENT_JOINED")
                      showroomz.api.admin.common.AdminMemberListSort sort,
                      @org.springdoc.core.annotations.ParameterObject showroomz.global.dto.PagingRequest paging);

    /** 상세 · 조치 응답 예시 — 정지 상태 · 탈퇴 신청 심사 중. */
    String DETAIL_EXAMPLE = """
            {
              "creatorId": 3018, "memberNo": "INF-3018", "status": "SUSPENDED", "statusLabel": "정지", "statusTone": "DANGER",
              "navigation": { "previousId": 3017, "nextId": 3019 },
              "basic": {
                "showroomName": "소연 쇼룸", "profileImageUrl": "https://cdn…/profile/3018.jpg",
                "showroomAddress": "soyeon_showroom", "showroomUrl": "https://showroomz.kr/soyeon_showroom", "showroomPublic": true,
                "instagramUrl": "https://instagram.com/soyeon_showroom", "businessEmail": "soyeon@example.com",
                "joinedAt": "2026-06-20T10:00:00", "onboardingApprovedAt": "2026-07-01T14:00:00",
                "lastLoginAt": "2026-10-15T21:03:00", "lastLoginDevice": "IPHONE"
              },
              "identity": { "destroyed": false, "unmasked": false, "name": "김*연", "birthday": "1994.**.**", "age": null,
                            "phone": "010-****-8842", "carrier": null, "firstVerifiedAt": "2026-06-20T10:02:00", "renewedAt": null },
              "unmask": { "allowed": true, "unmasked": false, "operatorName": null, "unmaskedAt": null, "expiresAt": null,
                          "remainingSeconds": null, "lastReason": "SETTLEMENT_WITHHOLDING_CHECK" },
              "business": { "destroyed": false, "businessType": "INDIVIDUAL", "businessTypeLabel": "개인 (비사업자)",
                            "businessRegistrationNumber": null, "businessLicenseImageUrl": null, "residentNumberRegistered": true,
                            "withholdingType": "WITHHOLDING_3_3", "withholdingLabel": "원천징수 3.3%" },
              "settlementAccount": {
                "destroyed": false, "bankName": "국민은행", "accountHolder": "김*연", "accountNumber": "333*-**-**8842",
                "lastChange": { "changedAt": "2026-08-29T14:02:00", "previousBankName": "신한은행", "previousLast4": "3301" },
                "pendingBusinessTypeChange": { "changeRequestId": 83, "requestCode": "CHG-2026-0083", "type": "TO_BUSINESS",
                                               "requestedAt": "2026-10-12T09:00:00" }
              },
              "rail": {
                "currentStatus": {
                  "status": "SUSPENDED", "label": "정지", "tone": "DANGER",
                  "joinedAt": "2026-06-20T10:00:00", "onboardingApprovedAt": "2026-07-01T14:00:00", "lastLoginAt": "2026-10-15T21:03:00",
                  "suspension": { "sanctionId": 5, "reason": "AD_DISCLOSURE_OMITTED_REPEATED", "reasonLabel": "협찬 표기 누락 반복",
                                  "reasonDetail": "공구 게시물 3건 협찬 표기 누락 — 2회 안내 후 반복", "suspendedAt": "2026-10-16T11:00:00",
                                  "operatorName": "김운영", "payoutHeld": false, "payoutNote": "유지 · 사유가 정산 정보와 무관" },
                  "withdrawal": null
                },
                "operatorChannelThreadId": 905,
                "openAdjustments": [ { "adjustmentId": 12, "settlementId": 1021, "settlementNumber": "STL-2609-016",
                                       "deadlineAt": "2026-10-20T23:59:59", "threadId": 930 } ],
                "withdrawalReview": { "requestId": 41, "requestedAt": "2026-10-14T10:20:00", "elapsedDays": 2, "dueDate": "2026-10-21",
                                      "reasonText": "활동 중단",
                                      "conditions": { "unfinishedGroupBuys": 0, "unfinishedContracts": 0, "unsettled": 1 } },
                "recentHistories": [
                  { "historyId": 77, "eventType": "SUSPENDED", "label": "정지", "tone": "DANGER",
                    "detail": "협찬 표기 누락 반복 — 공구 게시물 3건 협찬 표기 누락 — 2회 안내 후 반복", "actorType": "ADMIN",
                    "actorName": "김운영", "refType": "SANCTION", "refId": 5, "occurredAt": "2026-10-16T11:00:00" },
                  { "historyId": 74, "eventType": "WITHDRAWAL_REQUESTED", "label": "탈퇴 신청 접수", "tone": "WARNING",
                    "detail": "활동 중단", "actorType": "CREATOR", "actorName": "인플루언서 본인",
                    "refType": "WITHDRAWAL_REQUEST", "refId": 41, "occurredAt": "2026-10-14T10:20:00" }
                ],
                "historyTotal": 9
              }
            }
            """;

    @Operation(summary = "인플루언서 상세 — 상단 4카드 + 레일", description = """
            기본 정보 · 본인확인 정보(마스킹) · 사업자 정보 · 정산 정보 + 레일(현재 상태 · 이슈 스레드 · 탈퇴 신청 심사 · 처리 이력 최신 N건).
            하단 카드(운영 현황 · 최근 정산 · 계약 · 연결 브랜드 · 메모 · 이력 전체)는 각자 API 로 따로 부른다.

            **카드별 규칙**
            - **본인확인 정보(`identity`)** — 기본 마스킹(`김*연` · `1994.**.**` · `010-****-8842`). 요청한 운영자의 유효한 마스킹 해제 세션이 있으면
              실값 · 만 나이(`age`)까지 내려간다(`unmask` 에 처리자 · 만료 시각 · 남은 초 · 10분 고정 만료). 통신사 · 최근 갱신은 수집하지 않아 항상 null.
            - **사업자 정보(`business`)** — 비사업자면 등록번호 · 등록증 대신 `residentNumberRegistered`(값은 내리지 않음)와 「3.3% 원천징수」.
              상호 · 대표자 · 주소는 수집하지 않는다(등록증 이미지로 확인).
            - **정산 정보(`settlementAccount`)** — 계좌는 가운데 마스킹 · **해제 경로 없음**. `lastChange` 는 최근 계좌 변경(「변경 08.29 14:02 · 이전 신한 ****3301」).
              `pendingBusinessTypeChange` 가 있으면 「변경 요청 확인 중 ↗」 → `/v1/admin/change-requests/{changeRequestId}`.
            - 수집하지 않는 값은 null 이고 FE 는 그 행을 그리지 않는다.

            **레일**
            - `currentStatus.suspension` — 정지일 때만. `payoutHeld` 는 사유가 정한다(정산 정보 불일치만 true) · `payoutNote` 가 한 줄 설명.
            - `operatorChannelThreadId` — 운영팀 채널(소명 · 「스레드 열기 ↗」) · 채널이 없으면 null.
            - `openAdjustments` — 열린 정산 조정 협의(합의 기한 · 스레드).
            - `withdrawalReview` — 탈퇴 신청이 대기 중일 때만. `conditions` 는 **실시간** 건수이고 승인 API 가 다시 판정한다.
            - `recentHistories` 최신 N건 + `historyTotal`.

            **탈퇴 회원** — 레이아웃 그대로 `destroyed = true` 로 「— 파기됨」을 그린다. 쇼룸은 비공개(`showroomPublic=false` · `showroomUrl=null`).

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Detail.class),
                            examples = @ExampleObject(name = "정지 · 탈퇴 심사 중", value = DETAIL_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_NOT_FOUND", "message": "존재하지 않는 크리에이터입니다." }
                                    """)))
    })
    Detail detail(@Parameter(description = "creatorId(INF- 뒤 숫자)", required = true, example = "3018") Long creatorId,
                  @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "운영 현황 KPI 4 + 쇼룸 활동", description = """
            **운영 현황(`operations`)** — 연결 브랜드 · 판매 중 공구 · 정산 진행 중 리워드(세전 · 인플루언서 몫이 지급 완료 전인 정산의 합) · 조정 협의.
            경고색은 조정 협의(`openAdjustmentCount`)만.

            **쇼룸 활동(`activity`)** — 앱 내 기록만(외부 채널 수치 아님): 공구 게시물 · 검토 반려 횟수 · 숨김 · 노출 중지 횟수(사실 기록) · 일반 게시물 ·
            쇼룸 팔로워(앱 내 팔로우 수) · 소통 스레드(전체 · 열린 것).

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Overview.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "operations": { "connectedBrandCount": 4, "sellingGroupBuyCount": 1, "rewardInProgressAmount": 482000,
                                                      "openAdjustmentCount": 1 },
                                      "activity": { "groupBuyPostCount": 12, "reviewRejectCount": 2, "hiddenCount": 1, "generalPostCount": 30,
                                                    "followerCount": 1840, "threadCount": 9, "openThreadCount": 2 }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    Overview overview(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId);

    @Operation(summary = "최근 정산", description = """
            정산 관리와 같은 단계 배지(`status` · `statusLabel` · `tone`) · 인플루언서 몫 지급 상태(`creatorPayoutStatus`).
            조정 협의 행에는 `adjustment`(합의 기한 · 스레드)가 붙는다. 최신순 · 기본 5건(1~50) · `total` 은 전체 건수.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = SettlementList.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "total": 7,
                                      "content": [
                                        { "settlementId": 1021, "settlementNumber": "STL-2609-016", "groupBuyId": 501, "groupBuyTitle": "가을 수분 크림 공구",
                                          "marketName": "코코브라운", "status": "ADJUSTING", "statusLabel": "조정 협의", "tone": "WARNING",
                                          "rewardAmount": 99505, "creatorPayoutStatus": "WAITING", "creatorPayoutStatusLabel": "확인 기간 후 지급",
                                          "payoutDueDate": null, "paidAt": null,
                                          "adjustment": { "adjustmentId": 12, "settlementId": 1021, "settlementNumber": "STL-2609-016",
                                                          "deadlineAt": "2026-10-20T23:59:59", "threadId": 930 } },
                                        { "settlementId": 980, "settlementNumber": "STL-2608-041", "groupBuyId": 470, "groupBuyTitle": "선크림 공구",
                                          "marketName": "데일리코스", "status": "PAID", "statusLabel": "지급 완료", "tone": "SUCCESS",
                                          "rewardAmount": 210000, "creatorPayoutStatus": "PAID", "creatorPayoutStatusLabel": "지급 완료",
                                          "payoutDueDate": "2026-09-10", "paidAt": "2026-09-10T10:00:00", "adjustment": null }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    SettlementList settlements(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                               @Parameter(description = "건수(1~50 · 범위 밖은 보정)", example = "5") int size);

    @Operation(summary = "계약", description = """
            인플루언서에게 도착한 계약(서명 진행중 이후) · 최신순 페이지. 고정 지급비 · 지급 시점(`fixedFeeTrigger` — `POST_REGISTERED` 공구 게시물 등록 후 ·
            `GROUP_BUY_ENDED` 공구 종료 후 · `SETTLEMENT_COMPLETED` 정산 완료 후) · 지급 시점 도래 시각 · 브랜드 지급 기록(자기 신고).

            - `fixedFeeAmount = null` 이면 「없음」.
            - 「기록 없음 ∧ 시점 경과」의 경고색은 FE 판단 — 플랫폼은 고정 지급비를 보관 · 보증하지 않아 배지 · 조치 버튼이 없다.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "contractId": 220, "contractNumber": "CT-2609-0031", "marketName": "코코브라운", "title": "가을 수분 크림 공구 계약",
                                          "status": "CONCLUDED", "statusLabel": "체결완료", "tone": "SUCCESS",
                                          "fixedFeeAmount": 300000, "fixedFeeTrigger": "POST_REGISTERED", "fixedFeeTriggerLabel": "공구 게시물 등록 후",
                                          "fixedFeeTriggerReachedAt": "2026-09-05T12:00:00", "fixedFeePaidAt": null }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 1, "limit": 20, "hasNext": false }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    PageResponse<ContractRow> contracts(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                                        @Parameter(description = "페이지(1부터)", example = "1") int page,
                                        @Parameter(description = "페이지 크기(1~50)", example = "20") int size);

    @Operation(summary = "연결 브랜드", description = """
            연결중(`CONNECTED`) 먼저 · 연결 종료(`DISCONNECTED`) 아래. 요청중 · 거절은 내리지 않는다. 행 클릭 → 브랜드 상세(`GET /v1/admin/brands/{marketId}`).

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectionList.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "total": 2,
                                      "content": [
                                        { "connectionId": 88, "marketId": 42, "marketName": "코코브라운", "connectedAt": "2026-07-03T10:00:00",
                                          "sellingGroupBuyCount": 1, "status": "CONNECTED", "disconnectedAt": null },
                                        { "connectionId": 61, "marketId": 39, "marketName": "데일리코스", "connectedAt": "2026-07-10T10:00:00",
                                          "sellingGroupBuyCount": 0, "status": "DISCONNECTED", "disconnectedAt": "2026-09-01T09:00:00" }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ConnectionList connections(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId);

    @Operation(summary = "처리 이력", description = """
            최신순 · 저장 이력 + 파생 행(가입 신청 · 입점 승인 · 사업자 구분 변경 승인 · 반려 — `historyId = null`). 파기 대상 아님.

            `eventType` — `SUSPENDED` · `SUSPENSION_RELEASED` · `WITHDRAWAL_REQUESTED` · `WITHDRAWAL_CANCELED` · `WITHDRAWAL_APPROVED` · `WITHDRAWAL_REJECTED` ·
            `MEMO_CREATED` · `MEMO_UPDATED` · `MEMO_DELETED` · `PRIVACY_UNMASKED` · `SETTLEMENT_ACCOUNT_CHANGED` · `CONNECTIONS_ENDED` ·
            `BUSINESS_TYPE_CHANGE_APPROVED` · `BUSINESS_TYPE_CHANGE_REJECTED` · `SIGNED_UP` · `ONBOARDING_APPROVED`.
            `refType` — `SANCTION` · `WITHDRAWAL_REQUEST` · `MEMO` · `PRIVACY_ACCESS` · `SETTLEMENT_ACCOUNT` · `CHANGE_REQUEST`.
            `actorType` — `ADMIN`(운영자) · `CREATOR`(인플루언서 본인) · `SYSTEM`.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = HistoryPage.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "total": 3, "page": 1, "size": 20,
                                      "content": [
                                        { "historyId": 79, "eventType": "PRIVACY_UNMASKED", "label": "본인확인 정보 마스킹 해제", "tone": "WARNING",
                                          "detail": "정산·원천징수 확인", "actorType": "ADMIN", "actorName": "김운영",
                                          "refType": "PRIVACY_ACCESS", "refId": 14, "occurredAt": "2026-10-16T15:00:00" },
                                        { "historyId": 77, "eventType": "SUSPENDED", "label": "정지", "tone": "DANGER",
                                          "detail": "협찬 표기 누락 반복 — 공구 게시물 3건 협찬 표기 누락", "actorType": "ADMIN", "actorName": "김운영",
                                          "refType": "SANCTION", "refId": 5, "occurredAt": "2026-10-16T11:00:00" },
                                        { "historyId": null, "eventType": "ONBOARDING_APPROVED", "label": "입점 승인", "tone": "SUCCESS",
                                          "detail": null, "actorType": "ADMIN", "actorName": "운영자",
                                          "refType": null, "refId": null, "occurredAt": "2026-07-01T14:00:00" }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    HistoryPage histories(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                          @Parameter(description = "페이지(1부터)", example = "1") int page,
                          @Parameter(description = "페이지 크기(1~100)", example = "20") int size);

    @Operation(summary = "관리자 메모 목록", description = """
            최신순 · 인플루언서 비노출. `editable` = 요청 운영자가 작성자 본인 ∧ 작성 24시간 이내. `modifiedAt` 이 있으면 「수정됨」.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemoPage.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "total": 1,
                                      "content": [
                                        { "memoId": 6, "authorName": "김운영",
                                          "content": "벨라코스 고정 지급비 미지급 문의 — 계약서상 지급 시점을 근거로 브랜드 확인 안내",
                                          "createdAt": "2026-10-12T10:00:00", "modifiedAt": null, "editable": true }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    MemoPage memos(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                   @Parameter(description = "페이지(1부터)", example = "1") int page,
                   @Parameter(description = "페이지 크기", example = "20") int size,
                   @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 기입", description = """
            덮어쓰기가 아니라 **누적 기입** · 공백 제거 후 1 ~ 300자(서버 검증) · 인플루언서 비노출 · 처리 이력에 「관리자 메모 기입」이 남는다.
            탈퇴 회원은 조회 전용(409 `CREATOR_WITHDRAWN`).

            **권한:** ADMIN
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemoRequest.class),
                    examples = @ExampleObject(value = """
                            { "content": "벨라코스 고정 지급비 미지급 문의 — 계약서상 지급 시점을 근거로 브랜드 확인 안내" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기입 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Memo.class),
                            examples = @ExampleObject(value = """
                                    { "memoId": 6, "authorName": "김운영",
                                      "content": "벨라코스 고정 지급비 미지급 문의 — 계약서상 지급 시점을 근거로 브랜드 확인 안내",
                                      "createdAt": "2026-10-12T10:00:00", "modifiedAt": null, "editable": true }
                                    """))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과 — INVALID_INPUT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴 회원 — CREATOR_WITHDRAWN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWN", "message": "탈퇴한 인플루언서입니다. 조치할 수 없습니다." }
                                    """)))
    })
    Memo createMemo(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                    MemoRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 수정", description = """
            작성자 본인 ∧ 작성 24시간 이내만. 저장하면 `modifiedAt` 이 채워지고 처리 이력에 「관리자 메모 수정」이 남는다.

            **권한:** ADMIN
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MemoRequest.class),
                    examples = @ExampleObject(value = """
                            { "content": "벨라코스 고정 지급비 — 브랜드 지급 기록 확인(10.14), 종결" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Memo.class))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성자 아님 · 24시간 경과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "작성자 아님", value = """
                                            { "code": "CREATOR_MEMO_NOT_AUTHOR", "message": "작성자만 수정·삭제할 수 있습니다." }
                                            """),
                                    @ExampleObject(name = "24시간 경과", value = """
                                            { "code": "CREATOR_MEMO_EDIT_WINDOW_CLOSED", "message": "작성 후 24시간이 지난 메모는 수정·삭제할 수 없습니다." }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "메모 없음 · 다른 인플루언서의 메모 — CREATOR_MEMO_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_MEMO_NOT_FOUND", "message": "존재하지 않는 메모입니다." }
                                    """)))
    })
    Memo updateMemo(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                    @Parameter(description = "메모 ID", required = true, example = "6") Long memoId,
                    MemoRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 삭제", description = """
            수정과 같은 조건(작성자 본인 ∧ 24시간 이내) · soft delete — 목록에서 빠지고 처리 이력에 「관리자 메모 삭제」가 남는다.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "작성자 아님(CREATOR_MEMO_NOT_AUTHOR) · 24시간 경과(CREATOR_MEMO_EDIT_WINDOW_CLOSED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CREATOR_MEMO_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteMemo(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                                    @Parameter(description = "메모 ID", required = true, example = "6") Long memoId,
                                    @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "본인확인 정보 마스킹 해제(M4)", description = """
            본인확인 정보(실명 · 생년월일 · 휴대폰)를 실값으로 연다. **계좌번호는 대상이 아니다**(해제 경로 없음).

            | `reason` | 라벨 |
            | --- | --- |
            | `CS_RESPONSE` | CS 응대 |
            | `SETTLEMENT_WITHHOLDING_CHECK` | 정산·원천징수 확인 |
            | `CONTRACT_DISPUTE` | 계약·분쟁 대응 |
            | `INVESTIGATION_COOPERATION` | 수사기관 협조 |

            - 정형 사유 4종 필수 · 상세 사유 · 기타 없음. 직전 사유가 `unmask.lastReason` 으로 프리필된다.
            - **이 인플루언서 1건 · 이 운영자 · 10분 고정 만료**(연장 없음). 다른 운영자 화면은 그대로 마스킹이다.
            - 열람 사실은 처리 이력에 즉시 기록(「본인확인 정보 마스킹 해제」 · 사유).
            - 응답은 실값이 든 상세(`identity.unmasked=true` · `unmask.expiresAt` · `remainingSeconds`).

            **권한:** ADMIN 중 최고관리자 한정(그 외 403)
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UnmaskRequest.class),
                    examples = @ExampleObject(value = """
                            { "reason": "SETTLEMENT_WITHHOLDING_CHECK" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 — 실값이 든 상세",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Detail.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "creatorId": 3018, "memberNo": "INF-3018",
                                      "identity": { "destroyed": false, "unmasked": true, "name": "김소연", "birthday": "1994-03-21", "age": 32,
                                                    "phone": "010-2231-8842", "carrier": null, "firstVerifiedAt": "2026-06-20T10:02:00", "renewedAt": null },
                                      "unmask": { "allowed": true, "unmasked": true, "operatorName": "김운영", "unmaskedAt": "2026-10-16T15:00:00",
                                                  "expiresAt": "2026-10-16T15:10:00", "remainingSeconds": 600, "lastReason": "SETTLEMENT_WITHHOLDING_CHECK" },
                                      "…": "나머지 필드는 상세와 같음"
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "사유 누락 · 모르는 사유 — INVALID_INPUT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "최고관리자 아님 — CREATOR_PRIVACY_UNMASK_FORBIDDEN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_PRIVACY_UNMASK_FORBIDDEN", "message": "마스킹 해제 권한이 없습니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴로 파기된 회원 — CREATOR_PRIVACY_UNMASK_NOT_AVAILABLE",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_PRIVACY_UNMASK_NOT_AVAILABLE", "message": "파기된 정보는 해제할 수 없습니다." }
                                    """)))
    })
    Detail unmask(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                  UnmaskRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "지금 잠금", description = """
            이 운영자의 유효 해제 세션을 즉시 닫는다(「지금 잠금」 버튼). 화면을 떠날 때도 부른다. 세션이 없어도 204 — 멱등.
            잠근 뒤 상세를 다시 부르면 마스킹 값이 내려간다.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "잠금 완료"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> lock(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                              @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "정지(M1)", description = """
            로그인은 유지되고 **새로 벌이는 일만** 막힌다 — 새 게시물 게시 · 계약 서명(브랜드의 서명 요청 발송도 409 `CREATOR_COUNTERPARTY_SUSPENDED`).
            진행 중 공구 운영 · 소통 스레드 · 정산 조회는 그대로다. 막힌 행위는 409 `CREATOR_SUSPENDED`.

            | `reason` | 라벨 | 리워드 지급 정지 |
            | --- | --- | --- |
            | `AD_DISCLOSURE_OMITTED_REPEATED` | 협찬 표기 누락 반복 | — |
            | `AD_LAW_VIOLATION_REPEATED` | 표시광고법 위반 문구 반복 | — |
            | `GROUP_BUY_NONPERFORMANCE` | 공구 진행 불이행 | — |
            | `CONSUMER_RESPONSE_NEGLECT` | 소비자 응대 불이행 | — |
            | `SETTLEMENT_INFO_MISMATCH` | 정산 정보 불일치 | ✅ |
            | `OTHER` | 기타(직접 입력) — `reasonDetail` 필수 | — |

            - **지급 정지는 사유가 정한다** — 정산 정보 불일치만 리워드 지급을 멈춘다(지급 예정 행 → 보류). 브랜드 · 플랫폼 몫은 그대로.
            - 운영팀 채널에 「계정 이용 정지」 카드가 자동 등록된다(소명 경로). 정지 종료일 `endsOn` 은 선택 입력이고, 넣으면 종료일 다음 날 00:00 에 시스템이 `TERM_ENDED` 로 자동 해제한다(1021 기-02) — 비우면 수동 해제다.
            - 탈퇴 심사 중에도 정지할 수 있다. 응답은 갱신된 상세.

            **권한:** ADMIN
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SuspendRequest.class),
                    examples = {
                            @ExampleObject(name = "정형 사유", value = """
                                    { "reason": "AD_DISCLOSURE_OMITTED_REPEATED", "reasonDetail": "공구 게시물 3건 협찬 표기 누락 — 2회 안내 후 반복" }
                                    """),
                            @ExampleObject(name = "기타(상세 필수)", value = """
                                    { "reason": "OTHER", "reasonDetail": "타인 명의 계좌 사용 정황 — 본인 확인 전까지 정지" }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정지 완료 — 갱신된 상세",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Detail.class),
                            examples = @ExampleObject(value = DETAIL_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "기타 사유 상세 누락 · 사유 누락 · 상세 500자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_SUSPEND_DETAIL_REQUIRED", "message": "기타 사유를 선택하면 상세 사유를 입력해야 합니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 정지 · 탈퇴 회원 · 상태가 이미 바뀜",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "이미 정지", value = """
                                            { "code": "CREATOR_ALREADY_SUSPENDED", "message": "이미 정지된 인플루언서입니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 회원", value = """
                                            { "code": "CREATOR_WITHDRAWN", "message": "탈퇴한 인플루언서입니다. 조치할 수 없습니다." }
                                            """),
                                    @ExampleObject(name = "상태 변경됨", value = """
                                            { "code": "CREATOR_STATE_CHANGED", "message": "인플루언서 상태가 이미 변경되었습니다. 새로고침 후 다시 확인해 주세요." }
                                            """)
                            }))
    })
    Detail suspend(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                   SuspendRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "정지 해제", description = """
            해제는 회원 상세에서만 한다. 즉시 모든 기능이 다시 열린다.

            - `reason` 은 선택 — 비면 `RESOLVED`(소명 · 시정 확인). 운영자가 고를 수 있는 값은 `RESOLVED` · `MISTAKE`(오정지)뿐이고
              `WITHDRAWN`(탈퇴 승인으로 종결)은 시스템 전용(400). `note` 는 해제 메모(선택 · 500자 이내). 바디 생략 가능.
            - 지급 정지였으면 보류 행을 다시 판정해 다음 영업일 지급 예정으로 올린다(계좌 확인 중 등 다른 보류 사유가 남으면 보류 유지).
            - 운영팀 채널에 「정지 해제」 카드 자동 등록. 정지 이력과 사유는 보존된다.

            **권한:** ADMIN
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReleaseRequest.class),
                    examples = {
                            @ExampleObject(name = "소명 확인", value = """
                                    { "reason": "RESOLVED", "note": "협찬 표기 수정 완료 확인" }
                                    """),
                            @ExampleObject(name = "바디 생략(= RESOLVED)", value = "{}")
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 완료 — 갱신된 상세(`status=ACTIVE` · `suspension=null`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Detail.class))),
            @ApiResponse(responseCode = "400", description = "운영자가 고를 수 없는 사유(`WITHDRAWN`) · note 500자 초과 — INVALID_INPUT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "INVALID_INPUT", "message": "탈퇴 종결은 운영자가 고를 수 있는 해제 사유가 아닙니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "정지 아님 · 탈퇴 회원",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "정지 아님", value = """
                                            { "code": "CREATOR_NOT_SUSPENDED", "message": "정지 상태가 아닌 인플루언서입니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 회원", value = """
                                            { "code": "CREATOR_WITHDRAWN", "message": "탈퇴한 인플루언서입니다. 조치할 수 없습니다." }
                                            """)
                            }))
    })
    Detail release(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                   ReleaseRequest request, @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "탈퇴 승인(M2)", description = """
            **되돌릴 수 없다.** 바디 없음.

            조건(끝나지 않은 공구 · 계약 · 미정산)을 다시 판정해 하나라도 남으면 409 `CREATOR_WITHDRAWAL_CONDITION_UNMET`(응답 최상위 `unmet` 에 조건별 건수
            — `UNFINISHED_GROUP_BUYS` · `UNFINISHED_CONTRACTS` · `UNSETTLED`) — 이때는 반려로 처리한다.

            **승인되면** 쇼룸 비공개 · 브랜드 연결 일괄 종료 · 본인확인 · 정산 정보 즉시 파기 · 스튜디오 자격 해지(소비자 계정은 유지).
            열린 정지는 탈퇴 종결로 닫힌다. 주민등록번호 · 사업자등록번호는 세무 보존(파기 시점은 법률 자문 대기).

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료 — 갱신된 상세(`status=WITHDRAWN` · `destroyed=true` · 「— 파기됨」)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Detail.class))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 인플루언서의 신청 — CREATOR_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWAL_NOT_FOUND", "message": "존재하지 않는 탈퇴 신청입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "조건 미충족 · 대기 아님(취소 · 처리됨) · 탈퇴 회원",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "조건 미충족", value = """
                                            { "code": "CREATOR_WITHDRAWAL_CONDITION_UNMET",
                                              "message": "탈퇴 조건이 충족되지 않았습니다 — 끝나지 않은 공구 0건 · 끝나지 않은 계약 0건 · 미정산 1건",
                                              "unmet": { "UNSETTLED": 1 } }
                                            """),
                                    @ExampleObject(name = "대기 아님", value = """
                                            { "code": "CREATOR_WITHDRAWAL_NOT_PENDING", "message": "이미 처리된 탈퇴 신청입니다." }
                                            """)
                            }))
    })
    Detail approveWithdrawal(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                             @Parameter(description = "탈퇴 신청 ID — 상세 `rail.withdrawalReview.requestId`", required = true, example = "41")
                             Long requestId,
                             @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "탈퇴 반려(M3)", description = """
            사유는 조건과 같은 축 · 기타만 안내(`note` · 500자 이내) 필수. 회원 상태는 바뀌지 않는다(신청만 종결).

            | `reason` | 라벨 |
            | --- | --- |
            | `GROUP_BUY_REMAINING` | 끝나지 않은 공구 남음 |
            | `UNFINISHED_CONTRACTS` | 끝나지 않은 계약 남음 |
            | `SETTLEMENT_IN_PROGRESS` | 미정산 남음 |
            | `DISPUTE_IN_PROGRESS` | 분쟁·신고 처리 중 |
            | `OTHER` | 기타(직접 입력) |

            사유 · 안내는 스튜디오 계정 탭 S8 반려 안내(`withdrawal.rejection`)에 실린다. 인플루언서는 바로 다시 신청할 수 있다.

            **권한:** ADMIN
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = RejectWithdrawalRequest.class),
                    examples = {
                            @ExampleObject(name = "정형 사유", value = """
                                    { "reason": "SETTLEMENT_IN_PROGRESS", "note": "9월 정산 지급이 끝난 뒤 다시 신청해 주세요." }
                                    """),
                            @ExampleObject(name = "기타(안내 필수)", value = """
                                    { "reason": "OTHER", "note": "브랜드와의 고정 지급비 분쟁이 정리된 뒤 다시 신청해 주세요." }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료 — 갱신된 상세(`withdrawalReview=null`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Detail.class))),
            @ApiResponse(responseCode = "400", description = "기타 사유 안내 누락 · 사유 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "CREATOR_WITHDRAWAL_REJECT_DETAIL_REQUIRED",
                                      "message": "기타 사유를 선택하면 인플루언서에게 전달할 안내를 입력해야 합니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "CREATOR_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "대기 아님 — CREATOR_WITHDRAWAL_NOT_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    Detail rejectWithdrawal(@Parameter(description = "creatorId", required = true, example = "3018") Long creatorId,
                            @Parameter(description = "탈퇴 신청 ID", required = true, example = "41") Long requestId,
                            RejectWithdrawalRequest request,
                            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "모달 셀렉트 사유 목록", description = """
            정지(M1 · 지급 정지 여부 `holdsPayout` 포함) · 정지 해제 · 탈퇴 반려(M3) · 마스킹 해제(M4) 사유의 코드와 라벨 — enum 단일 소스라
            FE 가 목록을 하드코딩하지 않는다. `detailRequired=true`(기타)를 고르면 상세 · 안내 입력이 필수가 된다. 브랜드 상세의 같은 API 와 같은 모양.

            `suspensionTerm` — 정지 모달 미리보기(1021 7-2): 「이의 신청 기한 · 통지 후 `appealBusinessDays`영업일 이상 · `appealDeadlineAt`까지」
            (응답 시각 기준 · 공휴일 반영) · 「소명 검토 · 접수 후 `appealReviewBusinessDays`영업일」(미정이면 null → 「[정책값 미정]」).
            저장 값은 정지 응답의 `term.appealDeadlineAt` 이다.

            **권한:** ADMIN
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReasonOptions.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "suspensionReasons": [
                                        { "code": "AD_DISCLOSURE_OMITTED_REPEATED", "label": "협찬 표기 누락 반복", "detailRequired": false, "holdsPayout": false },
                                        { "code": "AD_LAW_VIOLATION_REPEATED", "label": "표시광고법 위반 문구 반복", "detailRequired": false, "holdsPayout": false },
                                        { "code": "GROUP_BUY_NONPERFORMANCE", "label": "공구 진행 불이행", "detailRequired": false, "holdsPayout": false },
                                        { "code": "CONSUMER_RESPONSE_NEGLECT", "label": "소비자 응대 불이행", "detailRequired": false, "holdsPayout": false },
                                        { "code": "SETTLEMENT_INFO_MISMATCH", "label": "정산 정보 불일치", "detailRequired": false, "holdsPayout": true },
                                        { "code": "OTHER", "label": "기타(직접 입력)", "detailRequired": true, "holdsPayout": false }
                                      ],
                                      "releaseReasons": [
                                        { "code": "RESOLVED", "label": "소명 · 시정 확인", "detailRequired": false, "holdsPayout": null },
                                        { "code": "MISTAKE", "label": "오정지", "detailRequired": false, "holdsPayout": null }
                                      ],
                                      "withdrawalRejectReasons": [
                                        { "code": "GROUP_BUY_REMAINING", "label": "끝나지 않은 공구 남음", "detailRequired": false, "holdsPayout": null },
                                        { "code": "UNFINISHED_CONTRACTS", "label": "끝나지 않은 계약 남음", "detailRequired": false, "holdsPayout": null },
                                        { "code": "SETTLEMENT_IN_PROGRESS", "label": "미정산 남음", "detailRequired": false, "holdsPayout": null },
                                        { "code": "DISPUTE_IN_PROGRESS", "label": "분쟁·신고 처리 중", "detailRequired": false, "holdsPayout": null },
                                        { "code": "OTHER", "label": "기타(직접 입력)", "detailRequired": true, "holdsPayout": null }
                                      ],
                                      "privacyUnmaskReasons": [
                                        { "code": "CS_RESPONSE", "label": "CS 응대", "detailRequired": false, "holdsPayout": null },
                                        { "code": "SETTLEMENT_WITHHOLDING_CHECK", "label": "정산·원천징수 확인", "detailRequired": false, "holdsPayout": null },
                                        { "code": "CONTRACT_DISPUTE", "label": "계약·분쟁 대응", "detailRequired": false, "holdsPayout": null },
                                        { "code": "INVESTIGATION_COOPERATION", "label": "수사기관 협조", "detailRequired": false, "holdsPayout": null }
                                      ],
                                      "suspensionTerm": { "appealBusinessDays": 3, "appealDeadlineAt": "2026-10-22T23:59:59", "appealReviewBusinessDays": null }
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ReasonOptions reasonOptions();
}
