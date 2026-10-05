package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderCancelRequestRepository;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRefundTaskRepository;
import showroomz.domain.order.type.FulfillmentStatus;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.payment.portone.PortOneCodes;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.global.utils.DiscountRate;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 저장된 주문 → 응답 조립(결제 계획서 5-3 · 5-5). 주문 생성 응답은 저장해 두지 않는다 — {@code channelKey}·{@code storeId}는
 * 설정에서, 나머지는 주문·결제 행에서 다시 조립한다(멱등 재요청이 첫 응답과 같은 것을 받는 근거).
 * 트랜잭션 안에서 불러야 한다(지연 로딩).
 */
@Component
@RequiredArgsConstructor
public class OrderAssembler {

    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final PaymentRepository paymentRepository;
    private final OrderCancelRequestRepository cancelRequestRepository;
    private final OrderRefundTaskRepository refundTaskRepository;
    private final UserOrderItemAssembler itemAssembler;
    private final UserOrderClaimLoader claimLoader;
    private final PortOnePaymentGateway gateway;
    private final OrderProperties orderProperties;

    public OrderDto.CreateOrderResponse toCreateResponse(Order order, Payment payment) {
        Users user = order.getUser();
        OrderDto.PaymentWindow window = OrderDto.PaymentWindow.builder()
                .paymentId(payment.getPaymentId())
                .storeId(gateway.storeId())
                .channelKey(payment.getChannelKey())
                .orderName(order.getOrderName())
                .totalAmount(payment.getAmount().longValue())
                .currency(payment.getCurrency())
                .payMethod(gateway.payMethodCode(payment.getMethod()))
                .cardCompany(PortOneCodes.cardCompany(payment.getCardIssuer()))
                .easyPayProvider(PortOneCodes.easyPayProvider(payment.getEasyPayProvider()))
                .customer(OrderDto.Customer.builder()
                        .fullName(order.getRecipientName() != null ? order.getRecipientName()
                                : user.getName() != null ? user.getName() : user.getNickname())
                        .phoneNumber(order.getRecipientPhone() != null ? order.getRecipientPhone() : user.getPhoneNumber())
                        .email(orderProperties.isCustomerEmailEnabled() ? user.getEmail() : null)
                        .build())
                .build();
        return OrderDto.CreateOrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .expiresAt(order.getExpiresAt())
                .payment(window)
                .build();
    }

    public OrderDto.OrderDetailResponse toDetail(Order order) {
        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderId(order.getId());
        List<OrderProduct> products = orderProductRepository.findByOrderIdWithVariant(order.getId());

        Map<Long, List<OrderProduct>> byGroup = new LinkedHashMap<>();
        for (OrderProduct product : products) {
            Long key = product.getDeliveryGroup() != null ? product.getDeliveryGroup().getId() : null;
            byGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(product);
        }

        List<OrderDto.Group> groupDtos = new ArrayList<>();
        for (OrderDeliveryGroup group : groups) {
            List<OrderProduct> groupProducts = byGroup.getOrDefault(group.getId(), List.of());
            groupDtos.add(OrderDto.Group.builder()
                    .marketId(group.getMarketId())
                    .marketName(group.getMarketName())
                    .groupBuyId(group.getGroupBuyId())
                    .groupBuyNumber(group.getGroupBuyNumber())
                    .items(groupProducts.stream().map(this::toItem).toList())
                    .shipping(OrderDto.Shipping.builder()
                            .productTotal(group.getProductTotal().longValue())
                            .deliveryFee(group.getDeliveryFee())
                            .freeShippingThreshold(group.getMarket() != null ? group.getMarket().getFreeShippingThreshold() : null)
                            .isFreeShipping(group.isFreeShippingApplied())
                            .build())
                    .build());
        }
        // 그룹이 없는 옛 행(백필 전)도 상품은 보여 준다.
        List<OrderProduct> orphan = byGroup.getOrDefault(null, List.of());
        if (!orphan.isEmpty()) {
            groupDtos.add(OrderDto.Group.builder().items(orphan.stream().map(this::toItem).toList()).build());
        }

        Payment payment = representativePayment(order).orElse(null);
        boolean cancellable = isCancellable(order, payment, products, groups);
        List<UserOrderDto.ItemRow> itemRows = toItemRows(order, products, cancellable);
        int productTotal = order.getProductTotal();
        return OrderDto.OrderDetailResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderedAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .expiresAt(order.getExpiresAt())
                .deliveryAddress(OrderDto.AddressInfo.builder()
                        .recipientName(order.getRecipientName())
                        .phoneNumber(order.getRecipientPhone())
                        .zipCode(order.getZipCode())
                        .address(order.getAddress())
                        .detailAddress(order.getDetailAddress())
                        .build())
                .deliveryMemo(order.getDeliveryMemo())
                .groups(groupDtos)
                .summary(OrderDto.Summary.builder()
                        .productTotal(order.getProductTotal().longValue())
                        .discountTotal(order.getDiscountTotal().longValue())
                        .deliveryFeeTotal(order.getDeliveryFeeTotal().longValue())
                        .totalAmount(order.getTotalAmount().longValue())
                        .itemCount(products.size())
                        .discountRate(DiscountRate.of(productTotal, productTotal - order.getDiscountTotal()))
                        .build())
                .payment(payment != null ? toPaymentInfo(payment) : null)
                .cancellable(cancellable)
                .addressChangeable(isAddressChangeable(order, groups))
                .items(itemRows)
                .itemCount(itemRows.size())
                .maskedAddress(OrderAddressMasker.mask(order))
                .notices(itemAssembler.notices(itemRows))
                .build();
    }

    /**
     * C10-1 「주문 상품 N」 — 쇼룸 그룹 없는 평면 행(C10 설계서 3-1). 목록과 같은 조립기를 탄다.
     * 취소 요청·환불 큐는 결제된 주문에만 생기므로 결제 전 주문은 읽지 않는다.
     */
    private List<UserOrderDto.ItemRow> toItemRows(Order order, List<OrderProduct> products, boolean cancellable) {
        List<Long> orderIds = List.of(order.getId());
        boolean paid = order.getPaidAt() != null;
        UserOrderItemAssembler.Context context = UserOrderItemAssembler.Context.of(
                paid ? cancelRequestRepository.findOpenOrRejectedByOrderIds(orderIds) : List.of(),
                paid ? new HashSet<>(refundTaskRepository.findPendingGroupIdsByOrderIds(orderIds)) : Set.of(),
                cancellable ? Set.of(order.getId()) : Set.of())
                .withClaims(paid ? claimLoader.load(products) : UserOrderClaimContext.EMPTY);
        return products.stream()
                .map(product -> itemAssembler.toRow(product, context, UserOrderItemAssembler.View.DETAIL))
                .toList();
    }

    /** 완료된 결제 → 살아 있는 결제 → 마지막 시도 순. */
    public Optional<Payment> representativePayment(Order order) {
        if (order.getPaidPaymentId() != null) {
            Optional<Payment> paid = paymentRepository.findById(order.getPaidPaymentId());
            if (paid.isPresent()) {
                return paid;
            }
        }
        Optional<Payment> ready = paymentRepository.findFirstByOrder_IdAndStatus(order.getId(), PaymentStatus.READY);
        if (ready.isPresent()) {
            return ready;
        }
        List<Payment> attempts = paymentRepository.findByOrder_IdOrderByAttemptDesc(order.getId());
        return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.get(0));
    }

    public OrderDto.PaymentInfo toPaymentInfo(Payment payment) {
        return OrderDto.PaymentInfo.builder()
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus())
                .method(payment.getMethod())
                .methodLabel(payment.methodLabel())
                .cardIssuer(payment.getCardIssuer())
                .easyPayProvider(payment.getEasyPayProvider())
                .amount(payment.getAmount().longValue())
                .paidAt(payment.getPaidAt())
                .build();
    }

    /**
     * 결제 전이면 언제나, 결제 후면 전 하위주문이 준비 시작 전(NEW)일 때 — 취소 처리 중이면 아니다(5-6 · 9-1 ⑤).
     * 서버 취소 게이트({@code CheckoutService.claimUserCancel} · 34 설계서 5-2)와 같은 판정이어야 한다 —
     * 어긋나면 앱이 취소 버튼을 그리고 서버가 409 를 낸다. 주문 내역 목록({@code UserOrderQueryService})도 이 판정을 쓴다 —
     * 항목·그룹은 호출자가 읽어 둔 것을 넘긴다(목록에서 주문마다 지연 로딩하지 않게).
     */
    boolean isCancellable(Order order, Payment payment, Collection<OrderProduct> products,
                          Collection<OrderDeliveryGroup> groups) {
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            return true;
        }
        if (order.getStatus() != OrderStatus.PAID) {
            return false;
        }
        return payment != null && payment.getStatus() == PaymentStatus.PAID
                && products.stream().allMatch(p -> p.getStatus() == OrderProductStatus.PAID)
                && groups.stream().allMatch(g -> g.getFulfillmentStatus() == FulfillmentStatus.NEW
                || g.getFulfillmentStatus() == FulfillmentStatus.PENDING);
    }

    /**
     * 배송지 변경 가능(C10 설계서 3-6) — 결제된 주문이고 취소되지 않은 하위주문이 전부 준비 시작 전(NEW).
     * 변경 API 와 같은 판정이어야 한다 — 버튼 노출의 정본은 서버다.
     */
    static boolean isAddressChangeable(Order order, Collection<OrderDeliveryGroup> groups) {
        if (order.getStatus() != OrderStatus.PAID || order.getPaidAt() == null) {
            return false;
        }
        List<OrderDeliveryGroup> alive = groups.stream()
                .filter(g -> g.getFulfillmentStatus() != FulfillmentStatus.CANCELLED).toList();
        return !alive.isEmpty() && alive.stream().allMatch(g -> g.getFulfillmentStatus() == FulfillmentStatus.NEW);
    }

    private OrderDto.Item toItem(OrderProduct product) {
        int regular = product.getRegularPrice() != null ? product.getRegularPrice() : product.getPrice();
        return OrderDto.Item.builder()
                .orderProductId(product.getId())
                .variantId(product.getVariantId())
                .productId(product.getVariant() != null && product.getVariant().getProduct() != null
                        ? product.getVariant().getProduct().getProductId() : null)
                .productName(product.getProductName())
                .optionName(product.getOptionName())
                .thumbnailUrl(product.getImageUrl())
                .quantity(product.getQuantity())
                .price(ProductDto.PriceInfo.builder()
                        .regularPrice(regular)
                        .salePrice(product.getPrice())
                        .discountRate(DiscountRate.of(regular, product.getPrice()))
                        .build())
                .status(product.getStatus().name())
                .build();
    }

    public static String ctaLabel(long totalAmount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(totalAmount) + "원 결제하기";
    }
}
