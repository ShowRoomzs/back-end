package showroomz.api.app.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.app.order.dto.OrderDto;
import showroomz.api.app.order.service.OrderPricingCalculator.Line;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문서·주문 생성의 입력 두 갈래(장바구니 선택 · 바로 구매)를 계산기의 줄({@link Line})로 바꾼다(결제 계획서 5-2 · 4-6).
 *
 * <p>귀속은 진입 경로가 정한다 — 장바구니 줄은 {@code cart.group_buy_id}, 바로 구매는 요청의 {@code groupBuyId}(필수).
 * 서버가 「진행 중 공구 1개면 그것」을 추측하는 코드는 두지 않는다. 트랜잭션 안에서 불러야 한다(지연 로딩).
 */
@Component
@RequiredArgsConstructor
public class OrderLineLoader {

    /** 바로 구매 한 줄의 lineId — 장바구니 id 와 겹치지 않게 0 을 쓴다(호출 안에서 유일하면 된다). */
    public static final long DIRECT_LINE_ID = 0L;

    private final CartRepository cartRepository;
    private final ProductVariantRepository productVariantRepository;
    private final GroupBuyRepository groupBuyRepository;

    public List<Line> load(Users user, List<Long> cartItemIds, OrderDto.DirectItem direct) {
        boolean hasCart = cartItemIds != null && !cartItemIds.isEmpty();
        boolean hasDirect = direct != null;
        if (hasCart == hasDirect) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "cartItemIds 또는 direct 중 하나만 보내 주세요.");
        }
        return hasCart ? fromCart(user, cartItemIds) : fromDirect(direct);
    }

    private List<Line> fromCart(Users user, List<Long> cartItemIds) {
        // 중복 id 는 한 줄로 본다 — 같은 항목을 두 번 실어도 두 번 사지 않는다.
        List<Long> requested = new ArrayList<>(new LinkedHashSet<>(cartItemIds));
        List<Cart> carts = cartRepository.findByIdInAndUser(requested, user);
        if (carts.size() != requested.size()) {
            Set<Long> found = carts.stream().map(Cart::getId).collect(Collectors.toSet());
            List<Long> missing = requested.stream().filter(id -> !found.contains(id)).toList();
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND,
                    "장바구니 항목을 찾을 수 없습니다. cartItemIds: " + missing);
        }
        Map<Long, Cart> byId = carts.stream().collect(Collectors.toMap(Cart::getId, Function.identity()));
        List<Line> lines = new ArrayList<>();
        for (Long id : requested) {
            Cart cart = byId.get(id);
            lines.add(new Line(cart.getId(), cart.getGroupBuy(), cart.getVariant(), cart.getQuantity()));
        }
        return lines;
    }

    private List<Line> fromDirect(OrderDto.DirectItem direct) {
        if (direct.getGroupBuyId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "공구를 지정해 주세요.");
        }
        ProductVariant variant = productVariantRepository.findByVariantId(direct.getVariantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        GroupBuy groupBuy = groupBuyRepository.findById(direct.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        int quantity = direct.getQuantity() != null ? direct.getQuantity() : 1;
        return List.of(new Line(DIRECT_LINE_ID, groupBuy, variant, quantity));
    }
}
