package showroomz.api.seller.contract.docs;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.contract.dto.ContractClausesResponse;
import showroomz.api.seller.contract.dto.ContractCreateRequest;
import showroomz.api.seller.contract.dto.ContractCreateResponse;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.api.seller.contract.dto.ContractDocumentDownloadResponse;
import showroomz.api.seller.contract.dto.ContractDuplicateResponse;
import showroomz.api.seller.contract.dto.ContractFormSourcesResponse;
import showroomz.api.seller.contract.dto.ContractListItem;
import showroomz.api.seller.contract.dto.ContractResendRequestResponse;
import showroomz.api.seller.contract.dto.ContractReviewRequestRequest;
import showroomz.api.seller.contract.dto.ContractSummaryResponse;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.api.seller.contract.dto.ContractValidationResponse;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractSortType;
import showroomz.domain.contract.type.ContractTab;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;

@Tag(name = "Seller - Contract", description = "파트너센터 계약 관리 API (§25·§26)")
public interface SellerContractControllerDocs {

    @Operation(
            summary = "계약 목록",
            description = """
                    상태 탭 · 검색 · 공구 기간 · 정렬로 계약을 조회한다(§26-1).

                    **권한:** SELLER

                    - `tab`은 필터일 뿐이며 응답의 `status`는 항상 9종 중 하나로 내려간다 — 목록 배지는 묶음이 아니라 개별 값이다.
                    - `entryMode`로 행 클릭 시 진입 모드(EDIT/VIEW)를 서버가 판정해 내려준다. FE가 상태표를 다시 들 필요가 없다.
                    - `title`이 비어 있는 작성중 계약은 `null`로 내려간다 — 서버는 `(공구명 미입력)` 같은 표시 문구를 지어내지 않는다.
                    - `startDate`/`endDate`는 **공구 기간이 그 구간에 걸치는** 계약을 찾는다. 기간이 아직 비어 있는 작성중 계약은 이 조건을 걸면 빠진다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "브랜드(마켓) 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<ContractListItem>> getContracts(
            @Parameter(description = "상태 탭 — 기본 ALL") @RequestParam(required = false) ContractTab tab,
            @Parameter(description = "공구명 · 상대 쇼룸명 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "공구 기간 조회 시작일(yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "공구 기간 조회 종료일(yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "정렬 — CREATED_DESC(기본) / START_AT_ASC")
            @RequestParam(required = false) ContractSortType sort,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 카운트 · GNB 배지",
            description = """
                    탭별 건수와 「지금 조치해야 하는」 건수를 돌려준다(설계서 4-4).

                    **권한:** SELLER

                    목록 API와 분리돼 있다 — GNB 배지는 계약 화면이 아닌 곳에서도 폴링되는데,
                    목록에 카운트를 얹으면 배지 하나 때문에 매번 20건을 조회하게 된다.

                    `actionRequiredCount`에 들어가는 것은 둘뿐이다.
                    - `REVIEW_REJECTED` — 브랜드 조치로만 풀리는 유일한 진행 상태
                    - `SIGNING` 이면서 상대만 서명을 마친 상태(B4c)

                    검토 대기·체결 처리 대기는 공이 상대에게 있는 정상 대기라 넣지 않는다 —
                    브랜드가 할 수 없는 일로 배지가 켜지면 안 된다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ContractSummaryResponse> getSummary();

    @Operation(
            summary = "작성 폼 선택지",
            description = """
                    계약 작성 폼의 드롭다운 선택지 — **연결됨 상대 전량 + 진열 상품 전량**(설계서 4-1).

                    **권한:** SELLER

                    기존 `/v1/seller/connections`·`/v1/seller/products`를 쓰지 않는 이유는 둘이 목록용이라
                    페이징·필터가 계약 폼과 다르기 때문이다. 상품에는 **현재 정가**를 동봉해,
                    공구가를 입력하는 즉시 할인율과 H1(공구가 > 정가)을 화면이 먼저 비출 수 있게 한다.
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<ContractFormSourcesResponse> getFormSources();

    @Operation(
            summary = "표준 조항",
            description = """
                    작성 화면의 표준 조항 카드와 전문 모달(C5)이 함께 쓰는 응답이다.

                    **권한:** SELLER

                    요약과 전문을 같은 행에서 파생시킨다(설계서 4-6) — 두 목록을 따로 관리하면
                    현재 시안의 요약 11개 ↔ 전문 9조 같은 어긋남이 또 생긴다.

                    문안이 법률 검토 대기인 조항(단독 판매·샘플 제공)은 `fullTitle`·`fullBody`가 `null`이다.
                    전문 모달은 이 두 값이 있는 조항만 그린다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "시행중인 표준 조항 버전 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractClausesResponse> getClauses();

    @Operation(
            summary = "계약 상세",
            description = """
                    상세 화면 13종(B2a·B3c·B3d·B4·B4a·B4b·B4c·B5·B5a·B5b·B6·B7·B8)이 **이 응답 하나**를 쓴다.
                    화면 분기는 FE가 값으로 고른다(설계서 4-5).

                    **권한:** SELLER (본인 브랜드 계약만)

                    - `permissions` — 버튼 노출 판정을 서버가 내려준다. FE가 상태 × 서명 조합 × 지급 여부 판정을
                      복제하면 서버 허용 집합과 어긋나는 버튼이 생긴다.
                    - `signature.asOf` — 서명 값은 어드민이 모두싸인에서 손으로 옮겨 적은 것이므로 「언제 기준」인지를 함께 내린다.
                    - `settlement.pgFeeRate` — 자문 회신 전이라 **항상 null**이다. 0으로 내리면 화면이 「0%」로 읽어
                      수수료가 없다는 뜻이 되어버린다.
                    - `fixedFee.obligationAlive` — 종결 3종이면 false. 화면이 「지급 의무 소멸」로 바뀌는 근거다.
                    - `version` — 임시저장(PUT)에 그대로 되돌려 보낸다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "해당 브랜드의 계약이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 계약",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractDetailResponse> getContract(@PathVariable Long contractId);

    @Operation(
            summary = "계약 문서 다운로드",
            description = """
                    계약 문서의 다운로드 URL을 돌려준다. 상태에 따라 받을 수 있는 종류가 다르다.

                    **권한:** SELLER (본인 브랜드 계약만)

                    | 상태 | 받을 수 있는 문서 |
                    |---|---|
                    | `REVIEW_PENDING` · `SIGNING` · `CONCLUSION_PENDING` | `GENERATED_DRAFT` 계약서 생성본 — 운영자가 내려받는 것과 같은 파일 |
                    | `CONCLUDED` | `SIGNED_PDF` 서명 완료 계약서 · `AUDIT_TRAIL` 감사 추적 인증서 |
                    | 그 외 | 없음 |

                    생성본은 운영자가 한 번이라도 내려받아 만들어진 뒤부터 받을 수 있다 — 그 전이면 404다.
                    요청 취소 후 재제출한 경우 이전 제출본의 생성본은 내려가지 않는다.

                    체결 문서는 어드민이 모두싸인에서 받아 업로드한다. 교체·삭제 경로는 만들지 않는다 —
                    서명 원본이 바뀌면 계약의 증거가 사라진다(§28-6).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "지금 상태에서 받을 수 없는 종류이거나 아직 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractDocumentDownloadResponse> getDocument(@PathVariable Long contractId,
                                                                 @PathVariable ContractDocumentType type);

    @Operation(
            summary = "계약 초안 생성",
            description = """
                    **빈 초안**을 만든다(B1). 계약 조건은 여기서 받지 않고 임시저장(PUT)으로 채운다.

                    **권한:** SELLER

                    스레드 경유 진입이면 `creatorId`를 함께 보낸다 — 상대가 **고정**되고 이후 PUT에서
                    상대 변경이 거부된다(§25-5-1 "자동 지정 · 변경 불가").

                    계약번호는 여기서 부여하지 않는다. 초안마다 번호를 태우면 **버려진 초안이 번호를 먹어**
                    일련번호에 구멍이 생긴다 — 번호는 검토 요청 시점에 붙는다(설계서 1-7).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "연결됨 상태가 아닌 상대",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractCreateResponse> createContract(@Valid @RequestBody ContractCreateRequest request);

    @Operation(
            summary = "임시저장",
            description = """
                    계약 조건을 저장한다 — **전체 교체(PUT 시맨틱)**다(설계서 4-2).

                    **권한:** SELLER · **허용 상태:** `DRAFT` · `REVIEW_REJECTED`

                    - 항목 배열은 통째로 교체되고 `sortOrder`는 배열 index로 채워진다. 빈 배열이면 전부 삭제다.
                    - **형식만** 본다 — 길이·숫자 범위·enum·10원 단위. 필수 미입력은 막지 않는다.
                      그러지 않으면 "검토 요청 전까지 임시저장할 수 있습니다"가 거짓이 된다(설계서 0-3).
                    - **상품을 바꾼 행은 공구가·리워드율·최소 물량이 초기화된다**(§25-5-3). 서버도 집행하므로
                      기존 행은 `contractItemId`를 그대로 돌려보내야 한다 — 식별자가 없으면 중간 행 삭제로
                      밀려 올라온 행을 상품 변경으로 오인한다.
                    - 검토 요청 이후에는 409(`CONTRACT_EDIT_LOCKED`)다. 편집 잠금은 화면 상태가 아니라 서버 권한이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공 — 저장된 상세를 그대로 돌려준다"),
            @ApiResponse(responseCode = "400", description = "형식 위반(10원 단위 등) · 연결되지 않은 상대 · 남의 상품",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "편집 잠금 · 다른 곳에서 먼저 저장됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"CONTRACT_MODIFIED_ELSEWHERE","message":"다른 곳에서 먼저 저장되었습니다."}""")))
    })
    ResponseEntity<ContractDetailResponse> updateContract(@PathVariable Long contractId,
                                                          @Valid @RequestBody ContractUpdateRequest request);

    @Operation(
            summary = "초안 삭제",
            description = """
                    **작성중 초안만** 지운다.

                    **권한:** SELLER · **허용 상태:** `DRAFT`

                    검토 요청 이후의 계약은 지울 수 없다 — 되돌림은 취소이지 삭제가 아니다(설계서 4-2).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "409", description = "작성중이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> deleteContract(@PathVariable Long contractId);

    @Operation(
            summary = "검증 (상태 불변)",
            description = """
                    상태를 바꾸지 않고 하드 H1~H8 · 경고 W1~W6 판정만 돌려준다(설계서 2-4).

                    **권한:** SELLER

                    쓰는 곳 둘 — ① 검토 요청 확인 모달(C3)이 열거할 경고를 받는다 ② 재작성 직후 위반 행을 표시한다.

                    `hardViolations[].kind`를 반드시 구분해 쓸 것:
                    - `REQUIRED`(미입력) → **에러 문구 없이** [검토 요청] 버튼만 비활성
                    - `RULE`(규칙 위반) → 해당 필드에 에러 문구

                    이 구분 없이 메시지를 전부 뿌리면 빈 폼에 빨간 문구가 8개 뜬다(§25-6 · 설계서 2-5).
                    """)
    @ApiResponses(@ApiResponse(responseCode = "200", description = "판정 성공"))
    ResponseEntity<ContractValidationResponse> validateContract(@PathVariable Long contractId);

    @Operation(
            summary = "검토 요청",
            description = """
                    `DRAFT`·`REVIEW_REJECTED` → `REVIEW_PENDING`.

                    **권한:** SELLER

                    한 트랜잭션이 하는 일: 상태 판정 → 스냅샷 정가 갱신 → 하드 전량 재검증 → 경고 판정·대조 →
                    **계약번호 부여** → 조항 버전 고정 → 상태 전이 → 이력 append → 어드민 통지.

                    - 하드 위반이 있으면 400이며 바디에 `hardViolations[]`가 함께 실린다.
                    - `acknowledgedWarnings`가 서버 판정 경고 집합과 **다르면** 409(`CONTRACT_WARNING_MISMATCH`)다.
                      FE가 보낸 목록을 그대로 믿지 않는다 — 모달을 본 뒤 다른 탭에서 값을 고쳤을 수 있다.
                    - 이 요청 이후 편집이 잠긴다. 되돌리려면 `/review-request/cancel`(요청 취소)을 쓴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 성공"),
            @ApiResponse(responseCode = "400", description = "하드 검증 실패 — hardViolations[] 포함",
                    content = @Content(examples = @ExampleObject(value = """
                            {"code":"CONTRACT_VALIDATION_FAILED",
                             "message":"계약 내용을 다시 확인해 주세요.",
                             "hardViolations":[
                               {"code":"H1","kind":"RULE","field":"items[0].groupBuyPrice",
                                "message":"공구가는 정가 이하여야 합니다."}]}"""))),
            @ApiResponse(responseCode = "409", description = "편집 잠금 · 상태 경합 · 경고 확인 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractDetailResponse> requestReview(@PathVariable Long contractId,
                                                         @RequestBody(required = false)
                                                         ContractReviewRequestRequest request);

    @Operation(
            summary = "검토 요청 취소",
            description = """
                    `REVIEW_PENDING` → `DRAFT`.

                    **권한:** SELLER · **허용 상태:** `REVIEW_PENDING` **만**

                    **종결이 아니다.** 사유를 받지 않고 상대에게 통지하지 않는다 — 상대에게는 아직 아무것도 가지 않았다.
                    어드민에는 통지한다(이미 검토를 시작했을 수 있다).

                    브랜드에게 **계약 취소(종결)는 없다** — 발송 전의 되돌림은 이 API뿐이고,
                    서명 요청이 나간 뒤의 취소는 운영자만 한다(어드민 `/cancel`). 서명 요청이 나간 뒤로는 이 API가 409다.

                    계약번호는 반납하지 않는다 — 이미 어드민 큐·통지에 나간 번호를 재사용하면
                    같은 번호가 서로 다른 계약을 가리키게 된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "409", description = "검토 대기 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractDetailResponse> cancelReviewRequest(@PathVariable Long contractId);

    // 요청이 가는 어드민 스레드가 아직 없어 Swagger에서 숨긴다 — 어드민 스레드 구현 후 제거.
    @Hidden
    @Operation(
            summary = "서명 안내 다시 받기",
            description = """
                    어드민의 「재발송 요청」 큐에 요청 행을 남긴다. **상태는 변하지 않는다.**

                    **권한:** SELLER · **허용 상태:** `SIGNING`

                    요청은 발송이 아니다 — 실제 재발송은 어드민이 모두싸인에서 한다.

                    횟수 제한은 정책 미정(§28-8 D #7)이라 만들지 않되 연타는 막는다 —
                    미처리 요청이 이미 있으면 새 행 대신 그 요청을 `alreadyRequested: true`로 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 접수(또는 기존 미처리 요청 반환)"),
            @ApiResponse(responseCode = "409", description = "서명 진행중이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractResendRequestResponse> requestResend(@PathVariable Long contractId);

    @Operation(
            summary = "고정 지급비 지급 완료 기록",
            description = """
                    브랜드가 인플루언서에게 직접 지급한 사실을 기록한다(B5a). **상태는 변하지 않는다.**

                    **권한:** SELLER · **허용 조건:** `CONCLUDED` + 고정 지급비 > 0 + 아직 미기록

                    되돌리는 API를 만들지 않는다 — 시안에 취소 버튼이 없고 정정 경로가 미결이다(설계서 미결 #4).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "기록 성공"),
            @ApiResponse(responseCode = "409", description = "체결완료가 아니거나 이미 기록됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractDetailResponse> recordFixedFeePayment(@PathVariable Long contractId);

    @Operation(
            summary = "이 조건으로 새 계약 작성 (재작성)",
            description = """
                    같은 조건의 **새 초안**을 만든다(§26-5). 복사가 아니라 새 행 생성이라
                    원 계약은 그대로 종결 상태로 남는다.

                    **권한:** SELLER · **허용 상태:** `CONCLUDED`·`DECLINED`·`EXPIRED`·`CANCELED`

                    | 구분 | 처리 |
                    |---|---|
                    | 복사 | 계약 상대 · 공구명 · 상품 항목 4열 · 고정 지급비 · 콘텐츠 의무 · 비고 |
                    | 비운다 | 공구 시작·종료 일시 · 게시 완료 기한 |
                    | 다시 받는다 | 고정 지급비 고지 확인 |
                    | 승계 안 함 | 서명 이력 · 기한 · 계약번호 · 상태 · 경고 · 문서 |

                    응답에 **복사 직후 재검증 결과**가 함께 실린다 — 상품이 미진열로 바뀌었거나 정가가 내려갔을 수 있다.
                    이때 스냅샷을 현재 상품 값으로 갱신하고 위반을 알린다. 옛 정가를 들고 있으면 H1이 통과해버린다.

                    진행 중 계약의 복제는 409다 — 같은 조건의 계약 2건이 동시에 검토 큐에 올라가는 상황을
                    기획이 다룬 적 없다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "409", description = "복제할 수 없는 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ContractDuplicateResponse> duplicateContract(@PathVariable Long contractId);
}
