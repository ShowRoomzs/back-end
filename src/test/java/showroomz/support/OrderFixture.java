package showroomz.support;

import org.springframework.transaction.support.TransactionTemplate;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.order.entity.Order;
import showroomz.domain.order.entity.OrderProduct;
import showroomz.domain.order.repository.OrderProductRepository;
import showroomz.domain.order.repository.OrderRepository;
import showroomz.domain.order.type.OrderProductStatus;
import showroomz.domain.product.entity.ProductVariant;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 주문 픽스처(선행 수정 계획서 3-5) — 「결제 완료 주문 1건 + 상품 N줄」을 만든다. 리뷰·문의·어드민 회원 목록 테스트가 쓴다.
 * 주문번호·금액·배송지는 실데이터 모양으로 채우고, 상태는 운영과 같은 조건부 UPDATE({@code markPaid})로 올린다.
 */
public class OrderFixture {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final OrderRepository orderRepository;
    private final OrderProductRepository orderProductRepository;
    private final TransactionTemplate transactionTemplate;

    public OrderFixture(OrderRepository orderRepository, OrderProductRepository orderProductRepository,
                        TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.orderProductRepository = orderProductRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /** 결제 대기 주문(PAYMENT_PENDING) — 상품 1줄. */
    public Order pendingOrder(Users user, ProductVariant variant, int quantity, int unitPrice) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int total = unitPrice * quantity;
        Order order = Order.create(user, "FX%s-%06d".formatted(now.toLocalDate().toString().replace("-", ""), SEQ.incrementAndGet()),
                new Order.Totals(total, 0, 0, total),
                new Order.AddressSnapshot("김수민", "010-1234-5678", "06234", "서울 강남구 테헤란로 000", "12층"),
                "문 앞에 놓아주세요", variant.getProduct().getName(), null, now.plusMinutes(30));
        orderRepository.save(order);
        orderProductRepository.save(OrderProduct.builder()
                .order(order)
                .variant(variant)
                .productName(variant.getProduct().getName())
                .optionName(variant.getName())
                .quantity(quantity)
                .price(unitPrice)
                .regularPrice(variant.getRegularPrice())
                .imageUrl(variant.getProduct().getThumbnailUrl())
                .orderDate(now)
                .status(OrderProductStatus.PENDING)
                .build());
        return order;
    }

    /** 결제 완료 주문(PAID) — 상품 1줄이 {@code productStatus}로. */
    public Order paidOrder(Users user, ProductVariant variant, int quantity, int unitPrice, OrderProductStatus productStatus) {
        Order order = pendingOrder(user, variant, quantity, unitPrice);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        // 조건부 UPDATE 는 @Modifying(flush) 라 트랜잭션이 필요하다 — 운영 코드는 서비스 트랜잭션 안에서 부른다.
        transactionTemplate.executeWithoutResult(tx -> {
            orderRepository.markPaid(order.getId(), null, now);
            orderProductRepository.transitionByOrder(order.getId(),
                    java.util.EnumSet.of(OrderProductStatus.PENDING), productStatus);
        });
        return orderRepository.findById(order.getId()).orElseThrow();
    }
}
