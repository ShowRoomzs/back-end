package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
                        .build())
                .payment(payment != null ? toPaymentInfo(payment) : null)
                .cancellable(isCancellable(order, payment))
                .build();
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

    /** 결제 전이면 언제나, 결제 후면 배송 전(지금은 상품 전부 PAID)일 때 — 취소 처리 중이면 아니다(5-6 · 9-1 ⑤). */
    private boolean isCancellable(Order order, Payment payment) {
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            return true;
        }
        if (order.getStatus() != OrderStatus.PAID) {
            return false;
        }
        return payment != null && payment.getStatus() == PaymentStatus.PAID
                && order.getOrderProducts().stream()
                .allMatch(p -> p.getStatus() == showroomz.domain.order.type.OrderProductStatus.PAID);
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
