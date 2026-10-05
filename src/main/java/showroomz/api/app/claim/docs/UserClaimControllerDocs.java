package showroomz.api.app.claim.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.claim.dto.UserClaimDto;
import showroomz.domain.order.type.ClaimType;

@Tag(name = "User - Claim", description = "반품 · 교환 요청 · 상세 · 회수 송장 · 철회 (C10-3 · C10-5)")
public interface UserClaimControllerDocs {

    @Operation(
            summary = "반품·교환 요청 폼 (C10-3 진입)",
            description = """
                    요청 화면을 그리는 데이터. 진입한 항목이 든 **하위 주문의 신청 가능 항목 전부**를 내린다 — 진입 항목은
                    `preselected` 로 체크된 채 그린다. 사유 · 택배사 · 금액은 여기서 받은 값만 쓴다(앱에 목록을 박지 않는다).

                    **지금은 `type=RETURN` 만 된다.** `EXCHANGE` 는 400 이다(교환은 다음 배포).

                    - `items[].claimableQuantity` — 요청하면 이 수량 **전부**가 접수된다(수량 선택 없음)
                    - `reasons[]` — `feeBearer` 가 `CONSUMER` 면 배송비가 발생하고 반송 택배비는 선불, `SELLER` 면 0원 · 착불.
                      `detailRequired` 면 상세 내용 필수, `photoAllowed` 면 사진 블록을 연다
                    - `fees.consumerFault` — 고객 귀책 반품이면 환불액에서 빼는 **최초 배송비**. 무료배송으로 받은 주문만이고
                      배송비를 내고 받은 주문은 0 이다. 주문할 때의 배송비라 브랜드가 그 뒤 설정을 바꿔도 변하지 않는다
                    - `returnTo` — 브랜드 반품 수취 주소(원문). 송장에 적는 값이다
                    - 반송 택배비는 고객이 택배사에 직접 낸다 — 앱이 받지 않는다(`courierPayment` 는 안내용)

                    **권한:** USER (본인 주문만)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `type=EXCHANGE`(아직 지원하지 않음) · 파라미터 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_PRODUCT_NOT_FOUND — 없는 항목이거나 남의 주문",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_NOT_ELIGIBLE — 배송완료가 아니거나(구매확정 뒤 포함) "
                    + "신청할 수량이 남지 않음(진행 중 · 이미 반려됨)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserClaimDto.FormResponse> getForm(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "진입한 주문 항목 ID", example = "1501") @RequestParam("orderProductId") Long orderProductId,
            @Parameter(description = "RETURN | EXCHANGE", example = "RETURN") @RequestParam("type") ClaimType type);

    @Operation(
            summary = "반품·교환 요청 (C10-3 [요청하기])",
            description = """
                    같은 하위 주문의 항목 여러 개를 **한 번에(한 박스로)** 요청한다. 사유 · 회수 송장은 요청에 하나다.
                    접수되면 브랜드 수락 없이 바로 진행된다 — 응답의 `claimIds` 중 하나로 상세를 열어 접수 화면을 그린다.

                    **지금은 `type=RETURN` 만 된다.**

                    - `invoice` 를 같이 내면 회수 중(`COLLECTING`)으로 시작한다. `null` 이면 「나중에 입력하기」 —
                      회수 대기(`REQUESTED`)로 접수되고 **7일 안에** 송장을 넣지 않으면 요청이 자동 취소된다
                    - `reasonDetail` — `detailRequired` 사유면 필수, 250자까지
                    - `imageUrls` — 이미지 업로드 API 가 준 URL. **브랜드 귀책 사유에서만** 받고 그 밖에는 무시한다. 10장까지
                    - `expectedFee` — 폼의 `fees` 에서 고른 값. 서버 계산과 다르면 409 `CLAIM_AMOUNT_CHANGED`(폼을 다시 받는다)
                    - `idempotencyKey` — 같은 키의 재요청은 새로 만들지 않고 기존 요청을 돌려준다
                    - 구매확정은 이 요청이 끝날 때까지 멈춘다(같은 하위 주문의 다른 항목 포함)

                    **권한:** USER (본인 주문만)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "접수"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — 항목 없음 · 고를 수 없는 사유·택배사 · 글자 수·사진 수 초과 · "
                    + "교환(아직 지원하지 않음) / CLAIM_REASON_DETAIL_REQUIRED — 상세 내용 누락 / INVOICE_FORMAT_INVALID — 송장번호 형식",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_GROUP_NOT_FOUND — 없는 주문이거나 남의 주문 / "
                    + "ORDER_PRODUCT_NOT_FOUND — 그 하위 주문의 항목이 아님(취소·전량 반품된 항목 포함)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_NOT_ELIGIBLE — 배송완료가 아님 / CLAIM_QUANTITY_EXCEEDED — "
                    + "신청할 수량이 남지 않음 / CLAIM_AMOUNT_CHANGED — 배송비가 폼과 다름",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserClaimDto.CreateResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody UserClaimDto.CreateRequest request);

    @Operation(
            summary = "반품·교환 상세 (C10-5 · 접수 화면)",
            description = """
                    요청(박스) 한 건의 상세. 경로의 `claimId` 는 진입한 항목이고, 응답의 `items` 에는 **같은 요청의 항목 전부**가
                    실린다(철회·자동 취소된 항목은 빠진다 — 진입한 항목은 취소됐어도 실린다).

                    - `items[].statusLabel` · `statusSub` · `actions` 는 서버가 내린다. `statusSubTone = ACTIVE`(로즈)는 고객이
                      할 일이 있을 때만이다 — 회수 송장 미등록 · 재발송 배송비 결제 필요
                    - `items[].rejection` — 검수에서 반려된 항목만. 브랜드가 적은 설명과 증빙 사진을 그대로 내린다
                    - `guide.visible` — 아직 보내기 전이거나 회수 중인 항목이 있을 때 「유의해 주세요」 박스를 그린다
                    - `info.collectionInvoice` 가 null 이면 `invoiceDueDate` 까지 등록해야 한다
                    - `info.pickupFrom` 은 마스킹, `info.returnTo`(브랜드 반품센터)는 원문이다
                    - `refund`(반품) — 요청 단위. `confirmed` 가 거짓이면 「환불 예정 금액」, 참이면 「환불 금액」.
                      `rejectedAmount` 는 반려돼 환불에서 빠진 상품 금액이다(없으면 null) · 전체 반려면 `amount = 0`
                    - `reshipFee` — 반려된 항목이 있을 때만. `WAITING` 이면 같은 박스의 나머지 검수가 끝나지 않아 결제 여부가
                      아직 정해지지 않은 것이다(블록을 열지 않는다)
                    - 지금 내려가는 버튼은 `WITHDRAW` · `REGISTER_COLLECTION_INVOICE` · `INQUIRY` 다. 회수 조회 · 배송 조회 버튼은
                      그 API 가 생기는 배포에서 켠다

                    **권한:** USER (본인 요청만 — 남의 요청은 404)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND — 없는 요청이거나 남의 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserClaimDto.DetailResponse> getDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "클레임 ID", example = "3021") @PathVariable("claimId") Long claimId);

    @Operation(
            summary = "회수 송장 등록 · 수정 (C10-5)",
            description = """
                    요청(박스) 단위로 적용된다 — 같은 요청의 항목 전부가 같은 송장을 쓴다. 응답은 갱신된 상세다.

                    - **회수 대기** 항목이면 **등록** — 요청의 항목 전부가 회수 중으로 넘어간다. 등록 기한(접수 + 7일)이 지났으면
                      요청이 이미 자동 취소됐다 → 409 `CLAIM_STATE_CHANGED`
                    - **회수 중** 항목이면 **수정** — **택배사 조회에 아직 잡히지 않았고** 등록 기한 전일 때만. 이미 스캔된 송장은
                      맞는 송장이라 고칠 수 없다 → 409 `CLAIM_INVOICE_NOT_EDITABLE`
                    - 송장번호의 하이픈·공백은 서버가 지운다. 택배사는 폼의 `carriers` 중 하나여야 한다

                    **권한:** USER (본인 요청만)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록·수정 성공 — 갱신된 상세"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — 고를 수 없는 택배사 / INVOICE_FORMAT_INVALID — 송장번호 형식",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_STATE_CHANGED — 회수 대기·회수 중이 아님 · 등록 기한 경과 / "
                    + "CLAIM_INVOICE_NOT_EDITABLE — 이미 조회되는 송장 · 수정 기한 경과",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserClaimDto.DetailResponse> putCollectionInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "클레임 ID", example = "3021") @PathVariable("claimId") Long claimId,
            @Valid @RequestBody UserClaimDto.InvoiceRequest request);

    @Operation(
            summary = "요청 철회 (C10-5 [요청 철회])",
            description = """
                    **회수 송장을 넣기 전(회수 대기)까지만** 철회할 수 있다. 항목 단위다 — 한 요청의 두 항목 중 하나만 철회할 수 있다.
                    철회하면 그 항목은 배송완료로 돌아가고 다시 요청할 수 있다. 앱은 주문 상세로 돌아간다.

                    **권한:** USER (본인 요청만)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 성공"),
            @ApiResponse(responseCode = "404", description = "CLAIM_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "CLAIM_WITHDRAW_NOT_ALLOWED — 이미 회수가 시작됐거나 끝난 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserClaimDto.WithdrawResponse> withdraw(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "클레임 ID", example = "3021") @PathVariable("claimId") Long claimId);
}
