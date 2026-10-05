package showroomz.api.seller.claim.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.claim.dto.SellerClaimBatchResponse;
import showroomz.api.seller.claim.dto.SellerClaimDetailResponse;
import showroomz.api.seller.claim.dto.SellerClaimListItem;
import showroomz.api.seller.claim.dto.SellerClaimReceiveRequest;
import showroomz.api.seller.claim.dto.SellerClaimRejectRequest;
import showroomz.api.seller.claim.dto.SellerClaimReshipDto;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimTab;
import showroomz.domain.order.type.ClaimType;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDate;
import java.util.Set;

@Tag(name = "Seller - Claim", description = "파트너센터 반품·교환 관리 API — 소비자가 신청한 반품·교환을 회수 · 검수 · 재발송까지 따라간다(§35).")
public interface SellerClaimControllerDocs {

    @Operation(
            summary = "반품·교환 목록",
            description = """
                    탭별 반품·교환 목록. 행은 **클레임(주문 항목 단위)** 이고, 한 박스로 같이 온 클레임은
                    `collection.size > 1` 로 묶어 그린다(「`collection.leadClaimNumber` 외 N건」). 같은 묶음이 인접하도록 정렬된다.

                    **권한:** SELLER (CREATOR 는 403)

                    **탭(`tab`)** — 생략 시 `COLLECT_WAIT`. 탭이 조건과 기본 정렬을 소유한다.
                    - `ALL` 전체(신청 최신순) · `COLLECT_WAIT` 회수 대기(신청 오래된순) · `COLLECTING` 회수 중
                    - `INSPECTION` 입고·검수(도착 오래된순) · `RESHIP` 재발송 대기 · `REJECT_HOLD` 거절 보류
                    - `DONE` 완료(환불 대기 · 재발송 중 · 종결 — 최신순)

                    **필터**
                    - `types` 유형(복수) · `reason` 사유
                    - `from` · `to` — **신청일시** 기준. 생략 시 최근 30일 · 최대 1년
                    - `keyword` — 접수번호(`CLM-3021`) 또는 주문번호. 한 입력으로 둘 다 받는다

                    **행의 값**
                    - 라벨 · 버튼 가능 여부(`actions`) · 기한 초과(`overdue`) · 보관 기한(`storage`)은 서버가 계산해 내린다
                    - `shipLabel` 은 재발송·거절 보류 단계에서 **실제로 보낼(보관 중인) 물건**이다 — 교환 재발송은 새 옵션,
                      거절은 원래 옵션
                    - `storage` 는 거절 보류만. `storageDueAt` 은 미결제 고지가 2회 쌓여야 생긴다(최종 고지일 + 3개월)
                    - `orderItems` 는 그 하위주문의 항목 전체다(행 확장) — 신청하지 않은 항목은 `claimedQuantity = 0`
                    - 소비자 연락처는 목록에 없다 — 상세에 마스킹해서만 내린다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "ORDER_SEARCH_RANGE_EXCEEDED — 기간 1년 초과 · "
                    + "INVALID_INPUT — 시작일 > 종료일 · `size` 범위 밖 · 정의되지 않은 enum 값",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SELLER 가 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<SellerClaimListItem>> getClaims(
            @Parameter(description = "탭 — 생략 시 COLLECT_WAIT", example = "COLLECT_WAIT")
            @RequestParam(value = "tab", required = false) ClaimTab tab,
            @Parameter(description = "유형 — 복수 선택. 생략 시 전체", example = "RETURN")
            @RequestParam(value = "types", required = false) Set<ClaimType> types,
            @Parameter(description = "사유", example = "CHANGE_OF_MIND")
            @RequestParam(value = "reason", required = false) ClaimReason reason,
            @Parameter(description = "신청일 시작 — 생략 시 종료일 30일 전", example = "2026-09-05")
            @RequestParam(value = "from", required = false) LocalDate from,
            @Parameter(description = "신청일 종료 — 생략 시 오늘", example = "2026-10-05")
            @RequestParam(value = "to", required = false) LocalDate to,
            @Parameter(description = "접수번호(CLM-3021) 또는 주문번호", example = "CLM-3021")
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "반품·교환 요약 (KPI · 탭 카운트)",
            description = """
                    KPI 4칸 + 탭 카운트 7종 + 유형 카운트를 한 응답으로 내린다.

                    **권한:** SELLER

                    - 전부 **검색 조건·기간과 무관한 전체 기준**이다 — 목록에 검색어를 넣어도 이 숫자는 변하지 않는다
                    - `tabCounts.ALL` 은 여섯 탭의 합이고, 대탭 「반품·교환 관리 N」의 숫자다
                    - `kpi.overdue` = 회수 대기 방치(신청 + 2영업일 경과) + 검수 기한 경과. 0이면 경고 톤을 뺀다
                    - 재발송 배송비 결제를 기다리는 교환 요청(아직 접수 전)은 어디에도 세지 않는다
                    """)
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<SellerClaimSummaryResponse> getSummary();

    @Operation(
            summary = "반품·교환 상세",
            description = """
                    상세 모달. 목록 행과 같은 값은 `summary` 에 있고, 상세에서만 보이는 것을 더한다. 단계에 해당하지 않는 블록은 null 이다.

                    **권한:** SELLER (내 마켓 클레임만 — 남의 마켓 것은 404)

                    - `consumerPhone` 은 마스킹된 값이다(`010-****-4412`). **재발송 수취 정보(원문)는 여기 없다** — 재발송 목록
                      엑셀에만 나간다
                    - `refund`(반품만) — 환불 예정액은 **요청(박스) 단위**다. `itemAmount`(이 항목의 상품 금액)와
                      `requestDeduction`(요청의 배송비 차감 — 요청당 한 번) · `requestExpectedAmount`(요청 예정액)를 따로 내린다.
                      묶음의 항목마다 같은 차감을 반복해 그리지 않는다
                    - `result`(완료 단계만) — 교환 완료면 `confirmDueAt`(구매확정 재시작 예정), 거절 종결이면
                      `rejectionEnd`(`RETURNED` 반송 완료 · `DISPOSED` 폐기)
                    - `notices`(거절 보류만) — 미결제 고지 회차. 보관 기한은 `summary.storage`
                    - `history` — 처리 이력 최신순
                    - `actions` — 버튼 노출의 정본은 서버다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 마켓 것이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimDetailResponse> getClaim(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId);

    @Operation(
            summary = "입고 확인 (다건)",
            description = """
                    회수된 상품이 도착했음을 확인한다. **검수 기한(입고 확인 + 2영업일)이 이 순간 발급된다.**

                    **권한:** SELLER

                    - 다건 · **행 단위 부분 성공** — 처리되지 않은 건만 `skipped` 에 사유와 함께 돌려주고 나머지는 진행한다
                    - 대상은 「입고 확인 전」(추적상 도착) 건이다. 택배 추적이 꺼져 있는 동안에는 도착이 감지되지 않으므로
                      「회수 중」 건도 받는다(목록의 `actions.canConfirmReceipt` 가 정본)
                    - 한 박스로 같이 온 건 중 일부만 먼저 확인해도 된다
                    - 내 마켓 것이 아닌 건과 단계가 맞지 않는 건은 같은 사유(`CLAIM_STATE_CHANGED`)로 제외된다
                    - 되돌리기는 없다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 결과 — 전부 제외돼도 200"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `claimIds` 비었음 · 500건 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimBatchResponse> receive(@Valid @RequestBody SellerClaimReceiveRequest request);

    @Operation(
            summary = "검수 통과 (단건)",
            description = """
                    「검수 대기」 건을 통과시킨다. 응답은 갱신된 상세다.

                    **권한:** SELLER

                    - **반품** → 「환불 대기」. 그 항목의 반품 수량이 이 순간 반영된다(전량이면 항목이 반품으로 종결).
                      **환불은 브랜드가 실행하지 않는다** — 같은 박스의 판정이 전부 끝나면 환불 큐에 오르고 운영자가 집행한다
                    - **교환** → 「재발송 대기」. 재발송 송장을 등록하면 된다
                    - 환불 예정액은 **요청(박스) 단위**라 같은 박스에 판정이 남은 건이 있으면 아직 확정되지 않는다
                    - 되돌리기는 없다. 통과와 거절이 동시에 들어오면 하나만 성공한다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "통과 — 갱신된 상세"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 클레임이거나 내 마켓 것이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 검수 대기가 아님(이미 판정됨 포함)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimDetailResponse> passInspection(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId);

    @Operation(
            summary = "검수 거절 (단건)",
            description = """
                    「검수 대기」 건을 거절한다. **제출 = 즉시 확정이고 되돌릴 수 없다.** 응답은 갱신된 상세다.

                    **권한:** SELLER

                    - `reasonCode` · `detail` · `evidenceImageUrls`(1~5장)가 **전부 필수**다 — 하나라도 빠지면 400.
                      설명과 증빙은 **소비자에게 그대로 전달된다**
                    - 거절된 상품은 소비자에게 돌려보낸다. 그 재발송 배송비는 소비자 부담이다
                      - 같은 박스에 통과된 반품이 있으면 그 환불액에서 빼고 → 바로 「재발송 대기」
                      - 교환 요청 때 낸 배송비가 있으면 그것으로 충당하고 → 바로 「재발송 대기」
                      - 둘 다 아니면 소비자 결제를 기다린다 → 「거절 보류」
                    - 같은 박스에 판정이 남은 건이 있으면 위 정산은 그 판정이 끝날 때 정해진다(그때까지 「거절 보류」)
                    - **거절은 구매확정 보류를 푼다** — 배송완료 후 7일이 이미 지났으면 이 순간 구매확정된다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "CLAIM_REJECT_INCOMPLETE — 사유 · 설명 · 증빙(1~5장) 누락",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 검수 대기가 아님(이미 판정됨 포함)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimDetailResponse> rejectInspection(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId,
            @RequestBody SellerClaimRejectRequest request);

    @Operation(
            summary = "재발송 송장 등록 (다건)",
            description = """
                    「재발송 대기」 건에 송장을 등록한다. 교환 새 상품이든 거절된 상품의 반송이든 같은 API 다
                    (`reshipReason` 으로 구분되고, 보낼 물건은 목록의 `shipLabel` 이다).

                    **권한:** SELLER

                    - **등록하면 완료가 아니라 「재발송 중」으로 넘어간다.** 도착이 확인되면 완료된다(교환 완료 / 거절 종결)
                    - 다건 · **행 단위 부분 성공** — 처리되지 않은 행만 `skipped` 에 사유와 함께 돌려준다
                    - 송장번호가 빈 행은 조용히 건너뛴다. 숫자 외 문자는 서버가 지운다
                    - **전역 중복 검사** — 배송 중인 주문의 송장, 재발송 중인 다른 건의 송장과 겹치면 그 행만 제외한다.
                      내 마켓 건이면 겹치는 번호(주문번호 / 접수번호)를 알려 주고, 남의 건이면 번호를 숨긴다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 결과 — 전부 제외돼도 200. 제외 사유: "
                    + "INVOICE_FORMAT_INVALID · INVOICE_DUPLICATE · CLAIM_STATE_CHANGED(재발송 대기가 아님 · 내 마켓 것이 아님)"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `items` 비었음 · 500건 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimBatchResponse> registerReshipments(
            @Valid @RequestBody SellerClaimReshipDto.RegisterRequest request);

    @Operation(
            summary = "재발송 송장 수정",
            description = """
                    「재발송 중」 건의 송장을 고친다. 응답은 갱신된 상세다.

                    **권한:** SELLER

                    - 등록 시각은 유지되고 추적 값만 초기화된다. 처리 이력에 「구 → 신」이 남는다
                    - 형식 · 전역 중복 검사는 등록과 같다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "INVOICE_FORMAT_INVALID · INVALID_INPUT",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVOICE_DUPLICATE — 다른 건에 이미 등록된 번호 / "
                    + "CLAIM_STATE_CHANGED — 재발송 중이 아님",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimDetailResponse> updateReshipment(
            @Parameter(description = "클레임 id", example = "3021") @PathVariable Long claimId,
            @Valid @RequestBody SellerClaimReshipDto.UpdateRequest request);

    @Operation(
            summary = "재발송 목록 다운로드 (xlsx)",
            description = """
                    「재발송 대기」 건의 수취 정보를 엑셀로 내려받는다. **재발송 수취인의 연락처·주소가 나가는 유일한 경로**이고
                    다운로드는 반출 이력으로 기록된다.

                    **권한:** SELLER

                    - `claimIds` 를 보내면 선택 건(재발송 대기가 아닌 건은 조용히 빠진다), 비우면 내 마켓의 재발송 대기 전체
                    - `columns` 선택 순서 = 엑셀 좌→우 열 순서. **선택한 열 뒤에 빈 「택배사」 「송장번호」 2열이 항상 붙는다** —
                      이 파일을 채워 업로드(`/reshipments/parse`)에 그대로 쓴다
                    - 「상품명」 「옵션」은 **보낼 물건**이다 — 교환 재발송은 새 옵션, 거절 반송은 원래 옵션
                    - 수취지는 재발송 수취지다. 교환은 소비자가 검수 전까지 바꿀 수 있지만 재발송 대기부터는 잠긴다 —
                      이 목록에 오른 주소는 바뀌지 않는다
                    - `saveAsDefault` — 이 컬럼 구성을 기본값으로 저장
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "xlsx 바이너리(Content-Disposition attachment)",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "400", description = "CLAIM_EXPORT_EMPTY — 내려받을 재발송 대기 건 없음 · "
                    + "INVALID_INPUT — `columns` 비었음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<byte[]> exportReshipments(@Valid @RequestBody SellerClaimReshipDto.ExportRequest request);

    @Operation(summary = "재발송 목록 컬럼 기본값 조회",
            description = "저장한 구성이 없으면 기본 7종(접수번호 · 수취인 · 연락처 · 우편번호 · 주소 · 상품명 · 옵션)을 내린다. "
                    + "`available` 은 고를 수 있는 컬럼 전체다.\n\n**권한:** SELLER")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<SellerClaimReshipDto.TemplateResponse> getReshipTemplate();

    @Operation(summary = "재발송 목록 컬럼 기본값 저장",
            description = "컬럼 구성과 순서를 저장한다. 중복은 첫 위치만 남는다.\n\n**권한:** SELLER")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `columns` 비었음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimReshipDto.TemplateResponse> updateReshipTemplate(
            @Valid @RequestBody SellerClaimReshipDto.TemplateUpdateRequest request);

    @Operation(
            summary = "재발송 송장 일괄 업로드 검증 (xlsx)",
            description = """
                    채운 재발송 목록 파일을 올려 행별로 분류한다. **상태를 바꾸지 않는다** — 이 응답으로 화면의 셀만 채우고
                    (「N건 목록에 채우기」), 확정은 `POST /reshipments` 다.

                    **권한:** SELLER

                    - 열은 **머리글로 찾는다** — 「접수번호」 「택배사」 「송장번호」. 순서·다른 열은 상관없다. 셋 중 하나라도 없으면 400
                    - 택배사는 한글명 · 코드 모두 인식한다. 칸이 비었으면 `carrier = null` 로 내린다(화면에서 고른 뒤 확정)
                    - 숫자 셀은 자릿수 그대로 읽는다(지수 표기로 깨지지 않는다)
                    - 없는 접수번호와 **남의 마켓 접수번호는 같은 사유**(`CLAIM_NOT_FOUND`)다
                    - 같은 접수번호가 반복되면 첫 행만 정상이다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분류 결과"),
            @ApiResponse(responseCode = "400", description = "CLAIM_UPLOAD_HEADER_MISSING — 필수 열 없음 · "
                    + "SHIPMENT_FILE_INVALID — xlsx 가 아니거나 손상 · SHIPMENT_FILE_TOO_MANY_ROWS — 1,000행 초과",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerClaimReshipDto.ParseResponse> parseReshipments(
            @Parameter(description = "xlsx 파일") @RequestPart("file") MultipartFile file);
}
