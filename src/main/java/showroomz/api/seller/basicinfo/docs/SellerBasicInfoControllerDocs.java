package showroomz.api.seller.basicinfo.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.basicinfo.dto.SellerBasicInfoDto;

@Tag(name = "Seller - Basic Info", description = "파트너센터 기본정보 관리 API")
public interface SellerBasicInfoControllerDocs {

    @Operation(summary = "사업자 정보 조회", description = """
            사업자 정보 탭. 항목은 3분류다 — ① 직접 수정(tax 확인용 이메일 · 브랜드 사이트 링크 → `PATCH /business`) ·
            ② 변경 요청(대표자명 · 상호 · 사업장 주소 등 → `POST /v1/seller/change-requests` · 운영자 승인 후 반영) · ③ 변경 불가(사업자등록번호).

            - `reviewDocuments` — 입점 심사 때 낸 서류(조회 전용 · 있는 것만): `BUSINESS_LICENSE` 사업자등록증 · `MAIL_ORDER_REPORT` 통신판매업신고증 · `BANKBOOK` 통장사본.
            - `changeRequest` — 사업자 정보(`BUSINESS_INFO`) 변경 요청 배너. 검토 대기 요청이 있으면 그것, 없으면 처리됐지만 아직 [확인]하지 않은 최근 1건 · 없으면 null.
              `cancelable=true` 면 [요청 취소] 버튼, `REJECTED` 면 `rejectReason` · `rejectReasonDetail` 을 그린다.
            - `sanction` — 계정 이용 정지 중일 때만(정지 배너 재료). 상세 사유는 내리지 않는다. `payoutHeld=true` 면 브랜드 몫 지급이 정지된 상태다.
            - `businessAddress` 는 사업장 주소 + 상세주소를 합친 한 문자열이다.

            **권한:** SELLER
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.BusinessInfoResponse.class),
                            examples = @ExampleObject(name = "변경 요청 검토 대기", value = """
                                    {
                                      "brandName": "코코브라운",
                                      "brandStatus": "ACTIVE",
                                      "sanction": null,
                                      "businessType": "법인사업자",
                                      "marketName": "코코브라운",
                                      "representativeName": "김대표",
                                      "companyName": "(주)코코브라운",
                                      "businessRegistrationNumber": "123-45-67890",
                                      "businessCondition": "도소매",
                                      "businessAddress": "서울시 서초구 서초대로 1 3층",
                                      "mailOrderRegNumber": "2024-서울서초-0001",
                                      "taxEmail": "tax@cocobrown.example",
                                      "brandSiteUrl": "https://cocobrown.example",
                                      "reviewDocuments": [
                                        { "documentType": "BUSINESS_LICENSE", "label": "사업자등록증", "fileUrl": "https://cdn…/license.jpg", "extension": "jpg" },
                                        { "documentType": "MAIL_ORDER_REPORT", "label": "통신판매업신고증", "fileUrl": "https://cdn…/mail-order.pdf", "extension": "pdf" },
                                        { "documentType": "BANKBOOK", "label": "통장사본", "fileUrl": "https://cdn…/bankbook.jpg", "extension": "jpg" }
                                      ],
                                      "changeRequest": {
                                        "requestId": 12, "requestCode": "CHG-2026-0001", "type": "BUSINESS_INFO", "status": "PENDING",
                                        "changedFieldLabels": ["대표자명", "사업장 주소"], "requestedAt": "2026-08-09T14:22:10", "processedAt": null,
                                        "cancelable": true, "rejectReason": null, "rejectReasonDetail": null, "requestedAccount": null
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "마켓 없음 — MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerBasicInfoDto.BusinessInfoResponse> getBusinessInfo();

    @Operation(
            summary = "사업자 정보 직접 수정",
            description = """
                    3분류 중 ①직접 수정 항목만 다룬다 — tax 확인용 이메일 · 브랜드 사이트 링크. 운영자 승인 없이 즉시 저장된다.

                    - `taxEmail` **필수** · 이메일 형식.
                    - `brandSiteUrl` 선택 — `http://` · `https://` 로 시작해야 한다. **비우면(빈 문자열 포함) null 로 저장**되고 소비자 앱의 [브랜드 사이트] 버튼이 사라진다.
                    - 대표자명 · 상호 · 주소 등 ② 항목은 이 API 로 바꿀 수 없다 — `POST /v1/seller/change-requests`.

                    **권한:** SELLER
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerBasicInfoDto.UpdateBusinessInfoRequest.class),
                    examples = {
                            @ExampleObject(name = "사이트 링크 포함", value = """
                                    { "taxEmail": "tax@cocobrown.example", "brandSiteUrl": "https://cocobrown.example" }
                                    """),
                            @ExampleObject(name = "사이트 링크 제거", value = """
                                    { "taxEmail": "tax@cocobrown.example", "brandSiteUrl": "" }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "이메일 미입력 · 형식 오류 또는 http(s):// 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "이메일 형식", value = """
                                            { "code": "INVALID_INPUT", "message": "올바른 이메일 형식이 아닙니다. (예: example@domain.com)" }
                                            """),
                                    @ExampleObject(name = "링크 형식", value = """
                                            { "code": "INVALID_INPUT", "message": "올바른 주소 형식이 아닙니다. (예: https://example.com)" }
                                            """)
                            }))
    })
    ResponseEntity<Void> updateBusinessInfo(@Valid @RequestBody SellerBasicInfoDto.UpdateBusinessInfoRequest request);

    @Operation(
            summary = "정산 계좌 조회",
            description = """
                    정산 계좌 탭. 계좌번호는 **뒤 6자리만** 노출한다(§16-7 · 파트너센터 응답에만 적용 — `******456789`).

                    - 계좌는 직접 수정할 수 없다 — 바꾸려면 통장 사본과 함께 변경 요청(`POST /v1/seller/change-requests` · `type=SETTLEMENT_ACCOUNT`)을 낸다.
                      은행 셀렉트는 `GET /v1/common/banks`.
                    - `changeRequest` — 정산 계좌 변경 요청 배너(검토 대기 또는 확인 전 처리 결과 1건 · 없으면 null). `requestedAccount` 에 요청한 새 계좌(마스킹)가 실린다.

                    **권한:** SELLER
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.SettlementInfoResponse.class),
                            examples = {
                                    @ExampleObject(name = "요청 없음", value = """
                                            { "bankName": "신한은행", "maskedAccountNumber": "******383841", "accountHolder": "(주)코코브라운", "changeRequest": null }
                                            """),
                                    @ExampleObject(name = "변경 요청 반려", value = """
                                            {
                                              "bankName": "신한은행", "maskedAccountNumber": "******383841", "accountHolder": "(주)코코브라운",
                                              "changeRequest": {
                                                "requestId": 15, "requestCode": "CHG-2026-0004", "type": "SETTLEMENT_ACCOUNT", "status": "REJECTED",
                                                "changedFieldLabels": ["은행", "계좌번호", "예금주"], "requestedAt": "2026-08-09T09:00:00",
                                                "processedAt": "2026-08-10T11:05:00", "cancelable": false,
                                                "rejectReason": "예금주와 사업자 명의 불일치", "rejectReasonDetail": null,
                                                "requestedAccount": { "bankName": "국민은행", "maskedAccountNumber": "******902218" }
                                              }
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "404", description = "마켓 없음 — MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerBasicInfoDto.SettlementInfoResponse> getSettlementInfo();

    @Operation(summary = "담당자·CS 조회", description = """
            판매 담당자(이름 · 연락처 — 내부 관리용) · 고객센터 번호(소비자 노출) + 반품 수취 주소 4필드(수취인 · 연락처 · 주소 · 상세주소).
            반품 수취 주소는 소비자 반품 · 교환 안내에 그대로 쓰인다.

            **권한:** SELLER
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.ManagerInfoResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "managerName": "김담당",
                                      "managerContact": "010-1234-5678",
                                      "csNumber": "02-1234-5678",
                                      "returnAddress": { "recipientName": "코코브라운 물류", "recipientContact": "010-9876-5432",
                                                         "address": "경기도 성남시 분당구 판교로 1", "detailAddress": "B동 2층" }
                                    }
                                    """))),
            @ApiResponse(responseCode = "404", description = "마켓 없음 — MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerBasicInfoDto.ManagerInfoResponse> getManagerInfo();

    @Operation(summary = "담당자·CS 일괄 저장", description = """
            7필드를 한 번에 저장한다(직접 수정 · ①분류 · 부분 수정 아님). 모두 필수다.

            | 필드 | 형식 |
            | --- | --- |
            | `managerName` | 판매 담당자 이름 |
            | `managerContact` | `010-0000-0000`(010 11자리 고정 · 내부 관리용) |
            | `csNumber` | `02-1234-5678` · `1588-1234` · `1234`(대표번호 · 단축번호 허용) |
            | `recipientName` | 반품 수취인 |
            | `recipientContact` | `010` · `011` · `016~019` 휴대폰(외부 노출용 · 구 통신사 번호 허용) |
            | `address` | 카카오 주소 API 결과(FE 에서 readonly) |
            | `detailAddress` | 상세주소 |

            **권한:** SELLER
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerBasicInfoDto.UpdateManagerInfoRequest.class),
                    examples = @ExampleObject(value = """
                            {
                              "managerName": "김담당",
                              "managerContact": "010-1234-5678",
                              "csNumber": "02-1234-5678",
                              "recipientName": "코코브라운 물류",
                              "recipientContact": "010-9876-5432",
                              "address": "경기도 성남시 분당구 판교로 1",
                              "detailAddress": "B동 2층"
                            }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "필수 누락 · 연락처 · 전화번호 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "담당자 연락처", value = """
                                            { "code": "INVALID_INPUT", "message": "올바른 핸드폰 번호 형식으로 입력해 주세요. (예: 010-0000-0000)" }
                                            """),
                                    @ExampleObject(name = "고객센터 번호", value = """
                                            { "code": "INVALID_INPUT", "message": "올바른 전화번호 형식으로 입력해 주세요. (예: 02-1234-5678)" }
                                            """),
                                    @ExampleObject(name = "주소 미입력", value = """
                                            { "code": "INVALID_INPUT", "message": "주소를 검색해 입력해 주세요." }
                                            """)
                            }))
    })
    ResponseEntity<Void> updateManagerInfo(@Valid @RequestBody SellerBasicInfoDto.UpdateManagerInfoRequest request);

    @Operation(summary = "배송·반품 정책 조회",
            description = """
                    배송·반품 정책 탭. 온보딩(`POST /v1/seller/auth/complete-registration`)에서 입력한 값과 같은 컬럼을 읽는다.

                    | 필드 | 의미 |
                    | --- | --- |
                    | `shippingLeadDays` | 발송 기한 N — 공구 마감 후 N영업일(주말·공휴일 제외 · 1~7). 값이 없던 옛 마켓은 기본값으로 내린다 |
                    | `shipDueText` | 소비자 상품 상세에 그대로 나가는 문구 — 「공구 마감 후 N영업일 이내 발송 (주말·공휴일 제외)」 |
                    | `defaultDeliveryFee` | 기본 배송비 — 반품 배송비 차감 · 교환 재발송비도 이 값이다 |
                    | `freeShippingThreshold` | 무료배송 기준금액 — **null 이면 무료배송 없음**(0 으로 저장된 값도 없음으로 내린다) |
                    | `jejuSurcharge` | 제주 추가비 |
                    | `remoteAreaSurcharge` | 도서산간 추가비 — **제주 제외** |

                    반품비 · 교환비는 따로 없다. 반품 수취 주소는 담당자·CS 탭에 있다.

                    **권한:** SELLER
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.DeliveryPolicyResponse.class),
                            examples = {
                                    @ExampleObject(name = "무료배송 기준 있음", value = """
                                            {
                                              "shippingLeadDays": 3,
                                              "shipDueText": "공구 마감 후 3영업일 이내 발송 (주말·공휴일 제외)",
                                              "defaultDeliveryFee": 3000,
                                              "freeShippingThreshold": 50000,
                                              "jejuSurcharge": 5000,
                                              "remoteAreaSurcharge": 4000
                                            }
                                            """),
                                    @ExampleObject(name = "무료배송 없음", value = """
                                            {
                                              "shippingLeadDays": 2,
                                              "shipDueText": "공구 마감 후 2영업일 이내 발송 (주말·공휴일 제외)",
                                              "defaultDeliveryFee": 3500,
                                              "freeShippingThreshold": null,
                                              "jejuSurcharge": 0,
                                              "remoteAreaSurcharge": 0
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SELLER · CREATOR 가 아님 — FORBIDDEN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "판매자 · 마켓 없음 — SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerBasicInfoDto.DeliveryPolicyResponse> getDeliveryPolicy();

    @Operation(summary = "배송·반품 정책 저장",
            description = """
                    다섯 값을 한 번에 저장한다(부분 수정 아님 — 화면의 값을 모두 보낸다).

                    - **이후 접수되는 주문에만 적용**된다 — 발송 기한 N · 배송비는 주문 생성 때 하위주문에 스냅샷되므로 이미 접수된 주문은 그대로다.
                    - 발송 기한 `shippingLeadDays` **필수** · 1~7영업일(전자상거래법 제15조① 공급 시기 약정 · 상한 7은 운영정책).
                      값이 바뀌면 변경 이력이 남는다(어드민 브랜드 상세 「08.20 2→3 변경」).
                    - 기본 배송비 `defaultDeliveryFee` **필수** · 0 이상.
                    - 무료배송 기준금액 `freeShippingThreshold` — **비우거나(null) 0 이면 무료배송 없음**(시안 06 「0원이면 무료배송을 적용하지 않습니다」) · 0 은 null 로 저장된다. 모든 주문을 무료배송으로 하려면 기본 배송비를 0 으로 둔다.
                    - 제주 추가비 · 도서산간 추가비 — 비우면 0. 도서산간은 제주를 뺀 지역이다.

                    **권한:** SELLER
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerBasicInfoDto.UpdateDeliveryPolicyRequest.class),
                    examples = {
                            @ExampleObject(name = "전체 입력", value = """
                                    {
                                      "shippingLeadDays": 3,
                                      "defaultDeliveryFee": 3000,
                                      "freeShippingThreshold": 50000,
                                      "jejuSurcharge": 5000,
                                      "remoteAreaSurcharge": 4000
                                    }
                                    """),
                            @ExampleObject(name = "무료배송 없음 · 추가비 0", value = """
                                    {
                                      "shippingLeadDays": 2,
                                      "defaultDeliveryFee": 3500,
                                      "freeShippingThreshold": null
                                    }
                                    """)
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "발송 기한 미입력 · 범위(1~7) · 기본 배송비 미입력 · 금액 음수",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "발송 기한 미입력", value = """
                                            { "code": "INVALID_INPUT", "message": "발송 기한을 입력해 주세요." }
                                            """),
                                    @ExampleObject(name = "발송 기한 범위", value = """
                                            { "code": "INVALID_INPUT", "message": "발송 기한은 1~7영업일로 입력해 주세요." }
                                            """),
                                    @ExampleObject(name = "제주 추가비 음수", value = """
                                            { "code": "INVALID_INPUT", "message": "제주 추가비는 0 이상이어야 합니다." }
                                            """)
                            }))
    })
    ResponseEntity<Void> updateDeliveryPolicy(@Valid @RequestBody SellerBasicInfoDto.UpdateDeliveryPolicyRequest request);

    @Operation(summary = "계정 정보 조회", description = """
            로그인 이메일 · 다음 변경 가능일과 **탈퇴 블록**(`withdrawal` · 4-F ~ 4-I)을 내려준다.

            **탈퇴 `state`** — 위에서부터 처음 맞는 것:
            1. `PENDING`(4-I 심사 중) — 승인 대기 신청이 있다. `pendingRequest` 에 신청 id · 일시 · 사유 · 남기실 말씀 · 신청자(담당자명 스냅샷 · 현재 로그인 이메일)
            2. `AVAILABLE`(4-F) — 조건 4종 모두 0건
            3. `BLOCKED`(4-G · 4-G2) — 조건 미충족

            **조건 4종(`conditions`)** — 상태와 무관하게 항상 4행을 내린다. 어드민 승인 직전 재검증과 같은 판정식이다.

            | `code` | 조건 |
            | --- | --- |
            | `ACTIVE_GROUP_BUYS` | 진행 중 · 준비 중 공구 |
            | `CONTRACTS_BEFORE_CONCLUSION` | 체결 전 계약(검토 대기 · 서명 진행 중 등) |
            | `UNCLOSED_ORDERS` | 미종결 주문(배송 · 반품 · 교환) |
            | `UNSETTLED` | 진행 중 정산 — 브랜드 몫 기준 |

            **반려 결과(`lastRejection`)** — 가장 최근 신청이 반려였고 열린 신청이 없으면 사유 · 운영자 안내가 실린다. 확인 처리가 없어
            다음 신청 전까지 계속 내려간다. 재신청은 바로 가능하다.

            **사유 선택지(`reasonOptions`)** — M5 드롭다운의 단일 소스. 「선택하지 않음」은 FE 기본값(= `reason` 미전송).

            마켓이 없는 계정은 `withdrawal = null`.

            **권한:** SELLER
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.AccountInfoResponse.class),
                            examples = {
                                    @ExampleObject(name = "4-G 조건 미충족(BLOCKED)", value = """
                                            {
                                              "loginEmail": "brand@example.com",
                                              "emailChangeable": true,
                                              "lastEmailChangedAt": null,
                                              "nextEmailChangeableAt": null,
                                              "withdrawal": {
                                                "state": "BLOCKED", "satisfiedCount": 2, "totalCount": 4,
                                                "conditions": [
                                                  { "code": "ACTIVE_GROUP_BUYS", "label": "진행 중 · 준비 중 공구", "count": 1, "satisfied": false },
                                                  { "code": "CONTRACTS_BEFORE_CONCLUSION", "label": "체결 전 계약", "count": 0, "satisfied": true },
                                                  { "code": "UNCLOSED_ORDERS", "label": "미종결 주문(배송 · 반품 · 교환)", "count": 0, "satisfied": true },
                                                  { "code": "UNSETTLED", "label": "진행 중 정산", "count": 2, "satisfied": false }
                                                ],
                                                "pendingRequest": null,
                                                "lastRejection": null,
                                                "reasonOptions": [
                                                  { "code": "BUSINESS_CLOSED", "label": "사업 종료" },
                                                  { "code": "LOW_PERFORMANCE", "label": "공구 성과 미흡" },
                                                  { "code": "OPERATION_BURDEN", "label": "운영 부담(배송 · CS)" },
                                                  { "code": "OTHER_CHANNEL_FOCUS", "label": "다른 채널 집중" },
                                                  { "code": "OTHER", "label": "기타" }
                                                ]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "4-I 심사 중(PENDING)", value = """
                                            {
                                              "loginEmail": "brand@example.com",
                                              "emailChangeable": false,
                                              "lastEmailChangedAt": "2026-10-02T10:00:00",
                                              "nextEmailChangeableAt": "2026-11-02T10:00:00",
                                              "withdrawal": {
                                                "state": "PENDING", "satisfiedCount": 4, "totalCount": 4,
                                                "conditions": [ "… 4행 — 모두 satisfied" ],
                                                "pendingRequest": {
                                                  "requestId": 31, "requestedAt": "2026-10-14T14:20:00",
                                                  "reason": "BUSINESS_CLOSED", "reasonLabel": "사업 종료",
                                                  "message": "그동안 감사했습니다.",
                                                  "requesterName": "김담당", "requesterEmail": "brand@example.com"
                                                },
                                                "lastRejection": null,
                                                "reasonOptions": [ "… 5개" ]
                                              }
                                            }
                                            """),
                                    @ExampleObject(name = "반려 직후(AVAILABLE + lastRejection)", value = """
                                            {
                                              "loginEmail": "brand@example.com",
                                              "emailChangeable": true,
                                              "lastEmailChangedAt": null,
                                              "nextEmailChangeableAt": null,
                                              "withdrawal": {
                                                "state": "AVAILABLE", "satisfiedCount": 4, "totalCount": 4,
                                                "conditions": [ "… 4행 — 모두 satisfied" ],
                                                "pendingRequest": null,
                                                "lastRejection": {
                                                  "requestId": 31, "rejectedAt": "2026-10-16T11:00:00",
                                                  "reasonCode": "UNCLOSED_ORDERS", "reasonLabel": "미종결 주문 잔존",
                                                  "note": "반품 검수 2건을 마친 뒤 다시 신청해 주세요."
                                                },
                                                "reasonOptions": [ "… 5개" ]
                                              }
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SELLER · CREATOR 가 아님 — FORBIDDEN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "판매자 없음 — SELLER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerBasicInfoDto.AccountInfoResponse> getAccountInfo();

    @Operation(
            summary = "비밀번호 변경",
            description = """
                    현재 비밀번호를 검증한 뒤 바꾼다. 확인 순서 — ① 현재 비밀번호 일치(401) ② 새 비밀번호 확인 일치(400).

                    - 새 비밀번호 — **8~16자 · 영문 · 숫자 · 특수문자(`@$!%*#?&`) 각 1자 이상**.
                    - 변경해도 기존 로그인 세션 · 토큰은 유지된다.

                    **권한:** SELLER
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerBasicInfoDto.ChangePasswordRequest.class),
                    examples = @ExampleObject(value = """
                            { "currentPassword": "OldPass1!", "newPassword": "NewPass2@", "newPasswordConfirm": "NewPass2@" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "새 비밀번호 확인 불일치 · 형식 오류 · 미입력",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "확인 불일치", value = """
                                            { "code": "NEW_PASSWORD_CONFIRM_MISMATCH", "message": "비밀번호가 일치하지 않습니다." }
                                            """),
                                    @ExampleObject(name = "형식 오류", value = """
                                            { "code": "INVALID_INPUT", "message": "8~16자 영문·숫자·특수문자 조합으로 입력해 주세요." }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "현재 비밀번호 불일치 — PASSWORD_MISMATCH",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "PASSWORD_MISMATCH", "message": "현재 비밀번호가 일치하지 않습니다." }
                                    """)))
    })
    ResponseEntity<Void> changePassword(@Valid @RequestBody SellerBasicInfoDto.ChangePasswordRequest request);

    @Operation(
            summary = "로그인 이메일 변경",
            description = """
                    현재 비밀번호를 확인한 뒤 로그인 이메일을 바꾼다. 확인 순서 — ① 현재 비밀번호(401) ② 지금 이메일과 같음(400)
                    ③ **1개월 롤링 제한**(§15-5 · 마지막 변경일 + 1개월 전이면 400 `EMAIL_CHANGE_LIMIT_EXCEEDED` · 메시지에 다음 변경 가능일)
                    ④ 다른 계정 사용 중(400 `DUPLICATE_EMAIL` — 반려된 가입 신청의 이메일은 다시 쓸 수 있다).

                    **성공하면** 구 이메일로 변경 통지 메일이 나가고, 리프레시 토큰이 삭제된다 — 액세스 토큰 만료 뒤 **새 이메일로 다시 로그인**해야 한다.
                    응답은 갱신된 계정 정보(`emailChangeable=false` · `nextEmailChangeableAt` = 지금 + 1개월)다.

                    **권한:** SELLER
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerBasicInfoDto.ChangeEmailRequest.class),
                    examples = @ExampleObject(value = """
                            { "currentPassword": "NewPass2@", "newEmail": "partner@cocobrown.example" }
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공 — 갱신된 계정 정보",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.AccountInfoResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "loginEmail": "partner@cocobrown.example",
                                      "emailChangeable": false,
                                      "lastEmailChangedAt": "2026-10-18T10:00:00",
                                      "nextEmailChangeableAt": "2026-11-18T10:00:00",
                                      "withdrawal": { "…": "계정 정보 조회와 같음" }
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "월 1회 제한 초과 · 지금 이메일과 같음 · 이메일 중복 · 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "월 1회 제한", value = """
                                            { "code": "EMAIL_CHANGE_LIMIT_EXCEEDED",
                                              "message": "로그인 이메일은 월 1회만 변경할 수 있습니다. 다음 변경 가능일은 2026.11.18입니다." }
                                            """),
                                    @ExampleObject(name = "같은 이메일", value = """
                                            { "code": "INVALID_INPUT", "message": "현재 이메일과 동일합니다." }
                                            """),
                                    @ExampleObject(name = "이메일 중복", value = """
                                            { "code": "DUPLICATE_EMAIL", "message": "이미 사용 중인 이메일입니다." }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "현재 비밀번호 불일치 — PASSWORD_MISMATCH",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerBasicInfoDto.AccountInfoResponse> changeEmail(@Valid @RequestBody SellerBasicInfoDto.ChangeEmailRequest request);

    @Operation(summary = "탈퇴 신청", description = """
            M5 모달의 [탈퇴 신청]. 브랜드는 즉시 탈퇴가 없다 — 신청 → 운영자 승인(§51-5). `DELETE /v1/seller/auth/withdraw` 는
            409 `BRAND_WITHDRAWAL_REQUIRES_REQUEST` 로 이 API 를 안내한다.

            - 사유 · 남기실 말씀은 **모두 선택**이다 — 바디를 생략하거나 `{}` 로 보내도 신청된다. 모르는 사유 코드는 400.
            - 남기실 말씀은 1000자 이내.
            - 조건 4종을 **서버가 다시 판정**한다 — 하나라도 남았으면 409 `BRAND_WITHDRAWAL_CONDITION_UNMET` + 응답 최상위 `unmet`
              (미충족 조건별 건수). FE 는 버튼을 막아 두므로 이 응답은 「화면을 연 뒤 상황이 바뀐」 경우다 — 계정 정보를 다시 읽어 4-G 로 그린다.
            - 승인 전까지 브랜드는 **활성 그대로**다(공구 · 주문 · 정산 진행). **정지 중에도 신청할 수 있다.**
            - 운영자 승인 시점에 조건을 한 번 더 판정한다. 신청 접수 알림은 없다(어드민 목록의 탈퇴 대기 필터 · 상세 심사 카드가 받는다).
            - 성공하면 FE 는 `GET /v1/seller/basic-info/account` 를 다시 읽어 4-I 를 그린다.

            **권한:** SELLER
            """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerBasicInfoDto.WithdrawalRequestBody.class),
                    examples = {
                            @ExampleObject(name = "사유 · 말씀 입력", value = """
                                    { "reason": "BUSINESS_CLOSED", "message": "그동안 감사했습니다." }
                                    """),
                            @ExampleObject(name = "모두 생략", value = "{}")
                    }))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 접수",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerBasicInfoDto.WithdrawalRequestResponse.class),
                            examples = @ExampleObject(value = """
                                    { "requestId": 31, "requestedAt": "2026-10-14T14:20:00" }
                                    """))),
            @ApiResponse(responseCode = "400", description = "남기실 말씀 1000자 초과 · 모르는 사유 코드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "INVALID_INPUT", "message": "남기실 말씀은 1000자 이내로 입력해 주세요." }
                                    """))),
            @ApiResponse(responseCode = "404", description = "마켓 없음 — MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "조건 미충족 · 이미 심사 중 · 탈퇴 브랜드",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "조건 미충족", value = """
                                            {
                                              "code": "BRAND_WITHDRAWAL_CONDITION_UNMET",
                                              "message": "탈퇴 조건이 충족되지 않았습니다.",
                                              "unmet": { "ACTIVE_GROUP_BUYS": 1, "UNSETTLED": 2 }
                                            }
                                            """),
                                    @ExampleObject(name = "이미 심사 중", value = """
                                            { "code": "BRAND_WITHDRAWAL_ALREADY_PENDING", "message": "이미 승인 대기 중인 탈퇴 신청이 있습니다." }
                                            """),
                                    @ExampleObject(name = "탈퇴 브랜드", value = """
                                            { "code": "BRAND_WITHDRAWN", "message": "탈퇴한 브랜드입니다." }
                                            """)
                            }))
    })
    ResponseEntity<SellerBasicInfoDto.WithdrawalRequestResponse> requestWithdrawal(
            @Valid @RequestBody(required = false) SellerBasicInfoDto.WithdrawalRequestBody request);

    @Operation(summary = "탈퇴 신청 취소", description = """
            4-I 의 [신청 취소]. 운영자가 처리하기 전까지 브랜드가 직접 취소한다. 바디 없음.

            - 다른 브랜드의 신청 id 도 404 다(존재 여부를 흘리지 않는다).
            - 운영자가 막 승인 · 반려한 뒤의 취소는 409 — FE 는 계정 정보를 다시 읽는다.
            - 처리 이력에 「탈퇴 신청 취소」가 남는다.

            **권한:** SELLER
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 완료 — 계정 정보가 4-F · 4-G 로 돌아간다"),
            @ApiResponse(responseCode = "404", description = "없음 · 내 마켓의 신청이 아님 — BRAND_WITHDRAWAL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BRAND_WITHDRAWAL_NOT_FOUND", "message": "존재하지 않는 탈퇴 신청입니다." }
                                    """))),
            @ApiResponse(responseCode = "409", description = "이미 승인 · 반려 · 취소된 신청 — BRAND_WITHDRAWAL_NOT_PENDING",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    { "code": "BRAND_WITHDRAWAL_NOT_PENDING", "message": "승인 대기 중인 탈퇴 신청이 아닙니다." }
                                    """)))
    })
    ResponseEntity<Void> cancelWithdrawal(@Parameter(description = "탈퇴 신청 ID — 계정 정보 `withdrawal.pendingRequest.requestId`",
            required = true, example = "31") @PathVariable Long requestId);
}
