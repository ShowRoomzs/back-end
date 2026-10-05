package showroomz.api.app.claim.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.order.type.ClaimFeeBearer;
import showroomz.domain.order.type.ClaimPaymentStatus;
import showroomz.domain.order.type.ClaimReason;
import showroomz.domain.order.type.ClaimResult;
import showroomz.domain.order.type.ClaimStatus;
import showroomz.domain.order.type.ClaimType;
import showroomz.domain.order.type.DeliveryCarrier;
import showroomz.domain.order.type.StoragePhase;
import showroomz.domain.order.type.UserClaimPhase;
import showroomz.domain.order.type.UserOrderTone;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소비자 앱 반품·교환(C10-3 요청 · C10-5 상세 — 앱 클레임 설계서 3 · 4절). 사유 · 택배사 · 금액 · 라벨 · 버튼은 서버가
 * 내린다 — 앱에 목록을 박지 않는다(0-7 · 0-8). 시각은 주문 API 와 같은 규약({@link OrderDto#TIME_PATTERN} · KST).
 */
public class UserClaimDto {

    /** 반송 택배비를 누가 내는가 — 고객 귀책은 선불(택배사에 직접), 브랜드 귀책은 착불. 앱은 이 돈을 다루지 않는다. */
    public enum CourierPayment {
        PREPAID, COLLECT
    }

    public enum ActionType {
        /** 요청 철회 — 회수 송장을 넣기 전까지만. */
        WITHDRAW,
        REGISTER_COLLECTION_INVOICE,
        /** 회수 조회(C10-4). */
        TRACK_COLLECTION,
        /** 재발송 배송 조회(C10-2). */
        TRACK_RESHIP,
        /** 1:1 문의 — 문의 생성에 orderId 를 넘긴다. */
        INQUIRY
    }

    public enum ReshipFeeState {
        /** 결제 필요 — 하단 CTA 를 연다. */
        PAYABLE,
        /** 거절은 났는데 같은 박스의 나머지 판정이 안 끝났다 — 블록을 열지 않는다. */
        WAITING,
        PAID,
        /** 함께 반품한 상품의 환불액에서 차감. */
        DEDUCTED,
        /** 교환 요청 때 결제한 배송비로 충당. */
        COVERED
    }

    // ------------------------------------------------------------------ 폼(3-1)

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반품·교환 요청 화면의 폼 데이터")
    public static class FormResponse {
        private ClaimType type;
        private Long deliveryGroupId;
        private String brandName;
        @Schema(description = "그 하위주문의 신청 가능 항목 전부 — 진입 항목은 preselected")
        private List<FormItem> items;
        private List<FormReason> reasons;
        @Schema(description = "회수 송장에 고를 수 있는 택배사")
        private List<FormCarrier> carriers;
        @Schema(description = "브랜드 반품 수취 주소 — 송장에 적는 값이라 원문이다")
        private ReturnTo returnTo;
        @Schema(description = "교환받을 배송지의 기본값 — 원 주문 배송지. 교환 폼만", nullable = true)
        private ReshipTo reshipTo;
        private Fees fees;
        private CourierPayments courierPayment;
        @Schema(description = "환불 수단 문구", example = "신한카드 결제 취소", nullable = true)
        private String refundMethodLabel;
        @Schema(description = "회수 송장 등록 기한(일) — 「7일 안에 입력하지 않으면 요청이 취소돼요」", example = "7")
        private Integer invoiceDueDays;
        @Schema(example = "250")
        private Integer detailMaxLength;
        @Schema(example = "10")
        private Integer photoMax;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FormItem {
        private Long orderProductId;
        private String productName;
        private String optionName;
        private String thumbnailUrl;
        @Schema(description = "단가(공구가)", example = "27200")
        private Integer unitPrice;
        @Schema(description = "신청 가능 수량 — 요청의 items[].quantity 상한. 2 이상이면 수량 선택을 그린다", example = "2")
        private Integer claimableQuantity;
        @Schema(description = "진입한 항목 — 체크된 채로 그린다")
        private Boolean preselected;
        @Schema(description = "교환할 수 있는 옵션 — 교환 폼만. 같은 상품 · 같은 가격의 옵션이고 받은 옵션도 든다. 반품 폼은 null",
                nullable = true)
        private List<ExchangeOption> exchangeOptions;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExchangeOption {
        private Long variantId;
        @Schema(example = "용량: 리필")
        private String optionName;
        @Schema(description = "재고 없음 — 목록에서 빼지 않고 회색 + 「품절」로 그린다")
        private Boolean soldOut;
        @Schema(description = "받은 옵션 — 불량 · 오배송일 때만 고를 수 있다(같은 옵션으로 다시 받기)")
        private Boolean current;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "교환받을 배송지 — 요청 화면은 지금 고르는 값이라 원문이다")
    public static class ReshipTo {
        private String recipientName;
        private String phone;
        private String zipCode;
        private String address;
        private String detailAddress;
        private String memo;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FormReason {
        private ClaimReason code;
        @Schema(example = "단순 변심")
        private String label;
        @Schema(description = "괄호 설명 — 유형에 따라 다르다. 없으면 null", example = "상품이 필요 없어짐", nullable = true)
        private String hint;
        @Schema(description = "CONSUMER 면 배송비가 발생하고 반송 택배비는 선불, SELLER 면 0원 · 착불")
        private ClaimFeeBearer feeBearer;
        @Schema(description = "상세 내용 필수 여부")
        private Boolean detailRequired;
        @Schema(description = "사진 첨부를 받는가 — 브랜드 귀책 사유만")
        private Boolean photoAllowed;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FormCarrier {
        private DeliveryCarrier code;
        @Schema(example = "CJ대한통운")
        private String label;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반품·교환 도착지 — 브랜드 반품 수취 주소")
    public static class ReturnTo {
        private String name;
        private String contact;
        private String address;
        private String detailAddress;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "앱이 다루는 배송비 — 사유의 feeBearer 로 갈라 쓴다. 신청 때 서버가 다시 계산해 검증한다")
    public static class Fees {
        @Schema(description = "고객 귀책 사유일 때 — 반품은 환불액에서 빼는 최초 배송비(무료배송으로 받은 주문만, 아니면 0), "
                + "교환은 요청할 때 결제하는 재발송 배송비", example = "3000")
        private Integer consumerFault;
        @Schema(description = "브랜드 귀책 사유일 때 — 항상 0", example = "0")
        private Integer sellerFault;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반송 택배비 안내 — 「선불로 / 착불로 보내주세요」")
    public static class CourierPayments {
        @Schema(example = "PREPAID")
        private CourierPayment consumerFault;
        @Schema(example = "COLLECT")
        private CourierPayment sellerFault;
    }

    // ------------------------------------------------------------------ 신청(3-2)

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반품·교환 요청")
    public static class CreateRequest {
        @Size(max = 64)
        @Schema(description = "멱등키 — 같은 키의 재요청은 새로 만들지 않고 기존 요청을 돌려준다", example = "8f1c0b7e-…")
        private String idempotencyKey;
        @NotNull(message = "요청 유형을 선택해 주세요.")
        private ClaimType type;
        @NotNull(message = "주문 정보를 확인해 주세요.")
        private Long deliveryGroupId;
        @Valid
        @NotEmpty(message = "상품을 선택해 주세요.")
        private List<CreateItem> items;
        @NotNull(message = "사유를 선택해 주세요.")
        private ClaimReason reasonCode;
        private String reasonDetail;
        @Schema(description = "사진 URL — 이미지 업로드 API 가 준 값. 브랜드 귀책 사유에서만 받는다(그 밖에는 무시)")
        private List<String> imageUrls;
        @Valid
        @Schema(description = "회수 송장 — null 이면 「나중에 입력하기」", nullable = true)
        private InvoiceRequest invoice;
        @Schema(description = "폼에서 본 배송비 — 서버 계산값과 다르면 409. 생략하면 검증하지 않는다", example = "3000", nullable = true)
        private Integer expectedFee;
        @Schema(description = "교환받을 배송지 — 내 배송지 ID. 생략하면 원 주문 배송지. 교환만", example = "55", nullable = true)
        private Long reshipAddressId;
        @Valid
        @Schema(description = "결제 수단 — 재발송 배송비 결제가 필요한 교환(고객 귀책)에서 필수", nullable = true)
        private OrderDto.PaymentSelection payment;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateItem {
        @NotNull(message = "상품을 선택해 주세요.")
        private Long orderProductId;
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
        @Schema(description = "신청 수량 — 1 이상 · 폼의 claimableQuantity 이하. 생략하면 신청 가능 수량 전부", example = "1",
                nullable = true)
        private Integer quantity;
        @Schema(description = "교환받을 옵션 — 교환에서 필수. 폼의 exchangeOptions 중 하나", example = "302", nullable = true)
        private Long exchangeVariantId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "회수 송장")
    public static class InvoiceRequest {
        @NotNull(message = "택배사를 선택해 주세요.")
        private DeliveryCarrier carrier;
        @NotNull(message = "송장번호를 입력해 주세요.")
        @Schema(description = "숫자 외 문자(하이픈·공백)는 서버가 지운다", example = "684922013378")
        private String trackingNumber;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "요청 접수 결과 — 이어서 상세(GET /{claimId})로 접수 화면을 그린다")
    public static class CreateResponse {
        @Schema(description = "요청 id(박스 단위)")
        private Long requestId;
        @Schema(description = "항목별 클레임 id — items 순서")
        private List<Long> claimIds;
        @Schema(description = "REQUESTED(송장 나중에) · COLLECTING(송장 같이 냄) · PAYMENT_PENDING(결제가 끝나야 접수된다)")
        private ClaimStatus status;
        @Schema(description = "포트원 결제창 파라미터 — 주문 결제와 같은 모양. null 이면 결제 없이 접수된 것이다", nullable = true)
        private OrderDto.PaymentWindow payment;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "결제창 복귀 결과")
    public static class PaymentCompleteResponse {
        @Schema(description = "PAID 면 결제 완료. READY · FAILED 면 결제되지 않았다")
        private ClaimPaymentStatus paymentStatus;
        private Long requestId;
        @Schema(description = "그 요청의 클레임 — 요청이 이미 지워졌으면 빈 배열")
        private List<Long> claimIds;
        @Schema(description = "REQUESTED · COLLECTING 이면 접수됨, PAYMENT_PENDING 그대로면 요청 화면으로 돌아간다", nullable = true)
        private ClaimStatus claimStatus;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "교환받을 배송지 변경")
    public static class ReshipAddressRequest {
        @NotNull(message = "배송지를 선택해 주세요.")
        @Schema(description = "내 배송지 ID", example = "55")
        private Long addressId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "요청 철회 결과 — 앱은 주문 상세로 돌아간다(항목이 배송완료로 복귀)")
    public static class WithdrawResponse {
        private Long claimId;
        private ClaimStatus status;
        private ClaimResult result;
    }

    // ------------------------------------------------------------------ 상세(4-1)

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반품·교환 상세 — 접수 화면도 같은 데이터다")
    public static class DetailResponse {
        private Long requestId;
        private ClaimType type;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime requestedAt;
        @Schema(description = "완료 일시 — 진입한 항목이 종결됐을 때. 없으면 앱이 「검수 후 표시돼요」", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime completedAt;
        private Long orderId;
        private String orderNumber;
        private Guide guide;
        @Schema(description = "그 요청의 클레임 전부 — 철회·취소된 것은 뺀다(진입한 항목은 취소됐어도 싣는다)")
        private List<DetailItem> items;
        private Info info;
        @Schema(description = "환불 정보 — 반품만", nullable = true)
        private Refund refund;
        @Schema(description = "결제 정보 — 교환만", nullable = true)
        private ExchangePayment exchangePayment;
        @Schema(description = "상품 다시 받기 — 반려된 항목이 있을 때만", nullable = true)
        private ReshipFee reshipFee;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "교환 재발송 배송비 결제")
    public static class ExchangePayment {
        @Schema(description = "재발송 배송비 — 브랜드 귀책이면 0", example = "3000")
        private Integer reshipFee;
        @Schema(description = "결제한 금액 — 결제 취소됐으면 0", example = "3000")
        private Integer paidAmount;
        @Schema(description = "결제 수단 — 결제가 없었으면 「결제 없음」", example = "신한카드")
        private String methodLabel;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "「유의해 주세요」 박스 — 문구는 앱, 선불/착불만 값으로 갈린다")
    public static class Guide {
        @Schema(description = "아직 보내기 전이거나 회수 중인 항목이 있을 때")
        private Boolean visible;
        private CourierPayment courierPayment;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailItem {
        private Long claimId;
        @Schema(description = "진입한 항목")
        private Boolean focused;
        private String brandName;
        private String productName;
        private String optionName;
        @Schema(description = "교환받을 옵션 — 교환만", nullable = true)
        private String exchangeOptionName;
        private Integer quantity;
        @Schema(description = "상품 금액(단가 × 신청 수량)", example = "27200")
        private Long amount;
        private String thumbnailUrl;
        private UserClaimPhase phase;
        @Schema(example = "반품 요청")
        private String statusLabel;
        @Schema(example = "회수 송장 입력 필요", nullable = true)
        private String statusSub;
        @Schema(description = "ACTIVE(로즈 — 고객이 할 일이 있다) · MUTED")
        private UserOrderTone statusSubTone;
        private List<Action> actions;
        @Schema(description = "반려 사유 블록 — 검수에서 반려된 항목만", nullable = true)
        private Rejection rejection;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Action {
        private ActionType type;
        @Schema(example = "요청 철회")
        private String label;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Rejection {
        @Schema(example = "개봉·사용 흔적")
        private String reasonLabel;
        @Schema(description = "법적 근거 한 줄 — 문구는 법무 확정 대기(잠정)")
        private String legalNote;
        @Schema(description = "브랜드가 적은 설명 — 그대로 전달한다")
        private String sellerMessage;
        private List<String> evidenceImageUrls;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime rejectedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "신청 정보")
    public static class Info {
        @Schema(example = "단순 변심")
        private String reasonLabel;
        @Schema(example = "고객 직접 발송")
        private String methodLabel;
        @Schema(description = "회수 송장 — 미등록이면 null", nullable = true)
        private Invoice collectionInvoice;
        @Schema(description = "회수 송장 등록 기한 — 미등록일 때만. 「10.11(일)까지 등록해 주세요」", example = "2026-10-11", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate invoiceDueDate;
        @Schema(description = "재발송 송장 — 진입한 항목의 것", nullable = true)
        private Invoice reshipInvoice;
        @Schema(description = "반품 수거지 — 마스킹", nullable = true)
        private UserOrderDto.MaskedAddress pickupFrom;
        @Schema(description = "반품/교환 도착지 — 브랜드 반품센터(원문)")
        private ReturnTo returnTo;
        @Schema(description = "교환받을 배송지 — 마스킹. 교환만", nullable = true)
        private UserOrderDto.MaskedAddress reshipTo;
        @Schema(description = "교환받을 배송지를 바꿀 수 있는가")
        private Boolean reshipAddressChangeable;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Invoice {
        private DeliveryCarrier carrier;
        private String carrierLabel;
        private String trackingNumber;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "환불 정보 — 요청(박스) 단위")
    public static class Refund {
        @Schema(description = "반려된 상품의 금액 — 환불에서 빠진다(앱이 취소선). 반려가 없으면 null", nullable = true)
        private Long rejectedAmount;
        @Schema(description = "반품 배송비 차감 — 무료배송으로 받은 주문의 고객 귀책 반품만", example = "3000")
        private Integer returnDeduction;
        @Schema(description = "환불 대상 상품 금액 — 반려·취소된 항목 제외", example = "27200")
        private Long approvedAmount;
        @Schema(description = "반려 상품 재발송비 차감 — 일부 반려에서 환불액에서 뺐을 때", example = "0")
        private Integer reshipDeduction;
        @Schema(description = "환불 예정 금액(또는 확정 금액) — 전체 반려면 0", example = "24200")
        private Long amount;
        @Schema(description = "환불이 집행됐는가 — 거짓이면 「환불 예정 금액」, 참이면 「환불 금액」")
        private Boolean confirmed;
        @Schema(example = "신한카드 결제 취소", nullable = true)
        private String refundMethodLabel;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반려 상품을 다시 받는 배송비")
    public static class ReshipFee {
        private ReshipFeeState state;
        @Schema(example = "3000", nullable = true)
        private Integer amount;
        @Schema(description = "결제 기한 — PAYABLE 일 때. 「10.23(금)까지 결제해 주세요」", example = "2026-10-23", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate dueDate;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime settledAt;
        @Schema(description = "결제 수단 — PAID 일 때만. 「카카오페이」", example = "카카오페이", nullable = true)
        private String methodLabel;
        @Schema(description = "결제 기한이 지난 PAYABLE 에서만 — 미결제 고지와 보관 기한", nullable = true)
        private Storage storage;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "반려 상품 재발송 배송비 결제")
    public static class ReshipFeePaymentRequest {
        @NotNull(message = "결제수단은 필수입니다.")
        @Schema(description = "결제수단", example = "EASY_PAY")
        private PaymentMethod method;
        @Schema(description = "카드사 — method=CARD 일 때 필수", example = "SHINHAN", nullable = true)
        private CardIssuer cardIssuer;
        @Schema(description = "간편결제 — method=EASY_PAY 일 때 필수", example = "KAKAOPAY", nullable = true)
        private EasyPayProvider easyPayProvider;
        @Schema(description = "화면에 보인 금액(상세의 reshipFee.amount) — 서버 금액과 다르면 409", example = "3000", nullable = true)
        private Integer expectedAmount;
    }

    /** 회수 조회 이력의 출처 — 택배사 스캔인가 브랜드의 처리인가. */
    public enum TrackingEventSource {
        COURIER, BRAND
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "회수 조회의 상품 — 진입한 클레임 하나")
    public static class CollectionItem {
        private String brandName;
        private String productName;
        @Schema(description = "옵션 — 교환이면 「받은 옵션 → 바꿀 옵션」", example = "30ml + 리필 2개 → 30ml + 리필 1개")
        private String optionLabel;
        private String thumbnailUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "회수 이력 한 줄")
    public static class CollectionEvent {
        @Schema(description = "COURIER = 택배 스캔 · BRAND = 브랜드 처리(입고 · 검수)")
        private TrackingEventSource source;
        @Schema(description = "위치 — 택배 스캔만", example = "강남집배점", nullable = true)
        private String location;
        @Schema(example = "입고 · 검수 시작")
        private String description;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime occurredAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "C10-4 회수 조회")
    public static class CollectionTrackingResponse {
        private ClaimType type;
        @Schema(description = "false 면 「아직 조회되지 않아요」 화면 — 송장은 등록됐는데 택배 이력이 없다")
        private Boolean trackable;
        @Schema(description = "5칸 바의 현재 칸 — -1 은 전부 빈 칸. 검수 반려는 3 에서 멈춘다", example = "1")
        private Integer stageIndex;
        @Schema(description = "칸 이름 5개 — 마지막은 반품 「환불」 · 교환 「새 상품」",
                example = "[\"접수\", \"이동 중\", \"도착\", \"검수\", \"환불\"]")
        private List<String> stages;
        private UserOrderDto.TrackingHeadline headline;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime requestedAt;
        private Long claimId;
        private CollectionItem item;
        @Schema(description = "회수 송장이 없으면 null", nullable = true)
        private UserOrderDto.TrackingCarrier carrier;
        @Schema(nullable = true)
        private String trackingNumber;
        @Schema(description = "송장 등록 일시", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime invoiceRegisteredAt;
        @Schema(description = "보낸 사람 — 이름(가림)과 시·구까지", example = "김*진 · 서울 강남구")
        private String sender;
        @Schema(description = "받는 곳", example = "아로마티카 반품센터")
        private String receiver;
        @Schema(description = "이력 — 최신순. 택배 스캔과 브랜드 처리를 시각순으로 섞는다")
        private List<CollectionEvent> events;
        @Schema(description = "[송장 수정] 가능 여부 — 회수 중 · 택배 이력 없음 · 등록 기한 전")
        private Boolean invoiceEditable;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "미결제 고지 · 보관 기한 — 2회 이상 고지하면 최종 고지일 + 3개월 동안 보관한다")
    public static class Storage {
        private Integer noticeCount;
        @Schema(description = "보관 기한 — 고지 2회 미만이면 null", nullable = true)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OrderDto.TIME_PATTERN)
        private LocalDateTime storageDueAt;
        private StoragePhase phase;
    }
}
