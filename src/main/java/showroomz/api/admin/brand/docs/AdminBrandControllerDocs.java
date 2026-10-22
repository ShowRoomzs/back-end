package showroomz.api.admin.brand.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.brand.dto.AdminBrandDto;
import showroomz.api.admin.brand.type.AdminBrandSort;
import showroomz.api.admin.brand.type.AdminBrandTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Admin - Brand Member", description = "어드민 회원 관리 · 브랜드 — 상세 · 정지 · 탈퇴 심사 · 메모 · 이력(ui-admin-15 · 52 설계서)")
public interface AdminBrandControllerDocs {

    /** 정지 · 해제 · 탈퇴 승인 · 반려가 돌려주는 갱신된 레일 — 상세의 rail 과 같은 모양. */
    String RAIL_EXAMPLE = """
            {
              "currentStatus": {
                "marketId": 42, "memberStatus": "SUSPENDED", "label": "정지", "tone": "DANGER",
                "joinedAt": "2026-03-02T10:00:00", "approvedAt": "2026-03-05T14:00:00", "lastLoginAt": "2026-10-15T09:12:00",
                "sanction": {
                  "sanctionId": 7, "reason": "SETTLEMENT_INFO_MISMATCH", "reasonLabel": "정산 정보 불일치",
                  "reasonDetail": "통장 사본 예금주가 사업자 상호와 다름 — 9월 정산 지급 전 확인 필요",
                  "payoutHeld": true, "suspendedAt": "2026-10-16T11:00:00", "suspendedByName": "김운영", "threadId": 812
                },
                "withdrawal": null,
                "actions": { "canSuspend": false, "canRelease": true, "canReviewWithdrawal": false }
              },
              "pendingChips": [
                { "key": "SHIP_OVERDUE", "label": "발송 기한 경과", "tone": "WARNING", "count": 2, "target": "ORDERS", "threadId": null },
                { "key": "OPERATOR_THREAD_UNREAD", "label": "운영팀 스레드 미확인", "tone": "WARNING", "count": 1, "target": "THREAD", "threadId": 812 }
              ],
              "withdrawalReview": null,
              "histories": {
                "items": [
                  { "type": "SUSPENDED", "label": "정지", "tone": "DANGER", "actorType": "ADMIN", "actorName": "김운영",
                    "detail": "정산 정보 불일치 — 통장 사본 예금주가 사업자 상호와 다름", "refType": "BRAND_SANCTION", "refId": 7,
                    "occurredAt": "2026-10-16T11:00:00" }
                ],
                "total": 6
              }
            }
            """;

    @Operation(summary = "브랜드 회원 목록",
            description = """
                    입점이 끝난 브랜드(셀러 승인 완료)만 나옵니다. 목록 시안(14)이 아직 없어 상세의 「목록 · 이전 · 다음」을 성립시키는 최소 구성입니다.

                    - `tab`: `ALL`(기본) · `ACTIVE`(휴면 포함) · `SUSPENDED` · `WITHDRAWN`
                    - `keyword`: `BRD-`로 시작하면 회원번호 정확 일치(접두사 뒤가 숫자가 아니면 0건) · 그 외 브랜드명 부분 일치
                    - `sort`: `RECENT_JOINED`(기본 · 가입 최신순) · `BRAND_NO`(회원번호순)
                    - 탈퇴 승인 대기는 **회원 상태가 아니라** `withdrawalPending` 플래그입니다(승인 전까지 브랜드는 활성).
                    - `summary` 의 상태별 건수는 탭과 무관하게 검색어를 반영한 값이고, `summary.withdrawalPending` 은 검색 조건과도 무관한 전체 대기 건수입니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminBrandDto.ListResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "marketId": 42, "brandNo": "BRD-1042", "brandName": "코코브라운", "managerName": "김담당",
                                          "businessType": "법인사업자", "memberStatus": "ACTIVE", "memberStatusLabel": "활성",
                                          "withdrawalPending": true, "joinedAt": "2026-03-02T10:00:00", "lastLoginAt": "2026-10-15T09:12:00" },
                                        { "marketId": 39, "brandNo": "BRD-1039", "brandName": "데일리코스", "managerName": "이담당",
                                          "businessType": "개인사업자", "memberStatus": "SUSPENDED", "memberStatusLabel": "정지",
                                          "withdrawalPending": false, "joinedAt": "2026-02-11T15:20:00", "lastLoginAt": "2026-10-01T18:40:00" }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false },
                                      "summary": { "total": 48, "active": 44, "suspended": 2, "withdrawn": 2, "withdrawalPending": 1 }
                                    }
                                    """)))
    })
    AdminBrandDto.ListResponse list(@Parameter(description = "탭 — ALL(기본) · ACTIVE(휴면 포함) · SUSPENDED · WITHDRAWN", example = "ALL")
                                    AdminBrandTab tab,
                                    @Parameter(description = "`BRD-1042`(회원번호 정확 일치) 또는 브랜드명(부분 일치)", example = "코코")
                                    String keyword,
                                    @Parameter(description = "정렬 — RECENT_JOINED(기본) · BRAND_NO", example = "RECENT_JOINED")
                                    AdminBrandSort sort,
                                    @ParameterObject PagingRequest paging);

    @Operation(summary = "브랜드 회원 상세",
            description = """
                    좌측 사실 조회 7카드(기본 · 사업자 · 정산 · 배송반품 · 운영 현황 · 연결 인플루언서 · 관리자 메모)와 우측 레일(현재 상태 · 진행 중 칩 · 탈퇴 신청 심사 · 처리 이력)을 한 응답으로 내립니다.

                    **좌측 카드**
                    - **계좌번호는 가운데 마스킹**(`110-****-3841`)이고 원본은 응답에 없습니다 — 전체 값은 정산 관리에서 조회합니다.
                    - `settlementInfo.pendingChangeRequests` — 검토 대기 변경 요청(카드 머리 「변경 요청 대기 N건」 → `/v1/admin/change-requests/{requestId}`).
                    - `deliveryPolicy.shipLeadLastChange` — 발송 기한 최근 변경(「08.20 2→3 변경」) · 없으면 null.
                    - `operations.unsettledAmount` 가 0 이 아니면 경고색, `payoutHeld=true` 면 「지급 정지 중」을 붙입니다.
                    - 업태 · 종목 · 배송 방법 · 택배사 연동은 내리지 않습니다(§52-1 · 수집하지 않거나 브랜드 단위 값이 없음).
                    - 「최근 문의」 카드는 없습니다 — 파트너센터 1:1 문의가 정의되지 않았습니다(§55 A-5). 대신 `operatorThread`(운영팀 소통 스레드)를 내립니다.

                    **우측 레일**
                    - `rail.currentStatus.actions` — `canSuspend`(활성일 때) · `canRelease`(정지일 때) · `canReviewWithdrawal`(대기 신청이 있을 때). 탈퇴 계정은 모두 false.
                    - `rail.pendingChips` 는 1건 이상인 칩만 — `SHIP_OVERDUE` · `CANCEL_RESPONSE_PENDING` · `INSPECTION_PENDING` · `RESHIP_PENDING` ·
                      `PRODUCT_INQUIRY_UNANSWERED` · `OPERATOR_THREAD_UNREAD` · `SETTLEMENT_ADJUSTING` · `BRAND_TAX_INVOICE_PENDING`. `target` 이 이동할 화면이다.
                    - `rail.withdrawalReview` — 탈퇴 신청이 대기 중일 때만(신청일 · 경과일 · 신청자 · 사유 · 남기실 말씀).
                    - `rail.histories` 는 최신 3건(탈퇴 심사 중이면 2건) · `memos` 는 최신 1건 + 전체 건수입니다.

                    **탈퇴 계정** — `purged=true` 이고 파기된 값은 `null` 입니다(「— 파기됨」). 배송 · 반품 정책 · 브랜드명 · 가입일 · 입점 승인일은 보존됩니다.

                    `navigation` 은 회원번호 순 이전 · 다음 브랜드(없으면 null)입니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminBrandDto.Detail.class),
                            examples = @ExampleObject(name = "활성 · 탈퇴 심사 중", value = """
                                    {
                                      "marketId": 42, "brandNo": "BRD-1042", "brandName": "코코브라운",
                                      "memberStatus": "ACTIVE", "memberStatusLabel": "활성", "withdrawalPending": true, "purged": false,
                                      "navigation": { "previousMarketId": 41, "nextMarketId": 43 },
                                      "basicInfo": { "managerName": "김담당", "managerContact": "010-1234-5678", "email": "brand@example.com",
                                                     "siteUrl": "https://cocobrown.example", "joinedAt": "2026-03-02T10:00:00", "approvedAt": "2026-03-05T14:00:00" },
                                      "businessInfo": { "businessType": "법인사업자", "companyName": "(주)코코브라운", "representativeName": "김대표",
                                                        "businessRegistrationNumber": "123-45-67890", "mailOrderRegNumber": "2024-서울서초-0001",
                                                        "businessAddress": "서울시 서초구 …" },
                                      "settlementInfo": { "bankName": "신한은행", "accountHolder": "(주)코코브라운", "maskedAccountNumber": "110-****-3841",
                                                          "taxEmail": "tax@cocobrown.example", "pendingChangeRequests": [] },
                                      "deliveryPolicy": { "defaultDeliveryFee": 3000, "freeShippingThreshold": 50000, "jejuSurcharge": 5000,
                                                          "remoteAreaSurcharge": 4000,
                                                          "shipLeadBusinessDays": 3, "shipLeadText": "공구 마감 후 3영업일 이내 발송 (주말·공휴일 제외)",
                                                          "shipLeadLastChange": { "changedAt": "2026-08-20T10:00:00", "previousDays": 2, "newDays": 3 },
                                                          "returnAddress": { "recipientName": "코코브라운 물류", "contact": "02-123-4567",
                                                                             "address": "경기도 …", "detailAddress": "B동 2층" } },
                                      "operations": { "connectedCreatorCount": 3, "activeGroupBuyCount": 0, "settledAmountTotal": 12450000,
                                                      "unsettledAmount": 0, "payoutHeld": false,
                                                      "contracts": { "signing": 0, "concluded": 5 }, "products": { "selling": 12, "soldOut": 2 } },
                                      "connectedCreators": { "total": 3, "items": [
                                        { "creatorId": 3018, "showroomName": "소연 쇼룸", "connectedAt": "2026-04-01T10:00:00",
                                          "disconnectedAt": null, "activeGroupBuyCount": 0, "status": "CONNECTED", "statusLabel": "연결중" } ] },
                                      "operatorThread": { "threadId": 812, "unreadByOperator": 0, "lastMessageAt": "2026-10-10T16:00:00" },
                                      "rail": {
                                        "currentStatus": { "marketId": 42, "memberStatus": "ACTIVE", "label": "활성", "tone": "SUCCESS",
                                                           "joinedAt": "2026-03-02T10:00:00", "approvedAt": "2026-03-05T14:00:00",
                                                           "lastLoginAt": "2026-10-15T09:12:00", "sanction": null, "withdrawal": null,
                                                           "actions": { "canSuspend": true, "canRelease": false, "canReviewWithdrawal": true } },
                                        "pendingChips": [],
                                        "withdrawalReview": { "requestId": 31, "requestedAt": "2026-10-14T14:20:00", "elapsedDays": 2,
                                                              "requesterName": "김담당", "reason": "BUSINESS_CLOSED", "reasonLabel": "사업 종료",
                                                              "message": "그동안 감사했습니다." },
                                        "histories": { "items": [
                                          { "type": "WITHDRAWAL_REQUESTED", "label": "탈퇴 신청 접수", "tone": "WARNING", "actorType": "BRAND",
                                            "actorName": "김담당", "detail": "사유: 사업 종료", "refType": "BRAND_WITHDRAWAL_REQUEST", "refId": 31,
                                            "occurredAt": "2026-10-14T14:20:00" } ], "total": 5 }
                                      },
                                      "memos": { "items": [
                                        { "memoId": 9, "authorName": "김운영", "content": "탈퇴 신청 — 9월 정산 지급 완료 확인 후 승인 예정",
                                          "createdAt": "2026-10-15T10:00:00", "modifiedAt": null, "editable": true } ], "total": 2 }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND — 없거나 입점이 끝나지 않은 브랜드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "MARKET_NOT_FOUND", "message": "존재하지 않는 마켓입니다." }
                                    """)))
    })
    AdminBrandDto.Detail detail(@Parameter(description = "마켓 ID(BRD- 뒤 숫자)", required = true, example = "42") Long marketId,
                                @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "브랜드 정지(M1)",
            description = """
                    로그인은 허용하고 **새로 벌이는 일만** 막습니다 — 상품 등록 · 새 계약 작성 · 검토 요청 · 인플루언서 연결 요청 · 오픈 전 공구의 오픈. 진행 중 공구의 판매 · 주문 처리 · 상품 문의 답변 · 기본정보(계좌 포함) 수정은 그대로입니다.
                    막힌 행위는 409 `BRAND_SUSPENDED`(「계정 이용 정지 중에는 …」)로 응답합니다.

                    **사유 6종** — `holdsPayout` 은 `GET /v1/admin/brands/reason-options` 의 값과 같습니다.

                    | `reason` | 라벨 | 브랜드 몫 지급 정지 |
                    | --- | --- | --- |
                    | `SETTLEMENT_INFO_MISMATCH` | 정산 정보 불일치 | ✅ |
                    | `BUSINESS_INFO_FALSIFIED` | 사업자 정보 허위 기재 | ✅ |
                    | `PRODUCT_INFO_FALSIFIED` | 상품 정보 허위·과장 | — |
                    | `REPEATED_SHIPPING_DELAY` | 배송 지연 반복 | — |
                    | `CONSUMER_RESPONSE_NEGLECT` | 소비자 응대 불이행 | — |
                    | `OTHER` | 기타(직접 입력) — `reasonDetail` 필수 | — |

                    - **지급 정지는 사유가 정합니다**(운영자 스위치 없음) — 지급 예정 브랜드 몫이 보류(`BRAND_SUSPENDED`)되고, 해제 다음 영업일에 지급됩니다. 인플루언서 · 플랫폼 몫은 예정대로입니다.
                    - 운영팀 소통 스레드에 「계정 이용 정지」 시스템 카드가 자동으로 붙습니다(소명 경로).
                    - 탈퇴 심사 중에도 정지할 수 있습니다. 정지 기간은 없습니다(수동 해제).
                    - 응답은 갱신된 레일입니다.

                    **권한:** ADMIN
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminBrandDto.SuspendRequest.class),
                    examples = {
                            @ExampleObject(name = "지급 정지 사유", value = """
                                    { "reason": "SETTLEMENT_INFO_MISMATCH", "reasonDetail": "통장 사본 예금주가 사업자 상호와 다름 — 9월 정산 지급 전 확인 필요" }
                                    """),
                            @ExampleObject(name = "기타(상세 필수)", value = """
                                    { "reason": "OTHER", "reasonDetail": "소비자 개인정보를 외부 채널로 수집한 정황 — 소명 전까지 정지" }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "정지 완료 — 갱신된 레일",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.Rail.class),
                            examples = @ExampleObject(value = RAIL_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "기타 사유 상세 누락 · 사유 누락 · 상세 500자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BRAND_SUSPEND_DETAIL_REQUIRED", "message": "기타 사유를 선택하면 상세 사유를 입력해야 합니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 정지 · 탈퇴 브랜드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "이미 정지", value = """
                                            { "code": "BRAND_ALREADY_SUSPENDED", "message": "이미 정지된 브랜드입니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 브랜드", value = """
                                            { "code": "BRAND_WITHDRAWN", "message": "탈퇴한 브랜드입니다." }
                                            """)
                            }))
    })
    AdminBrandDto.Rail suspend(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                               AdminBrandDto.SuspendRequest request,
                               @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "브랜드 정지 해제",
            description = """
                    해제는 이 화면에서만 합니다(운영팀 스레드에 해제 버튼 없음). 즉시 모든 기능이 다시 열립니다.

                    - `reason` 은 선택 — 비면 `RESOLVED`(소명 · 시정 확인). 운영자가 고를 수 있는 값은 `RESOLVED` · `MISTAKE`(오정지)뿐이고
                      `WITHDRAWN`(탈퇴 승인으로 종결)은 시스템 전용입니다. 바디 생략 가능.
                    - `note` — 해제 메모(선택 · 500자 이내 · 처리 이력에 남는다).
                    - 지급 정지 중이던 브랜드 몫은 해제 다음 영업일 지급 예정으로 돌아갑니다(다른 보류 사유가 남으면 보류 유지).
                    - 운영팀 스레드에 「정지 해제」 카드가 붙습니다. 정지 이력과 사유는 그대로 보존됩니다.

                    **권한:** ADMIN
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminBrandDto.ReleaseRequest.class),
                    examples = {
                            @ExampleObject(name = "소명 확인", value = """
                                    { "reason": "RESOLVED", "note": "정산 계좌 변경 요청 승인으로 예금주 일치 확인" }
                                    """),
                            @ExampleObject(name = "바디 생략(= RESOLVED)", value = "{}")
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해제 완료 — 갱신된 레일(`memberStatus=ACTIVE` · `sanction=null` · `canSuspend=true`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.Rail.class))),
            @ApiResponse(responseCode = "400", description = "운영자가 고를 수 없는 사유(`WITHDRAWN`) · note 500자 초과 — INVALID_INPUT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "정지 아님 · 탈퇴 브랜드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "정지 아님", value = """
                                            { "code": "BRAND_NOT_SUSPENDED", "message": "정지 상태가 아닌 브랜드입니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 브랜드", value = """
                                            { "code": "BRAND_WITHDRAWN", "message": "탈퇴한 브랜드입니다." }
                                            """)
                            }))
    })
    AdminBrandDto.Rail release(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                               AdminBrandDto.ReleaseRequest request,
                               @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "탈퇴 승인(M2)",
            description = """
                    **되돌릴 수 없습니다.** 바디 없음.

                    승인 직전에 조건 4종을 다시 판정하고, 하나라도 남으면 409 `BRAND_WITHDRAWAL_CONDITION_UNMET` 에 `unmet`(조건 → 남은 건수)을 실어 거절합니다 —
                    이때는 반려로 처리합니다.

                    | 조건 | `unmet` 키 |
                    | --- | --- |
                    | 진행 중 · 준비 중 공구 | `ACTIVE_GROUP_BUYS` |
                    | 체결 전 계약 | `CONTRACTS_BEFORE_CONCLUSION` |
                    | 미종결 주문(배송 · 반품 · 교환) | `UNCLOSED_ORDERS` |
                    | 진행 중 정산(브랜드 몫) | `UNSETTLED` |

                    **승인되면** 회원 상태가 `WITHDRAWN` 이 되고 로그인이 막히며(이메일 · 비밀번호 폐기 · 토큰 삭제), 사업자 · 정산 · 담당자 정보가 파기되고,
                    상품은 전건 미진열 · 작성중 계약은 삭제 · 남은 연결은 전건 종료됩니다. 열린 정지는 `WITHDRAWN` 사유로 종결됩니다.
                    주문 · 정산 · 계약 · 이력 · 배송 정책은 보존됩니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인 완료 — 갱신된 레일(`memberStatus=WITHDRAWN` · `currentStatus.withdrawal` 채움 · 액션 모두 false)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.Rail.class))),
            @ApiResponse(responseCode = "404", description = "없음 · 다른 브랜드의 신청 — BRAND_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BRAND_WITHDRAWAL_NOT_FOUND", "message": "존재하지 않는 탈퇴 신청입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "조건 미충족 · 대기 아님(취소 · 처리됨) · 탈퇴 브랜드",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "조건 미충족", value = """
                                            { "code": "BRAND_WITHDRAWAL_CONDITION_UNMET", "message": "탈퇴 조건이 충족되지 않았습니다.",
                                              "unmet": { "UNCLOSED_ORDERS": 2 } }
                                            """),
                                    @ExampleObject(name = "대기 아님", value = """
                                            { "code": "BRAND_WITHDRAWAL_NOT_PENDING", "message": "승인 대기 중인 탈퇴 신청이 아닙니다." }
                                            """)
                            }))
    })
    AdminBrandDto.Rail approveWithdrawal(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                         @Parameter(description = "탈퇴 신청 ID — 상세 `rail.withdrawalReview.requestId`", required = true, example = "31")
                                         Long requestId,
                                         @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "탈퇴 반려(M3)",
            description = """
                    반려 사유는 신청 조건과 같은 축입니다. `OTHER` 면 `note`(브랜드에 전달할 안내 · 500자 이내) 필수.

                    | `reason` | 라벨 |
                    | --- | --- |
                    | `CLAIM_UNRESOLVED` | 반품·교환 처리 미완료 |
                    | `UNSHIPPED_ORDERS` | 미배송 주문 잔존 |
                    | `ACTIVE_GROUP_BUYS` | 진행중 공구 잔존 |
                    | `UNSETTLED` | 미정산액 잔존 |
                    | `CONTRACT_IN_PROGRESS` | 체결 전 계약 잔존 |
                    | `OTHER` | 기타(직접 입력) |

                    회원 상태는 바뀌지 않고(내내 활성) 브랜드는 바로 재신청할 수 있습니다. 사유 · 안내는 파트너센터 계정 정보의 `withdrawal.lastRejection` 에 실립니다.

                    **권한:** ADMIN
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminBrandDto.WithdrawalRejectRequest.class),
                    examples = {
                            @ExampleObject(name = "정형 사유", value = """
                                    { "reason": "CLAIM_UNRESOLVED", "note": "반품 검수 2건을 마친 뒤 다시 신청해 주세요." }
                                    """),
                            @ExampleObject(name = "기타(안내 필수)", value = """
                                    { "reason": "OTHER", "note": "소비자 분쟁 1건이 조정 중입니다. 종결 후 다시 신청해 주세요." }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려 완료 — 갱신된 레일(`withdrawalReview=null`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.Rail.class))),
            @ApiResponse(responseCode = "400", description = "기타 사유 안내 누락 · 사유 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BRAND_WITHDRAWAL_REJECT_DETAIL_REQUIRED", "message": "기타 사유를 선택하면 브랜드에 전달할 안내를 입력해야 합니다." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "BRAND_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "대기 아님 — BRAND_WITHDRAWAL_NOT_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Rail rejectWithdrawal(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                        @Parameter(description = "탈퇴 신청 ID", required = true, example = "31") Long requestId,
                                        AdminBrandDto.WithdrawalRejectRequest request,
                                        @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "처리 이력 전체",
            description = """
                    최신순. 이 화면의 조치(정지 · 해제 · 탈퇴 신청 · 취소 · 승인 · 반려 · 메모)와 가입 신청 · 입점 승인 · 변경 요청(접수 · 승인 · 반려)을 합쳐 내립니다.
                    파기 대상이 아닙니다(탈퇴 후에도 남는다).

                    `type` — `SUSPENDED` · `SUSPENSION_RELEASED` · `WITHDRAWAL_REQUESTED` · `WITHDRAWAL_CANCELED` · `WITHDRAWAL_APPROVED` · `WITHDRAWAL_REJECTED` ·
                    `MEMO_CREATED` · `MEMO_UPDATED` · `MEMO_DELETED` · `SIGNED_UP` · `ONBOARDING_APPROVED` · `CHANGE_REQUEST_RECEIVED` · `CHANGE_REQUEST_APPROVED` ·
                    `CHANGE_REQUEST_REJECTED`. `tone` 으로 점 색을 그립니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.HistoryPage.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "type": "WITHDRAWAL_REQUESTED", "label": "탈퇴 신청 접수", "tone": "WARNING", "actorType": "BRAND",
                                          "actorName": "김담당", "detail": "사유: 사업 종료", "refType": "BRAND_WITHDRAWAL_REQUEST", "refId": 31,
                                          "occurredAt": "2026-10-14T14:20:00" },
                                        { "type": "CHANGE_REQUEST_APPROVED", "label": "변경 요청 승인", "tone": "SUCCESS", "actorType": "ADMIN",
                                          "actorName": "김운영", "detail": "정산 계좌", "refType": "BRAND_CHANGE_REQUEST", "refId": 15,
                                          "occurredAt": "2026-08-10T11:05:00" },
                                        { "type": "ONBOARDING_APPROVED", "label": "입점 승인", "tone": "SUCCESS", "actorType": "ADMIN",
                                          "actorName": "운영자", "detail": null, "refType": null, "refId": null, "occurredAt": "2026-03-05T14:00:00" }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 3, "limit": 20, "hasNext": false }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.HistoryPage histories(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                        @ParameterObject PagingRequest paging);

    @Operation(summary = "관리자 메모 목록",
            description = """
                    최신순 · 브랜드 비노출. `editable` = 요청 운영자가 작성자 본인 ∧ 작성 24시간 이내 ∧ 탈퇴 계정 아님. `modifiedAt` 이 있으면 「수정됨」.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.MemoPage.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "memoId": 9, "authorName": "김운영", "content": "탈퇴 신청 — 9월 정산 지급 완료 확인 후 승인 예정",
                                          "createdAt": "2026-10-15T10:00:00", "modifiedAt": null, "editable": true },
                                        { "memoId": 4, "authorName": "박운영", "content": "배송 지연 2회 — 다음 지연 시 정지 검토",
                                          "createdAt": "2026-09-02T13:00:00", "modifiedAt": "2026-09-02T15:10:00", "editable": false }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.MemoPage memos(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                 @ParameterObject PagingRequest paging,
                                 @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 기입",
            description = """
                    덮어쓰기가 아니라 **누적 기입**입니다. 공백 제거 후 1 ~ 300자 · 브랜드 비노출 · 처리 이력에 「관리자 메모 기입」이 남습니다.
                    탈퇴 계정은 조회 전용(409 `BRAND_WITHDRAWN`).

                    **권한:** ADMIN
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminBrandDto.MemoRequest.class),
                    examples = @ExampleObject(value = """
                            { "content": "탈퇴 신청 — 9월 정산 지급 완료 확인 후 승인 예정" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기입 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.Memo.class),
                            examples = @ExampleObject(value = """
                                    { "memoId": 9, "authorName": "김운영", "content": "탈퇴 신청 — 9월 정산 지급 완료 확인 후 승인 예정",
                                      "createdAt": "2026-10-15T10:00:00", "modifiedAt": null, "editable": true }
                                    """))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과 — INVALID_INPUT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴 브랜드 — BRAND_WITHDRAWN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Memo createMemo(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                  AdminBrandDto.MemoRequest request,
                                  @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 수정",
            description = """
                    작성자 본인만 · 작성 후 24시간 이내만 고칠 수 있습니다. 저장하면 `modifiedAt` 이 채워지고 처리 이력에 「관리자 메모 수정」이 남습니다.

                    **권한:** ADMIN
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AdminBrandDto.MemoRequest.class),
                    examples = @ExampleObject(value = """
                            { "content": "탈퇴 신청 — 9월 정산 지급 완료(10.15) 확인, 승인 진행" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.Memo.class))),
            @ApiResponse(responseCode = "400", description = "빈 내용 · 300자 초과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "작성자 아님 · 24시간 경과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "작성자 아님", value = """
                                            { "code": "BRAND_MEMO_NOT_AUTHOR", "message": "작성자만 수정·삭제할 수 있습니다." }
                                            """),
                                    @ExampleObject(name = "24시간 경과", value = """
                                            { "code": "BRAND_MEMO_EDIT_WINDOW_CLOSED", "message": "작성 후 24시간이 지난 메모는 수정·삭제할 수 없습니다." }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "메모 없음 · 다른 브랜드의 메모 — BRAND_MEMO_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BRAND_MEMO_NOT_FOUND", "message": "존재하지 않는 메모입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "탈퇴 브랜드 — BRAND_WITHDRAWN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.Memo updateMemo(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                  @Parameter(description = "메모 ID", required = true, example = "9") Long memoId,
                                  AdminBrandDto.MemoRequest request,
                                  @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "관리자 메모 삭제",
            description = """
                    수정과 같은 조건(작성자 본인 · 24시간 이내 · 탈퇴 계정 아님). 삭제된 메모는 목록에서 빠지고, 처리 이력에 「관리자 메모 삭제」가 남습니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "작성자 아님(BRAND_MEMO_NOT_AUTHOR) · 24시간 경과(BRAND_MEMO_EDIT_WINDOW_CLOSED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "BRAND_MEMO_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴 브랜드 — BRAND_WITHDRAWN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteMemo(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                    @Parameter(description = "메모 ID", required = true, example = "9") Long memoId,
                                    @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "연결 인플루언서 전체",
            description = """
                    상세의 「연결 인플루언서」 카드 [전체 보기]. 연결중 먼저(최근 연결순) → 연결 종료(최근 종료순). 요청중 · 거절은 내리지 않습니다.
                    행 클릭 → 인플루언서 상세(`GET /v1/admin/creators/{creatorId}`).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.ConnectedCreatorPage.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "content": [
                                        { "creatorId": 3018, "showroomName": "소연 쇼룸", "connectedAt": "2026-04-01T10:00:00",
                                          "disconnectedAt": null, "activeGroupBuyCount": 1, "status": "CONNECTED", "statusLabel": "연결중" },
                                        { "creatorId": 2990, "showroomName": "예린 뷰티", "connectedAt": "2026-03-10T10:00:00",
                                          "disconnectedAt": "2026-07-01T09:00:00", "activeGroupBuyCount": 0, "status": "DISCONNECTED", "statusLabel": "연결 종료" }
                                      ],
                                      "pageInfo": { "currentPage": 1, "totalPages": 1, "totalResults": 2, "limit": 20, "hasNext": false }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    AdminBrandDto.ConnectedCreatorPage connectedCreators(@Parameter(description = "마켓 ID", required = true, example = "42") Long marketId,
                                                         @ParameterObject PagingRequest paging);

    @Operation(summary = "모달 셀렉트 사유 목록",
            description = """
                    정지(M1 · 지급 정지 여부 `holdsPayout` 포함) · 정지 해제 · 탈퇴 반려(M3) 사유의 코드와 라벨 — enum 단일 소스라 FE 가 목록을 하드코딩하지 않습니다.
                    `detailRequired=true` 인 항목(기타)을 고르면 상세 · 안내 입력란이 필수가 됩니다. 인플루언서 상세의 같은 API 와 같은 모양입니다.

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminBrandDto.ReasonOptions.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "suspensionReasons": [
                                        { "code": "SETTLEMENT_INFO_MISMATCH", "label": "정산 정보 불일치", "detailRequired": false, "holdsPayout": true },
                                        { "code": "BUSINESS_INFO_FALSIFIED", "label": "사업자 정보 허위 기재", "detailRequired": false, "holdsPayout": true },
                                        { "code": "PRODUCT_INFO_FALSIFIED", "label": "상품 정보 허위·과장", "detailRequired": false, "holdsPayout": false },
                                        { "code": "REPEATED_SHIPPING_DELAY", "label": "배송 지연 반복", "detailRequired": false, "holdsPayout": false },
                                        { "code": "CONSUMER_RESPONSE_NEGLECT", "label": "소비자 응대 불이행", "detailRequired": false, "holdsPayout": false },
                                        { "code": "OTHER", "label": "기타(직접 입력)", "detailRequired": true, "holdsPayout": false }
                                      ],
                                      "releaseReasons": [
                                        { "code": "RESOLVED", "label": "소명 · 시정 확인", "detailRequired": false, "holdsPayout": null },
                                        { "code": "MISTAKE", "label": "오정지", "detailRequired": false, "holdsPayout": null }
                                      ],
                                      "withdrawalRejectReasons": [
                                        { "code": "CLAIM_UNRESOLVED", "label": "반품·교환 처리 미완료", "detailRequired": false, "holdsPayout": null },
                                        { "code": "UNSHIPPED_ORDERS", "label": "미배송 주문 잔존", "detailRequired": false, "holdsPayout": null },
                                        { "code": "ACTIVE_GROUP_BUYS", "label": "진행중 공구 잔존", "detailRequired": false, "holdsPayout": null },
                                        { "code": "UNSETTLED", "label": "미정산액 잔존", "detailRequired": false, "holdsPayout": null },
                                        { "code": "CONTRACT_IN_PROGRESS", "label": "체결 전 계약 잔존", "detailRequired": false, "holdsPayout": null },
                                        { "code": "OTHER", "label": "기타(직접 입력)", "detailRequired": true, "holdsPayout": null }
                                      ]
                                    }
                                    """)))
    })
    AdminBrandDto.ReasonOptions reasonOptions();
}
