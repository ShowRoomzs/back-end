package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.service.OrderPricingCalculator.GroupShipping;
import showroomz.api.app.order.service.OrderPricingCalculator.Line;
import showroomz.api.app.order.service.OrderPricingCalculator.Pricing;
import showroomz.api.app.order.service.OrderPricingCalculator.Summary;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.app.order.dto.UserOrderDto;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.address.repository.DeliveryAddressRepository;
import showroomz.domain.cart.type.CartUnavailableReason;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver.GroupBuyPrice;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderDeliveryGroup;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderDeliveryGroupRepository;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.service.OrderNumberGenerator;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.order.type.OrderStatus;
import showroomz.domain.payment.entity.Payment;
import showroomz.domain.payment.entity.PaymentCancel;
import showroomz.domain.payment.repository.PaymentCancelRepository;
import showroomz.domain.payment.repository.PaymentRepository;
import showroomz.domain.payment.type.CancelRequester;
import showroomz.domain.payment.type.CardIssuer;
import showroomz.domain.payment.type.EasyPayProvider;
import showroomz.domain.payment.type.PaymentMethod;
import showroomz.domain.payment.type.PaymentStatus;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductOption;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.global.config.properties.OrderProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.payment.portone.PortOnePaymentGateway;
import showroomz.global.utils.DiscountRate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * C9 주문서·주문 생성·취소의 <b>트랜잭션 단위</b>(결제 계획서 5-2 · 5-3 · 4-7 ①④⑦). 외부 HTTP(포트원)는 여기 없다 —
 * 커밋 뒤 부르는 것은 {@link OrderCommandService}가 한다.
 *
 * <p>금액은 장바구니와 같은 {@link OrderPricingCalculator} 한 식에서 나온다(2-3 ①). 앱이 보낸 금액은 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final UserRepository userRepository;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final OrderRepository orderRepository;
    private final OrderDeliveryGroupRepository deliveryGroupRepository;
    private final OrderProductRepository orderProductRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final OrderLineLoader lineLoader;
    private final OrderPricingCalculator pricingCalculator;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderAssembler assembler;
    private final StockReleaser stockReleaser;
    private final PortOnePaymentGateway gateway;
    private final OrderProperties orderProperties;

    // ------------------------------------------------------------------ 주문서

    /** 주문서 — 저장하지 않는다. 살 수 없는 항목이 섞여 있으면 조용히 빼지 않고 막는다(금액이 이유 없이 줄면 안 된다). */
    @Transactional(readOnly = true)
    public OrderDto.CheckoutResponse checkout(Long userId, OrderDto.CheckoutRequest request) {
        Users user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        Prepared prepared = prepare(user, request.getCartItemIds(), request.getDirect(), now);

        DeliveryAddress address = resolveAddress(user, request.getDeliveryAddressId(), false);
        return OrderDto.CheckoutResponse.builder()
                .deliveryAddress(address != null ? toAddressInfo(address) : null)
                .memoPresets(OrderDto.MEMO_PRESETS)
                .groups(prepared.groups().stream().map(group -> toGroupDto(group, prepared.pricing())).toList())
                .summary(toSummary(prepared.summary(), prepared.lines().size()))
                .paymentMethods(availablePaymentMethods())
                .ctaLabel(OrderAssembler.ctaLabel(prepared.summary().finalTotal()))
                .build();
    }

    // ------------------------------------------------------------------ 주문 생성(T2)

    /**
     * T2 — 항목 검증 → 금액 계산 → 채번(T1, REQUIRES_NEW) → orders INSERT(멱등키 UK 는 여기서 flush 로 즉시 검사) →
     * 재고 차감(variant_id 오름차순) → 그룹·상품·payment READY. 어느 하나라도 실패하면 전부 롤백된다(재고 포함).
     * 유니크 위반은 {@code DataIntegrityViolationException}으로 호출자에게 올라가고, 호출자가 기존 주문을 다시 읽는다.
     */
    @Transactional
    public CreatedOrder createOrderTx(Long userId, OrderDto.CreateOrderRequest request, LocalDateTime now) {
        Users user = requireUser(userId);
        PaymentChoice choice = requirePaymentChoice(request.getPayment());
        Prepared prepared = prepare(user, request.getCartItemIds(), request.getDirect(), now);
        DeliveryAddress address = resolveAddress(user, request.getDeliveryAddressId(), true);

        Summary summary = prepared.summary();
        if (request.getExpectedTotalAmount() != null && request.getExpectedTotalAmount() != summary.finalTotal()) {
            throw new BusinessException(ErrorCode.ORDER_AMOUNT_CHANGED);
        }

        String orderNumber = orderNumberGenerator.generate(now);
        List<String> productNames = prepared.lines().stream()
                .map(line -> line.variant().getProduct().getName()).toList();
        Order order = Order.create(user, orderNumber,
                new Order.Totals((int) summary.regularTotal(), (int) summary.discountTotal(),
                        (int) summary.deliveryFeeTotal(), (int) summary.finalTotal()),
                new Order.AddressSnapshot(address.getRecipientName(), address.getPhoneNumber(), address.getZipCode(),
                        address.getAddress(), address.getDetailAddress()),
                request.getDeliveryMemo() != null ? request.getDeliveryMemo() : address.getMemo(),
                Order.orderNameOf(productNames),
                request.getIdempotencyKey(),
                now.plusMinutes(orderProperties.getPaymentTimeoutMinutes()));
        // 멱등키 유니크 위반을 트랜잭션 안에서 즉시 드러낸다(선행 수정 계획서 C2) — 커밋 시점에 터지면 잡을 수 없다.
        orderRepository.saveAndFlush(order);

        reserveStock(prepared.lines());

        for (LineGroup group : prepared.groups()) {
            GroupShipping shipping = group.shipping();
            OrderDeliveryGroup deliveryGroup = deliveryGroupRepository.save(OrderDeliveryGroup.builder()
                    .order(order)
                    .groupBuy(group.groupBuy())
                    .market(group.market())
                    .productTotal((int) shipping.selectedProductTotal())
                    .deliveryFee(shipping.chargedDeliveryFee())
                    // 무료배송이어도 원래 배송비를 남긴다 — 반품 차감 · 재발송비가 주문 시점 값을 쓴다(앱 클레임 설계서 1-4).
                    .baseDeliveryFee(shipping.deliveryFee())
                    .freeShippingApplied(shipping.isFreeShipping())
                    .marketName(group.market() != null ? group.market().getMarketName() : null)
                    .groupBuyNumber(group.groupBuy().getGroupBuyNumber())
                    .build());
            for (Line line : group.lines()) {
                GroupBuyPrice price = prepared.pricing().priceOf(line);
                Product product = line.variant().getProduct();
                orderProductRepository.save(OrderProduct.builder()
                        .order(order)
                        .deliveryGroup(deliveryGroup)
                        .groupBuy(group.groupBuy())
                        .variant(line.variant())
                        .productName(product.getName())
                        .optionName(buildOptionName(line.variant()))
                        .quantity(line.quantity())
                        .price(price.salePrice())
                        .regularPrice(price.regularPrice())
                        .imageUrl(product.getThumbnailUrl())
                        .orderDate(now)
                        .cartId(line.lineId() == OrderLineLoader.DIRECT_LINE_ID ? null : line.lineId())
                        .build());
            }
        }

        Payment payment = paymentRepository.save(Payment.ready(order, 1, choice.method(), choice.cardIssuer(),
                choice.easyPayProvider(), order.getTotalAmount(), choice.channelKey()));
        return new CreatedOrder(order.getId(), payment.getPaymentId(), payment.getAmount());
    }

    /** T13 — 이전 READY 를 SUPERSEDED 로 내린 뒤 새 시도를 만든다. 주문 행을 잠가 같은 주문의 동시 재시도를 직렬화한다. */
    @Transactional
    public CreatedOrder retryPaymentTx(Long userId, Long orderId, OrderDto.PaymentSelection selection, LocalDateTime now) {
        Order order = orderRepository.findForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireOwner(order, userId);
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_IN_PROGRESS);
        }
        if (order.getStatus().isClosed() || order.isExpired(now)) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CLOSED);
        }
        PaymentChoice choice = requirePaymentChoice(selection);

        paymentRepository.closeReadyOfOrder(orderId, PaymentStatus.SUPERSEDED);
        Order attached = orderRepository.getReferenceById(orderId);
        int attempt = paymentRepository.findMaxAttempt(orderId) + 1;
        Payment payment = paymentRepository.save(Payment.ready(attached, attempt, choice.method(), choice.cardIssuer(),
                choice.easyPayProvider(), order.getTotalAmount(), choice.channelKey()));
        return new CreatedOrder(orderId, payment.getPaymentId(), payment.getAmount());
    }

    /** T3 / T14 — 사전 등록 성공 기록. */
    @Transactional
    public void markPreRegistered(String paymentId, LocalDateTime at) {
        paymentRepository.markPreRegistered(paymentId, at);
    }

    /** 멱등 재요청 — 같은 (user, key) 의 주문. */
    @Transactional(readOnly = true)
    public Optional<ExistingOrder> findExisting(Long userId, String idempotencyKey) {
        return orderRepository.findByUser_IdAndIdempotencyKey(userId, idempotencyKey)
                .map(order -> {
                    Payment payment = assembler.representativePayment(order).orElse(null);
                    return new ExistingOrder(order.getId(), order.getStatus(), order.isExpired(LocalDateTime.now()),
                            payment != null ? payment.getPaymentId() : null,
                            payment != null ? payment.getAmount() : null,
                            payment != null && payment.getPreRegisteredAt() != null,
                            payment != null ? payment.getStatus() : null);
                });
    }

    @Transactional(readOnly = true)
    public OrderDto.CreateOrderResponse buildCreateResponse(Long orderId, String paymentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        return assembler.toCreateResponse(order, payment);
    }

    // ------------------------------------------------------------------ 상세

    @Transactional(readOnly = true)
    public OrderDto.OrderDetailResponse getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireOwner(order, userId);
        return assembler.toDetail(order);
    }

    // ------------------------------------------------------------------ 배송지 변경

    /**
     * 주문 배송지 변경(C10 설계서 3-6) — 결제된 주문이고 취소되지 않은 하위주문이 전부 준비 시작 전(NEW)일 때만.
     * 주문은 배송지 id 를 참조하지 않고 스냅샷을 든다 — 고른 배송지의 값을 복사한다.
     *
     * <p>브랜드의 준비 시작 · 발주서 다운로드와 겹친다. 하위주문을 id 오름차순으로 잠근 뒤 상태를 본다 —
     * 발주서와 같은 잠금 순서(하위주문 → 주문)라 교착이 없고, 준비 시작이 먼저 커밋되면 변경이 진다.
     */
    @Transactional
    public UserOrderDto.MaskedAddress changeDeliveryAddressTx(Long userId, Long orderId, Long addressId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireOwner(order, userId);
        // 남의 배송지는 있는지도 알리지 않는다.
        DeliveryAddress address = deliveryAddressRepository.findById(addressId)
                .filter(found -> found.getUser().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));

        List<OrderDeliveryGroup> groups = deliveryGroupRepository.findByOrderIdForUpdate(orderId);
        if (!OrderAssembler.isAddressChangeable(order, groups)) {
            throw new BusinessException(ErrorCode.ORDER_ADDRESS_NOT_CHANGEABLE);
        }
        order.changeDeliveryAddress(new Order.AddressSnapshot(address.getRecipientName(), address.getPhoneNumber(),
                address.getZipCode(), address.getAddress(), address.getDetailAddress()), address.getMemo());
        return OrderAddressMasker.mask(order);
    }

    // ------------------------------------------------------------------ 취소

    /** 결제 전 취소 — 조건부 전이 + 재고 복원 + 살아 있는 결제 CANCELLED. 주문이 PAYMENT_PENDING 이 아니면 false. */
    @Transactional
    public boolean cancelPendingTx(Long userId, Long orderId, String reason, LocalDateTime now) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireOwner(order, userId);
        if (orderRepository.cancelPending(orderId, now, reason) != 1) {
            return false;
        }
        stockReleaser.release(orderId, now);
        paymentRepository.closeReadyOfOrder(orderId, PaymentStatus.CANCELLED);
        return true;
    }

    /**
     * T8 — 결제 후 취소 선점. 주문 PAID ∧ 배송 전 검증 → payment PAID → CANCEL_REQUESTED(0행이면 이미 취소 처리 중) →
     * payment_cancel REQUESTED. 포트원 호출은 커밋 뒤 호출자가 한다.
     */
    @Transactional
    public UserCancelClaim claimUserCancel(Long userId, Long orderId, String reason, LocalDateTime now) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        requireOwner(order, userId);
        if (order.getStatus() != OrderStatus.PAID || order.getPaidPaymentId() == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        // 소비자 단순 취소는 전 하위주문이 준비 시작 전(NEW)일 때만(34 설계서 5-2 · 약관 제17조②).
        // 준비 시작 이후는 취소 요청 → 브랜드 승인·거부 경로다. 선점과 준비 시작의 레이스에서
        // 준비 시작이 먼저 커밋되면 취소가 진다 — 약관의 방향과 일치한다.
        if (deliveryGroupRepository.countPreparedByOrder(orderId) > 0) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_WINDOW_CLOSED);
        }
        String paymentId = order.getPaidPaymentId();
        LocalDateTime retryAt = now.plusMinutes(orderProperties.getCancelRetryBaseMinutes());
        if (paymentRepository.claimCancel(paymentId, EnumSet.of(PaymentStatus.PAID), null, now, retryAt) != 1) {
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        paymentCancelRepository.save(PaymentCancel.requested(payment, payment.getAmount(), reason, CancelRequester.USER, now));
        return new UserCancelClaim(paymentId, payment.getAmount());
    }

    // ------------------------------------------------------------------ 내부

    /** 줄 적재 → 가격 → 구매 가능 판정 → 그룹·합계. 주문서와 주문 생성이 같은 순서로 같은 식을 탄다. */
    private Prepared prepare(Users user, List<Long> cartItemIds, OrderDto.DirectItem direct, LocalDateTime now) {
        List<Line> lines = lineLoader.load(user, cartItemIds, direct);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEMS_EMPTY);
        }
        Pricing pricing = pricingCalculator.price(lines, now);
        for (Line line : lines) {
            pricingCalculator.requireWithinQuantityLimit(line.quantity());
            requirePurchasable(line, pricing);
        }
        Set<Long> selected = lines.stream().map(Line::lineId).collect(Collectors.toSet());
        Summary summary = pricingCalculator.summarize(lines, selected, pricing);

        Map<Long, List<Line>> byGroupBuy = new LinkedHashMap<>();
        for (Line line : lines) {
            byGroupBuy.computeIfAbsent(line.groupBuy().getId(), key -> new ArrayList<>()).add(line);
        }
        List<LineGroup> groups = new ArrayList<>();
        byGroupBuy.forEach((groupBuyId, groupLines) -> {
            GroupBuy groupBuy = groupLines.get(0).groupBuy();
            Market market = groupLines.get(0).market();
            groups.add(new LineGroup(groupBuy, market, groupLines,
                    pricingCalculator.groupShipping(market, groupLines, selected, pricing)));
        });
        return new Prepared(lines, pricing, summary, groups);
    }

    /** 판정 순서 「공구 → 계약 옵션 → 상품 → 재고」 — {@code CartService.requirePurchasable}과 같다(4-6). */
    private void requirePurchasable(Line line, Pricing pricing) {
        GroupBuy groupBuy = line.groupBuy();
        if (groupBuy == null || !groupBuy.isOngoing(pricing.now())) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE, CartUnavailableReason.GROUP_BUY_CLOSED.getMessage());
        }
        if (pricing.priceOf(line) == null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE, OrderPricingCalculator.NOT_IN_GROUP_BUY_MESSAGE);
        }
        CartUnavailableReason reason = pricingCalculator.unavailableReason(line.variant());
        if (reason != null) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_PURCHASABLE, reason.getMessage());
        }
        int stock = line.variant().getStock() != null ? line.variant().getStock() : 0;
        if (line.quantity() > stock) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
    }

    /** 재고 차감 — 옵션별 합산 후 variant_id 오름차순(4-3). 0행이면 롤백된다. */
    private void reserveStock(List<Line> lines) {
        Map<Long, Integer> byVariant = new TreeMap<>();
        for (Line line : lines) {
            byVariant.merge(line.variant().getVariantId(), line.quantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : byVariant.entrySet()) {
            if (productVariantRepository.reserveStock(entry.getKey(), entry.getValue()) != 1) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }

    private DeliveryAddress resolveAddress(Users user, Long deliveryAddressId, boolean required) {
        DeliveryAddress address;
        if (deliveryAddressId != null) {
            address = deliveryAddressRepository.findById(deliveryAddressId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
            if (!address.getUser().getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.ADDRESS_ACCESS_DENIED);
            }
        } else {
            address = deliveryAddressRepository.findByUserAndIsDefaultTrue(user).orElse(null);
        }
        if (address == null && required) {
            throw new BusinessException(ErrorCode.ORDER_ADDRESS_REQUIRED);
        }
        return address;
    }

    private PaymentChoice requirePaymentChoice(OrderDto.PaymentSelection selection) {
        if (selection == null || selection.getMethod() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "결제수단을 선택해 주세요.");
        }
        PaymentMethod method = selection.getMethod();
        CardIssuer cardIssuer = null;
        EasyPayProvider provider = null;
        if (method == PaymentMethod.CARD) {
            if (selection.getCardIssuer() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "카드사를 선택해 주세요.");
            }
            cardIssuer = selection.getCardIssuer();
        } else {
            if (selection.getEasyPayProvider() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "간편결제를 선택해 주세요.");
            }
            provider = selection.getEasyPayProvider();
        }
        String channelKey = gateway.channelKeyFor(method, provider)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE));
        return new PaymentChoice(method, cardIssuer, provider, channelKey);
    }

    /** 지금 열려 있는 채널만 — 채널키가 "-" 로 꺼진 간편결제는 빠져 앱이 고장 난 버튼을 그리지 않는다(5-2). */
    private OrderDto.PaymentMethods availablePaymentMethods() {
        List<CardIssuer> cards = gateway.channelKeyFor(PaymentMethod.CARD, null).isPresent()
                ? Arrays.asList(CardIssuer.values()) : List.of();
        List<EasyPayProvider> providers = Arrays.stream(EasyPayProvider.values())
                .filter(provider -> gateway.channelKeyFor(PaymentMethod.EASY_PAY, provider).isPresent())
                .toList();
        return OrderDto.PaymentMethods.builder().cardIssuers(cards).easyPayProviders(providers).build();
    }

    private OrderDto.Group toGroupDto(LineGroup group, Pricing pricing) {
        GroupShipping shipping = group.shipping();
        return OrderDto.Group.builder()
                .marketId(group.market() != null ? group.market().getId() : null)
                .marketName(group.market() != null ? group.market().getMarketName() : null)
                .groupBuyId(group.groupBuy().getId())
                .groupBuyNumber(group.groupBuy().getGroupBuyNumber())
                .items(group.lines().stream().map(line -> toItemDto(line, pricing)).toList())
                .shipping(OrderDto.Shipping.builder()
                        .productTotal(shipping.selectedProductTotal())
                        .deliveryFee(shipping.chargedDeliveryFee())
                        .freeShippingThreshold(shipping.freeShippingThreshold())
                        .isFreeShipping(shipping.isFreeShipping())
                        .build())
                .build();
    }

    private OrderDto.Item toItemDto(Line line, Pricing pricing) {
        ProductVariant variant = line.variant();
        Product product = variant.getProduct();
        return OrderDto.Item.builder()
                .cartId(line.lineId() == OrderLineLoader.DIRECT_LINE_ID ? null : line.lineId())
                .variantId(variant.getVariantId())
                .productId(product.getProductId())
                .productName(product.getName())
                .optionName(buildOptionName(variant))
                .thumbnailUrl(product.getThumbnailUrl())
                .quantity(line.quantity())
                .price(priceInfo(line, pricing))
                .build();
    }

    private ProductDto.PriceInfo priceInfo(Line line, Pricing pricing) {
        GroupBuyPrice price = pricing.priceOf(line);
        return ProductDto.PriceInfo.builder()
                .regularPrice(price.regularPrice())
                .salePrice(price.salePrice())
                .discountRate(DiscountRate.of(price.regularPrice(), price.salePrice()))
                .build();
    }

    private OrderDto.Summary toSummary(Summary summary, int itemCount) {
        return OrderDto.Summary.builder()
                .productTotal(summary.regularTotal())
                .discountTotal(summary.discountTotal())
                .deliveryFeeTotal(summary.deliveryFeeTotal())
                .totalAmount(summary.finalTotal())
                .itemCount(itemCount)
                .build();
    }

    private OrderDto.AddressInfo toAddressInfo(DeliveryAddress address) {
        return OrderDto.AddressInfo.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .phoneNumber(address.getPhoneNumber())
                .zipCode(address.getZipCode())
                .address(address.getAddress())
                .detailAddress(address.getDetailAddress())
                .memo(address.getMemo())
                .isDefault(address.isDefault())
                .build();
    }

    /** 장바구니와 같은 형식 — 「용량: 30ml」·「색상: 베이지 / 사이즈: M」. 옵션 없는 단일 옵션은 옵션명(있으면). */
    static String buildOptionName(ProductVariant variant) {
        List<ProductOption> options = variant.getOptions();
        if (options == null || options.isEmpty()) {
            return variant.getName();
        }
        return options.stream()
                .sorted(Comparator.comparing(option -> option.getOptionGroup() != null
                        ? option.getOptionGroup().getOptionGroupId() : 0L))
                .map(option -> {
                    String groupName = option.getOptionGroup() != null ? option.getOptionGroup().getName() : null;
                    return (groupName != null ? groupName : "옵션") + ": " + option.getName();
                })
                .collect(Collectors.joining(" / "));
    }

    private Users requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private static void requireOwner(Order order, Long userId) {
        if (!order.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    // ------------------------------------------------------------------ 값 타입

    private record Prepared(List<Line> lines, Pricing pricing, Summary summary, List<LineGroup> groups) {
    }

    private record LineGroup(GroupBuy groupBuy, Market market, List<Line> lines, GroupShipping shipping) {
    }

    private record PaymentChoice(PaymentMethod method, CardIssuer cardIssuer, EasyPayProvider easyPayProvider, String channelKey) {
    }

    public record CreatedOrder(Long orderId, String paymentId, int amount) {
    }

    public record ExistingOrder(Long orderId, OrderStatus status, boolean expired, String paymentId, Integer amount,
                                boolean preRegistered, PaymentStatus paymentStatus) {
    }

    public record UserCancelClaim(String paymentId, int amount) {
    }
}
