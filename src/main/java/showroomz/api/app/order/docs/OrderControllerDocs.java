package showroomz.api.app.order.docs;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.global.dto.PageResponse;

@Tag(name = "User - Order", description = "주문서 · 주문 생성 · 주문 내역 · 주문 상세 · 취소 (C9 · C10)")
public interface OrderControllerDocs {

    @Operation(
            summary = "주문서 조회 (C9 진입)",
            description = "선택한 장바구니 항목(`cartItemIds`) 또는 바로 구매 한 줄(`direct`)로 주문서를 그린다. **저장하지 않는다.**\n\n" +
                    "**진입 두 갈래:** `cartItemIds` 와 `direct` 중 **하나만** 채운다(둘 다·둘 다 없음 → 400 `INVALID_INPUT`). " +
                    "바로 구매는 `direct.groupBuyId` 가 **필수**다 — 공구 귀속은 진입 경로가 정한다.\n\n" +
                    "**배송지:** `deliveryAddressId` 를 생략하면 기본 배송지. 기본 배송지도 없으면 `deliveryAddress: null` 로 내려간다 — " +
                    "에러가 아니다. 주문서는 열리고 CTA 만 잠근다.\n\n" +
                    "**살 수 없는 항목:** 마감·품절·계약에 없는 옵션이 섞여 있으면 400 `CART_ITEM_NOT_PURCHASABLE`(message 로 사유). " +
                    "조용히 빼지 않는다 — 금액이 이유 없이 줄면 안 된다.\n\n" +
                    "**금액:** `summary` 4줄(상품 금액·할인·배송비·총 결제 금액)은 장바구니 합계와 같은 식에서 나온다. " +
                    "가격은 공구 계약의 옵션 가격이고 배송비는 공구(쇼룸)별로 매긴다. `summary.totalAmount` 를 주문 생성의 " +
                    "`expectedTotalAmount` 로 되돌려 보내면 그 사이 가격·배송비가 바뀐 경우 409 로 알려 준다.\n\n" +
                    "**결제수단:** `paymentMethods` 는 지금 열려 있는 채널만 내린다 — 꺼진 간편결제는 목록에서 빠진다.\n\n" +
                    "**권한:** USER\n**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json",
            examples = {
                    @ExampleObject(name = "장바구니 진입", value = "{ \"cartItemIds\": [11, 12, 15] }"),
                    @ExampleObject(name = "바로 구매", value = "{ \"direct\": { \"variantId\": 301, \"quantity\": 1, \"groupBuyId\": 41 } }"),
                    @ExampleObject(name = "배송지 지정", value = "{ \"cartItemIds\": [11], \"deliveryAddressId\": 7 }")
            }))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문서",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.CheckoutResponse.class),
                            examples = @ExampleObject(name = "주문서 예시", value = "{\n" +
                                    "  \"deliveryAddress\": { \"id\": 7, \"recipientName\": \"김수민\", \"phoneNumber\": \"010-1234-5678\", \"zipCode\": \"06234\",\n" +
                                    "                       \"address\": \"서울 강남구 테헤란로 000\", \"detailAddress\": \"쇼룸타워 12층 1203호\", \"memo\": \"문 앞에 놓아주세요\", \"isDefault\": true },\n" +
                                    "  \"memoPresets\": [\"문 앞에 놓아주세요\", \"경비실에 맡겨주세요\", \"부재 시 연락해주세요\", \"배송 전 미리 연락해주세요\"],\n" +
                                    "  \"groups\": [ { \"marketId\": 3, \"marketName\": \"라보에이치\", \"groupBuyId\": 41, \"groupBuyNumber\": \"GB-20260901-003\",\n" +
                                    "      \"items\": [ { \"cartId\": 11, \"variantId\": 301, \"productId\": 120, \"productName\": \"시카 리페어 앰플 30ml 리필 2개 세트 기획\",\n" +
                                    "                   \"optionName\": \"용량: 30ml + 리필 2개\", \"thumbnailUrl\": \"https://cdn/…\", \"quantity\": 1,\n" +
                                    "                   \"price\": { \"regularPrice\": 38000, \"salePrice\": 24900, \"discountRate\": 34 } } ],\n" +
                                    "      \"shipping\": { \"productTotal\": 24900, \"deliveryFee\": 3000, \"freeShippingThreshold\": 50000, \"isFreeShipping\": false } } ],\n" +
                                    "  \"summary\": { \"productTotal\": 38000, \"discountTotal\": 13100, \"deliveryFeeTotal\": 3000, \"totalAmount\": 27900, \"itemCount\": 1 },\n" +
                                    "  \"paymentMethods\": { \"cardIssuers\": [\"SHINHAN\", \"SAMSUNG\", \"HYUNDAI\", \"KB\", \"LOTTE\", \"HANA\", \"BC\", \"NH\", \"WOORI\", \"KAKAOBANK\"],\n" +
                                    "                       \"easyPayProviders\": [\"KAKAOPAY\", \"NAVERPAY\", \"TOSSPAY\"] },\n" +
                                    "  \"ctaLabel\": \"27,900원 결제하기\"\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "입력 오류 · 살 수 없는 항목(CART_ITEM_NOT_PURCHASABLE) · 재고 부족(INSUFFICIENT_STOCK)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "남의 배송지(ADDRESS_ACCESS_DENIED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "장바구니 항목·옵션·공구·배송지 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderDto.CheckoutResponse> checkout(@AuthenticationPrincipal UserPrincipal principal,
                                                       @Valid @RequestBody OrderDto.CheckoutRequest request);

    @Operation(
            summary = "주문 생성 + 재고 예약 + 결제 준비",
            description = "주문을 만들고(결제 대기 · 30분) 재고를 차감(예약)한 뒤 포트원에 금액을 사전 등록하고 **결제창 재료**(`payment`)를 돌려준다.\n\n" +
                    "**앱은 `payment` 블록으로 포트원 RN SDK `<Payment request>` 를 만든다.** 금액을 앱이 조립하지 않는다. " +
                    "값은 그대로 쓰되 두 필드만 SDK 모양으로 중첩한다 — `cardCompany` → `card: { cardCompany }`, " +
                    "`easyPayProvider` → `easyPay: { easyPayProvider }`(null 인 쪽은 뺀다). `customer.email` 이 null 이면 키를 뺀다. " +
                    "`redirectUrl` 은 SDK 가 정하므로 내리지 않는다. 결제창이 끝나면 결과와 관계없이 `onComplete` 의 `paymentId` 로 " +
                    "`POST /v1/user/payments/{paymentId}/complete` 를 부른다.\n\n" +
                    "**멱등키:** `idempotencyKey` 는 주문 생성마다 새 UUID. 같은 키의 재요청(더블 탭·사전 등록 실패 후 재시도)은 " +
                    "새 주문을 만들지 않고 같은 주문으로 응답을 다시 조립한다. 그 주문이 만료·취소됐으면 409 `ORDER_ALREADY_CLOSED`.\n\n" +
                    "**금액 검증:** `expectedTotalAmount`(주문서의 `summary.totalAmount`)가 서버 계산과 다르면 409 `ORDER_AMOUNT_CHANGED` — " +
                    "주문서를 다시 그린다.\n\n" +
                    "**검증 순서:** 배송지 소유 → 항목 구매 가능·공구 귀속 → 수량 상한(99) → 금액 → 재고 차감 → 저장 → 커밋 → 사전 등록. " +
                    "사전 등록 실패는 주문을 롤백하지 않고 502 `PAYMENT_GATEWAY_ERROR` — 같은 키로 재요청하면 다시 시도한다. " +
                    "재고가 잡힌 채 결제되지 않은 주문은 30분 뒤 만료 스케줄러가 재고를 돌려놓는다.\n\n" +
                    "**앱 규칙:** `expiresAt` 5분 전부터 결제 CTA 를 잠근다(승인 중 만료 방지).\n\n" +
                    "**권한:** USER\n**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json",
            examples = {
                    @ExampleObject(name = "카드", value = "{\n  \"idempotencyKey\": \"5f1e2b3c-0a1d-4c2e-9b7f-1234567890ab\",\n" +
                            "  \"cartItemIds\": [11, 12, 15],\n  \"deliveryAddressId\": 7,\n  \"deliveryMemo\": \"문 앞에 놓아주세요\",\n" +
                            "  \"payment\": { \"method\": \"CARD\", \"cardIssuer\": \"SHINHAN\" },\n  \"expectedTotalAmount\": 27900\n}"),
                    @ExampleObject(name = "간편결제 · 바로 구매", value = "{\n  \"idempotencyKey\": \"…uuid…\",\n" +
                            "  \"direct\": { \"variantId\": 301, \"quantity\": 1, \"groupBuyId\": 41 },\n" +
                            "  \"payment\": { \"method\": \"EASY_PAY\", \"easyPayProvider\": \"KAKAOPAY\" }\n}")
            }))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "주문 생성 — 결제창 재료",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.CreateOrderResponse.class),
                            examples = @ExampleObject(name = "응답 예시", value = "{\n" +
                                    "  \"orderId\": 1147, \"orderNumber\": \"20260927-000123\", \"status\": \"PAYMENT_PENDING\", \"expiresAt\": \"2026-09-27T14:41:00\",\n" +
                                    "  \"payment\": {\n" +
                                    "    \"paymentId\": \"20260927-000123-1\", \"storeId\": \"store-…\", \"channelKey\": \"channel-key-…\",\n" +
                                    "    \"orderName\": \"시카 리페어 앰플 30ml 리필 2개 세트 기획\", \"totalAmount\": 27900, \"currency\": \"KRW\",\n" +
                                    "    \"payMethod\": \"CARD\", \"cardCompany\": \"SHINHAN_CARD\", \"easyPayProvider\": null,\n" +
                                    "    \"customer\": { \"fullName\": \"김수민\", \"phoneNumber\": \"010-1234-5678\", \"email\": \"sumin@example.com\" }\n" +
                                    "  }\n}"))),
            @ApiResponse(responseCode = "400", description = "입력 오류 · 배송지 없음(ORDER_ADDRESS_REQUIRED) · 살 수 없는 항목 · 재고 부족 · 이용 불가 결제수단(PAYMENT_METHOD_UNAVAILABLE)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "금액 변경(ORDER_AMOUNT_CHANGED) · 같은 키의 주문이 이미 닫힘(ORDER_ALREADY_CLOSED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "포트원 사전 등록 실패(PAYMENT_GATEWAY_ERROR) — 같은 키로 재요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderDto.CreateOrderResponse> createOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                             @Valid @RequestBody OrderDto.CreateOrderRequest request);

    @Operation(
            summary = "결제 재시도 — 새 paymentId 발급",
            description = "결제 실패 뒤 다른 수단으로 다시 시도한다. 이전 살아 있는 결제는 `SUPERSEDED` 로 내리고 새 `paymentId`(`{주문번호}-{시도번호}`)를 발급한다. " +
                    "이전 결제창이 뒤늦게 결제되면 서버가 자동 취소한다 — 한 주문에는 정확히 하나의 결제만 붙는다.\n\n" +
                    "주문이 이미 결제됐으면 409 `PAYMENT_ALREADY_IN_PROGRESS`, 만료·취소됐으면 409 `ORDER_ALREADY_CLOSED`.\n\n" +
                    "**권한:** USER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "새 결제창 재료 — 주문 생성 응답과 같은 모양",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.CreateOrderResponse.class))),
            @ApiResponse(responseCode = "403", description = "남의 주문(ORDER_ACCESS_DENIED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "PAYMENT_ALREADY_IN_PROGRESS · ORDER_ALREADY_CLOSED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "PAYMENT_GATEWAY_ERROR",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderDto.CreateOrderResponse> retryPayment(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Parameter(description = "주문 ID", example = "1147") @PathVariable("orderId") Long orderId,
                                                              @Valid @RequestBody OrderDto.RetryPaymentRequest request);

    @Operation(
            summary = "주문 내역 목록 (C10)",
            description = "최근 6개월의 **결제된 적 있는 주문**을 최신순으로 내린다. 결제 대기·만료·결제 전 취소 주문은 나오지 않고, " +
                    "결제 후 취소된 주문은 「취소」 행으로 나온다.\n\n" +
                    "**구조:** 페이지 단위는 **주문**(`content[]`)이고 그 아래 `items[]` 가 주문 항목(옵션) 행이다 — 주문이 페이지 경계에서 " +
                    "쪼개지지 않는다. 한 주문 안에서도 브랜드가 다르면 항목마다 상태가 다를 수 있다.\n\n" +
                    "**항목 행은 서버가 그릴 것을 전부 내린다** — 앱이 상태→색·버튼 매핑을 들지 않는다.\n" +
                    "- `status` · `statusLabel` · `statusTone`(`ACTIVE` 로즈 / `MUTED` 회색) · `dimmed`(취소 행 탈색)\n" +
                    "- `statusSub`: 상태 보조 문구 완성 문자열(「09.15 발송 예정」 「09.17 도착 예정」 「09.23 구매확정 예정」 「브랜드 확인 중」 「환불 처리 중」 「완료」). " +
                    "다른 형식이 필요하면 `dates` 의 원시 시각을 쓴다. **배송중**은 집화 후에만 「도착 예정」(집화일 + 3배송일 · 일요일·공휴일 제외 · " +
                    "택배사별 실제 소요일로 보정)이 붙고, 집화 전이거나 예정일이 지났으면 `statusSub` 가 null 이다 — 「배송중」만 그린다\n" +
                    "- `amountLabel`: 「24,900원」, 결제 후 취소 항목은 「환불 24,900원」\n" +
                    "- `cancelRejection`: 취소 요청이 반려된 항목의 회색 줄(「취소 요청 반려 · 사유 보기」) — 구매확정되면 사라진다\n" +
                    "- `cancelRequestId`: `CANCEL_REQUESTED` 일 때 검토 중인 요청\n" +
                    "- `actions[]`: **내려온 버튼만 그린다.** 지금은 `CANCEL`(주문 취소 — 주문 전체가 준비 시작 전일 때만) 하나다. " +
                    "취소 요청·배송 조회·반품/교환·취소 상세는 해당 API 가 배포될 때 함께 내려간다\n" +
                    "- `claim` · `claimRejection` · `returnedQuantity`: 반품·교환 모듈 배포 전에는 null · null · 0\n\n" +
                    "**status 값:** `PAID`(결제완료) · `PREPARING`(상품준비중) · `SHIPPING`(배송중) · `RETURNING`(반송중) · `DELIVERED`(배송완료) · " +
                    "`CONFIRMED`(구매확정) · `CANCEL_REQUESTED`(취소 요청중) · `CANCELLED`(취소) — " +
                    "`RETURN_IN_PROGRESS` · `EXCHANGE_IN_PROGRESS` · `RETURNED` 는 반품·교환 모듈과 함께 나온다.\n\n" +
                    "**페이지:** `page` 1부터 · `size` 1~50(밖이면 400). 필터는 없다. 빈 상태는 `content: []`.\n\n" +
                    "**권한:** USER\n**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문 내역",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "응답 예시", value = "{\n" +
                                    "  \"content\": [ {\n" +
                                    "    \"orderId\": 812, \"orderNumber\": \"20260912-000201\", \"orderedAt\": \"2026-09-12T10:21:07\",\n" +
                                    "    \"items\": [ {\n" +
                                    "      \"orderProductId\": 1501, \"productId\": 77, \"variantId\": 301,\n" +
                                    "      \"brandName\": \"라보에이치\", \"productName\": \"시카 리페어 앰플 30ml 리필 2개 세트 기획\",\n" +
                                    "      \"optionName\": \"30ml + 리필 2개\", \"quantity\": 1, \"returnedQuantity\": 0, \"thumbnailUrl\": \"https://cdn/…\",\n" +
                                    "      \"status\": \"SHIPPING\", \"statusLabel\": \"배송중\", \"statusTone\": \"ACTIVE\", \"statusSub\": \"09.17 도착 예정\",\n" +
                                    "      \"dimmed\": false, \"amount\": 24900, \"amountLabel\": \"24,900원\",\n" +
                                    "      \"cancelRejection\": { \"cancelRequestId\": 31, \"rejectedAt\": \"2026-09-14T16:20:05\" },\n" +
                                    "      \"cancelRequestId\": null, \"claim\": null, \"claimRejection\": null,\n" +
                                    "      \"dates\": { \"shipDueAt\": \"2026-09-15T10:21:07\", \"shippedAt\": \"2026-09-14T15:02:11\", \"arrivalDueDate\": \"2026-09-17\", \"deliveredAt\": null,\n" +
                                    "                 \"confirmDueAt\": null, \"confirmedAt\": null, \"cancelledAt\": null },\n" +
                                    "      \"actions\": []\n" +
                                    "    } ]\n" +
                                    "  } ],\n" +
                                    "  \"pageInfo\": { \"currentPage\": 1, \"totalPages\": 1, \"totalResults\": 7, \"limit\": 20, \"hasNext\": false }\n" +
                                    "}"))),
            @ApiResponse(responseCode = "400", description = "size 가 1~50 밖(INVALID_INPUT)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<UserOrderDto.OrderCard>> getOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "페이지 번호(1부터)", example = "1") @RequestParam(name = "page", defaultValue = "1") int page,
            @Parameter(description = "페이지당 주문 수(1~50)", example = "20") @RequestParam(name = "size", defaultValue = "20") int size);

    @Operation(
            summary = "주문 상세",
            description = "결제 완료 화면 · 마이 진입점 · 문의 카드가 쓰는 주문 상세. 주문서와 같은 모양의 `groups`·`summary` 에 상태·주문번호·결제 시각·" +
                    "배송지 스냅샷·요청사항·결제 정보를 더한다. 결제 화면으로 돌아온 앱은 이 API 로 상태를 다시 읽는다(웹훅이 먼저 확정했을 수 있다).\n\n" +
                    "**C10-1 주문 상세 화면용 필드**(기존 필드는 그대로다):\n" +
                    "- `items[]` · `itemCount`: 쇼룸 그룹 없는 평면 항목 행 — 주문 내역 목록과 같은 모양이다. 배송완료 항목의 `statusSub` 만 " +
                    "배송완료 시각(「09.16 14:20」)으로 다르다\n" +
                    "- `maskedAddress`: 마스킹된 배송지(이름 마지막 글자 · 연락처 가운데 블록 · 상세 주소 전체를 가린다). " +
                    "C10-1 은 `deliveryAddress`(원문) 대신 이것을 그린다\n" +
                    "- `notices[]`: 상단 안내 — `CONFIRM_DUE`(구매확정 기한 · `date` = 가장 이른 구매확정 예정). 문구는 앱이 갖는다\n" +
                    "- `summary.discountRate`: 할인율(%) — 상품 금액 기준, 배송비 제외\n\n" +
                    "**권한:** USER (본인 주문만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문 상세",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.OrderDetailResponse.class))),
            @ApiResponse(responseCode = "403", description = "ORDER_ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderDto.OrderDetailResponse> getOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Parameter(description = "주문 ID", example = "1147") @PathVariable("orderId") Long orderId);

    @Operation(
            summary = "배송 조회 (C10-2)",
            description = "주문 항목이 든 하위주문의 송장을 조회한다. 스캔 이력은 서버가 주기적으로 저장해 둔 것을 내린다 — " +
                    "방금 스캔된 이력은 다음 추적 회차 뒤에 보인다.\n\n" +
                    "**`state`**\n" +
                    "- `NOT_SHIPPED`: 송장 없음(결제완료 · 상품준비중) — `stageIndex = -1` · `carrier` · `trackingNumber` null · " +
                    "`shipDueAt` · `groupBuyEndAt` 을 내린다\n" +
                    "- `IN_TRANSIT`: 송장 있음 · 미완료 — `headline.date` 는 도착 예정일(집화 전·예정일 경과면 null), " +
                    "`stageIndex` 는 이력 0건이면 0, 있으면 1\n" +
                    "- `DELIVERED`: 배송완료 — `headline.date` 는 배송완료일 · `stageIndex = 2`\n\n" +
                    "**`scans[]`** 는 최신순 전체다 — 접기(최근 N건)는 앱이 한다. 위치·문구는 택배사 원문 그대로다.\n\n" +
                    "**`carrier.tel` · `carrier.trackingUrl`** 이 null 이면 [택배사 전화하기] · [택배사에서 조회]를 그리지 않는다.\n\n" +
                    "`context` 는 이 경로에서 항상 `ORDER` 다(`contextLabel` · `contextNote` null).\n\n" +
                    "**권한:** USER (본인 주문만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "ORDER_ACCESS_DENIED — 남의 주문",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND · ORDER_PRODUCT_NOT_FOUND — 그 주문의 항목이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UserOrderDto.TrackingResponse> getItemTracking(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "주문 ID", example = "1147") @PathVariable("orderId") Long orderId,
            @Parameter(description = "주문 항목 ID", example = "1501") @PathVariable("orderProductId") Long orderProductId);

    @Operation(
            summary = "주문 배송지 변경 (C10-1 · C13-2)",
            description = "주문의 배송지를 내 배송지 중 하나로 바꾼다. 주문은 배송지를 참조하지 않고 값을 복사해 든다 — " +
                    "바꾼 뒤 그 배송지를 수정·삭제해도 주문은 그대로다. 배송 요청사항도 고른 배송지의 것으로 바뀐다.\n\n" +
                    "**조건:** 결제된 주문이고 **취소되지 않은 하위 주문이 전부 결제완료(준비 시작 전)** 일 때만. " +
                    "브랜드가 하나라도 상품 준비를 시작했으면(발주서를 내려받은 것 포함) 409 `ORDER_ADDRESS_NOT_CHANGEABLE`. " +
                    "버튼은 주문 상세의 `addressChangeable` 이 true 일 때만 그린다.\n\n" +
                    "새 주소는 `POST /v1/user/delivery-addresses` 로 먼저 만들고 응답의 `id` 를 넘긴다. " +
                    "배송비는 다시 계산하지 않는다.\n\n" +
                    "**권한:** USER (본인 주문 · 본인 배송지만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공 — 바뀐 배송지(마스킹)"),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT — `addressId` 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "ORDER_ACCESS_DENIED — 남의 주문",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND · ADDRESS_NOT_FOUND — 없는 배송지이거나 남의 배송지",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ORDER_ADDRESS_NOT_CHANGEABLE — 준비 시작 이후 · 결제 전 · 취소된 주문",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderDto.ChangeAddressResponse> changeDeliveryAddress(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "주문 ID", example = "1147") @PathVariable("orderId") Long orderId,
            @Valid @RequestBody OrderDto.ChangeAddressRequest request);

    @Operation(
            summary = "주문 취소 (결제 전 · 배송 전 전액)",
            description = "**결제 전(PAYMENT_PENDING):** 즉시 취소하고 재고를 돌려놓는다 → 200.\n\n" +
                    "**결제 후(PAID, 배송 전):** 취소를 선점한 뒤 포트원에 전액 취소를 요청한다.\n" +
                    "- 성공 → 200 `status: CANCELLED`\n" +
                    "- 포트원이 명시적으로 거절 → 502 `PAYMENT_CANCEL_FAILED`(주문은 그대로)\n" +
                    "- 타임아웃·통신 오류 → **202** `paymentStatus: CANCEL_REQUESTED` — 취소됐을 수도 있어 되돌리지 않는다. 앱은 「취소 처리 중」으로 그리고 " +
                    "주문 상세를 다시 읽는다. 서버가 2분 뒤부터 재조회해 수렴시킨다.\n\n" +
                    "동시에 두 번 누르면 둘째는 409 `PAYMENT_CANCEL_IN_PROGRESS`. 만료·이미 취소된 주문은 409 `ORDER_NOT_CANCELLABLE`. " +
                    "브랜드가 하위 주문 하나라도 상품 준비를 시작했으면 409 `ORDER_CANCEL_WINDOW_CLOSED` — 바로 취소가 아니라 " +
                    "취소 요청(브랜드 승인) 경로다(앱용 API 는 아직 없다).\n\n" +
                    "취소 버튼은 주문 상세의 `cancellable` 이 true 일 때만 그린다.\n\n" +
                    "**권한:** USER (본인 주문만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 완료",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.CancelResponse.class))),
            @ApiResponse(responseCode = "202", description = "취소 처리 중(PG 응답 대기)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.CancelResponse.class))),
            @ApiResponse(responseCode = "403", description = "ORDER_ACCESS_DENIED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "ORDER_NOT_CANCELLABLE · PAYMENT_CANCEL_IN_PROGRESS · ORDER_CANCEL_WINDOW_CLOSED(준비 시작 후)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "PAYMENT_CANCEL_FAILED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderDto.CancelResponse> cancelOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Parameter(description = "주문 ID", example = "1147") @PathVariable("orderId") Long orderId,
                                                        @Valid @RequestBody(required = false) OrderDto.CancelRequest request);
}
