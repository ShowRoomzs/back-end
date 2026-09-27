package showroomz.api.app.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.cart.dto.CartDto;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.domain.payment.type.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

/** C9 결제 화면 DTO(결제 계획서 5절). 시각은 장바구니와 같은 KST {@code yyyy-MM-dd'T'HH:mm:ss}다(5-3). */
public class OrderDto {

    /** C9 요청사항 프리셋 4종. */
    public static final List<String> MEMO_PRESETS = List.of(
            "문 앞에 놓아주세요", "경비실에 맡겨주세요", "부재 시 연락해주세요", "배송 전 미리 연락해주세요");

    public static final String TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    // ------------------------------------------------------------------ 요청

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "바로 구매 한 줄 — C7 [바로 구매]. 공구는 필수다(4-6)")
    public static class DirectItem {
        @NotNull(message = "옵션(Variant) ID는 필수입니다.")
        @Schema(description = "옵션(Variant) ID", example = "301")
        private Long variantId;

        @NotNull(message = "수량은 필수입니다.")
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
        @Max(value = CartDto.MAX_QUANTITY, message = "수량은 " + CartDto.MAX_QUANTITY + " 이하여야 합니다.")
        @Schema(description = "수량", example = "1")
        private Integer quantity;

        @NotNull(message = "공구를 지정해 주세요.")
        @Schema(description = "진입한 공구 — 상품 상세 응답의 groupBuyId", example = "41")
        private Long groupBuyId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문서 조회 요청 — cartItemIds 와 direct 중 하나만 채운다")
    public static class CheckoutRequest {
        @Schema(description = "C8에서 선택한 장바구니 항목 ID", example = "[11, 12, 15]")
        private List<Long> cartItemIds;

        @Valid
        @Schema(description = "바로 구매 한 줄")
        private DirectItem direct;

        @Schema(description = "배송지 ID — 생략하면 기본 배송지", example = "7")
        private Long deliveryAddressId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "결제수단 선택 — CARD 면 cardIssuer, EASY_PAY 면 easyPayProvider")
    public static class PaymentSelection {
        @NotNull(message = "결제수단은 필수입니다.")
        @Schema(description = "결제수단", example = "CARD")
        private PaymentMethod method;

        @Schema(description = "카드사 — method=CARD 일 때 필수", example = "SHINHAN")
        private CardIssuer cardIssuer;

        @Schema(description = "간편결제 — method=EASY_PAY 일 때 필수", example = "KAKAOPAY")
        private EasyPayProvider easyPayProvider;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 생성 요청")
    public static class CreateOrderRequest {
        @NotBlank(message = "idempotencyKey는 필수입니다.")
        @Size(max = 64)
        @Schema(description = "앱이 주문 생성마다 새로 만드는 UUID — 같은 키의 재요청은 같은 주문을 돌려준다", example = "5f1e2b3c-0a1d-4c2e-9b7f-1234567890ab")
        private String idempotencyKey;

        @Schema(description = "장바구니 항목 ID — direct 와 둘 중 하나만", example = "[11, 12, 15]")
        private List<Long> cartItemIds;

        @Valid
        private DirectItem direct;

        @Schema(description = "배송지 ID — 생략하면 기본 배송지. 없으면 400 ORDER_ADDRESS_REQUIRED", example = "7")
        private Long deliveryAddressId;

        @Size(max = 50, message = "배송 요청사항은 50자 이내여야 합니다.")
        @Schema(description = "요청사항 — 프리셋 4종 또는 직접 입력 50자", example = "문 앞에 놓아주세요")
        private String deliveryMemo;

        @NotNull(message = "결제수단은 필수입니다.")
        @Valid
        private PaymentSelection payment;

        @Schema(description = "주문서 응답의 summary.totalAmount — 서버 계산과 다르면 409 ORDER_AMOUNT_CHANGED", example = "27900")
        private Long expectedTotalAmount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 취소 요청")
    public static class CancelRequest {
        @Size(max = 255, message = "취소 사유는 255자 이내여야 합니다.")
        @Schema(description = "취소 사유", example = "단순 변심")
        private String reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "결제 재시도 요청 — 다른 수단으로 새 paymentId 를 받는다")
    public static class RetryPaymentRequest {
        @NotNull(message = "결제수단은 필수입니다.")
        @Valid
        private PaymentSelection payment;
    }

    // ------------------------------------------------------------------ 주문서 · 상세

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "배송지 — 주문서는 저장된 배송지, 주문 상세는 주문 시점 스냅샷")
    public static class AddressInfo {
        @Schema(description = "배송지 ID — 스냅샷이면 null", example = "7", nullable = true)
        private Long id;
        private String recipientName;
        private String phoneNumber;
        private String zipCode;
        private String address;
        private String detailAddress;
        @Schema(description = "저장된 배송 메모 — 요청사항의 초기값", nullable = true)
        private String memo;
        @Schema(description = "기본 배송지 여부", example = "true")
        private Boolean isDefault;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 상품 한 줄")
    public static class Item {
        @Schema(description = "장바구니 항목 ID — 바로 구매·주문 상세는 null", nullable = true)
        private Long cartId;
        @Schema(description = "주문 상품 ID — 주문 상세에만", nullable = true)
        private Long orderProductId;
        private Long variantId;
        private Long productId;
        private String productName;
        private String optionName;
        private String thumbnailUrl;
        private Integer quantity;
        @Schema(description = "정가·판매가·할인율 — 공구 계약의 옵션 가격")
        private ProductDto.PriceInfo price;
        @Schema(description = "주문 상품 상태 — 주문 상세에만", nullable = true)
        private String status;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "그룹 배송비")
    public static class Shipping {
        private Long productTotal;
        @Schema(description = "실제 부과 배송비 — 무료면 0")
        private Integer deliveryFee;
        @Schema(nullable = true)
        private Integer freeShippingThreshold;
        private Boolean isFreeShipping;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "공구(쇼룸) 단위 그룹 — 배송비는 그룹마다 매긴다")
    public static class Group {
        private Long marketId;
        private String marketName;
        private Long groupBuyId;
        @Schema(nullable = true)
        private String groupBuyNumber;
        private List<Item> items;
        private Shipping shipping;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "C9 「결제 금액」 4줄")
    public static class Summary {
        @Schema(description = "상품 금액(정가 합)", example = "38000")
        private Long productTotal;
        @Schema(description = "할인 금액", example = "13100")
        private Long discountTotal;
        @Schema(description = "배송비", example = "3000")
        private Long deliveryFeeTotal;
        @Schema(description = "총 결제 금액", example = "27900")
        private Long totalAmount;
        private Integer itemCount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "지금 열려 있는 결제수단 — 채널이 꺼진 간편결제는 목록에서 빠진다")
    public static class PaymentMethods {
        private List<CardIssuer> cardIssuers;
        private List<EasyPayProvider> easyPayProviders;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문서 응답 — 저장하지 않는다")
    public static class CheckoutResponse {
        @Schema(description = "배송지 — 없으면 null(C9 「배송지 없음」, CTA 잠금)", nullable = true)
        private AddressInfo deliveryAddress;
        private List<String> memoPresets;
        private List<Group> groups;
        private Summary summary;
        private PaymentMethods paymentMethods;
        @Schema(example = "27,900원 결제하기")
        private String ctaLabel;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "결제창 재료 — 포트원 RN SDK <Payment request> 에 그대로 넘긴다. redirectUrl 은 SDK 가 정한다(2-4)")
    public static class PaymentWindow {
        private String paymentId;
        private String storeId;
        private String channelKey;
        private String orderName;
        private Long totalAmount;
        private String currency;
        @Schema(description = "포트원 payMethod — CARD · EASY_PAY", example = "CARD")
        private String payMethod;
        @Schema(description = "포트원 CardCompany 코드 — 카드일 때", nullable = true)
        private String cardCompany;
        @Schema(description = "포트원 easyPayProvider — 간편결제일 때", nullable = true)
        private String easyPayProvider;
        private Customer customer;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Customer {
        private String fullName;
        private String phoneNumber;
        @Schema(nullable = true)
        private String email;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 생성·결제 재시도 응답")
    public static class CreateOrderResponse {
        private Long orderId;
        private String orderNumber;
        private OrderStatus status;
        @Schema(description = "결제 대기 만료 시각(Asia/Seoul)", example = "2026-09-27T14:41:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN)
        private LocalDateTime expiresAt;
        private PaymentWindow payment;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문의 결제 정보 — 수단 라벨 · 상태 · 결제 시각")
    public static class PaymentInfo {
        private String paymentId;
        private PaymentStatus status;
        private PaymentMethod method;
        @Schema(description = "화면 라벨 — 「신한카드」·「카카오페이」", example = "신한카드")
        private String methodLabel;
        @Schema(nullable = true)
        private CardIssuer cardIssuer;
        @Schema(nullable = true)
        private EasyPayProvider easyPayProvider;
        private Long amount;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN)
        private LocalDateTime paidAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 상세 — 결제 완료 화면 · 마이 진입점 · 문의 카드")
    public static class OrderDetailResponse {
        private Long orderId;
        private String orderNumber;
        private OrderStatus status;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN)
        private LocalDateTime orderedAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN)
        private LocalDateTime paidAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TIME_PATTERN)
        private LocalDateTime expiresAt;
        private AddressInfo deliveryAddress;
        private String deliveryMemo;
        private List<Group> groups;
        private Summary summary;
        @Schema(description = "주문의 결제 — 완료된 결제, 없으면 살아 있는 결제, 그것도 없으면 마지막 시도", nullable = true)
        private PaymentInfo payment;
        @Schema(description = "지금 취소할 수 있는가(결제 전 · 배송 전)")
        private Boolean cancellable;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "주문 취소 응답 — 200 이면 취소 완료, 202 면 취소 처리 중(PG 응답 대기)")
    public static class CancelResponse {
        private Long orderId;
        private OrderStatus status;
        @Schema(nullable = true)
        private PaymentStatus paymentStatus;
        private String message;
    }
}
