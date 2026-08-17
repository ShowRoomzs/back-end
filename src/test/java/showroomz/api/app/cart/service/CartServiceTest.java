package showroomz.api.app.cart.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.app.cart.dto.CartDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.cart.entity.Cart;
import showroomz.domain.cart.repository.CartRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * C8 장바구니 — 담은 시점과 결제 시점 사이의 시차를 서버가 걸러 준다.
 *
 * <p>검증 대상은 셋이다. ① 담은 뒤 마감·품절된 항목은 목록에 남되 선택에서 빠져 합계·배송비에
 * 들어가지 않는다. ② 합계는 선택된 항목만으로 계산된다. ③ 살 수 없는 항목은 수량·옵션을
 * 바꿀 수 없다(화면이 컨트롤을 비활성으로 그리는 것과 같은 선에서 서버도 막는다).
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final String USERNAME = "user-1";

    @Mock
    private CartRepository cartRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private CartService cartService;

    private Users user;

    @BeforeEach
    void setUp() {
        user = new Users();
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "username", USERNAME);
    }

    @Test
    @DisplayName("담은 뒤 공구가 마감된 항목은 목록에 남되 선택에서 빠져 합계에 들어가지 않는다")
    void closedGroupBuyItemStaysInListButNotInTotal() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        Cart alive = cart(10L, variant(1L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 38000, 24900, 10), 1);
        Cart closed = cart(11L, variant(2L, product(market, ProductGroupBuyStatus.NOT_CONNECTED, 10), 26000, 17500, 10), 1);

        givenCart(List.of(alive, closed));

        CartDto.CartListResponse response = cartService.getCart(USERNAME, null);

        CartDto.CartItem closedItem = itemOf(response, 11L);
        assertThat(closedItem.getAvailability().getIsPurchasable()).isFalse();
        assertThat(closedItem.getAvailability().getReason()).isEqualTo("GROUP_BUY_CLOSED");
        assertThat(closedItem.getAvailability().getLabel()).isEqualTo("마감");
        assertThat(closedItem.getIsSelected()).isFalse();

        assertThat(response.getSummary().getTotalCount()).isEqualTo(2);
        assertThat(response.getSummary().getSelectableCount()).isEqualTo(1);
        assertThat(response.getSummary().getSelectedCount()).isEqualTo(1);
        // 살아 있는 항목 24,900원만 계산된다 — 마감 항목의 17,500원은 빠진다
        assertThat(response.getSummary().getSaleTotal()).isEqualTo(24_900L);
    }

    @Test
    @DisplayName("품절 항목은 사유를 달고 목록에 남는다 — 말없이 지우면 합계가 줄어든 이유를 알 수 없다")
    void soldOutItemKeepsItsPlaceWithReason() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        Cart soldOut = cart(11L, variant(2L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 0), 26000, 17500, 0), 1);

        givenCart(List.of(soldOut));

        CartDto.CartListResponse response = cartService.getCart(USERNAME, null);

        CartDto.CartItem item = itemOf(response, 11L);
        assertThat(item.getAvailability().getReason()).isEqualTo("SOLD_OUT");
        assertThat(item.getAvailability().getMessage()).isEqualTo("품절되어 주문할 수 없어요");
        assertThat(response.getSummary().getSelectedCount()).isZero();
        assertThat(response.getSummary().getFinalTotal()).isZero();
    }

    @Test
    @DisplayName("선택한 항목만 합계에 들어간다 — 체크를 풀면 배송비도 함께 빠진다")
    void summaryCountsOnlySelectedItems() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        Cart first = cart(10L, variant(1L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 38000, 24900, 10), 1);
        Cart second = cart(11L, variant(2L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 26000, 17500, 10), 1);

        givenCart(List.of(first, second));

        CartDto.CartListResponse response = cartService.getCart(USERNAME, List.of(10L));

        assertThat(response.getSummary().getSelectedCount()).isEqualTo(1);
        assertThat(response.getSummary().getSaleTotal()).isEqualTo(24_900L);
        assertThat(response.getSummary().getDeliveryFeeTotal()).isEqualTo(3_000L);
        assertThat(response.getSummary().getFinalTotal()).isEqualTo(27_900L);
        assertThat(itemOf(response, 11L).getIsSelected()).isFalse();
    }

    @Test
    @DisplayName("아무것도 선택하지 않으면 합계도 배송비도 0이다 — [주문하기]가 비활성인 상태")
    void emptySelectionChargesNothing() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        givenCart(List.of(cart(10L, variant(1L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 38000, 24900, 10), 1)));

        CartDto.CartListResponse response = cartService.getCart(USERNAME, List.of());

        assertThat(response.getSummary().getSelectedCount()).isZero();
        assertThat(response.getSummary().getFinalTotal()).isZero();
        assertThat(response.getGroups().get(0).getShipping().getHasSelectedItems()).isFalse();
        assertThat(response.getGroups().get(0).getShipping().getChargedDeliveryFee()).isZero();
        assertThat(response.getGroups().get(0).getShipping().getAmountToFreeShipping()).isNull();
    }

    @Test
    @DisplayName("공구는 쇼룸 단위로 묶이고, 그룹 끝에 무료배송까지 남은 금액이 붙는다")
    void groupsCarryTheirOwnShippingLine() {
        Market jenny = market(5L, "제니의 뷰티룸", 3000, 30000);
        Market mia = market(6L, "미아 스킨노트", 3000, 30000);

        Cart jennyItem = cart(10L, variant(1L, product(jenny, ProductGroupBuyStatus.IN_PROGRESS, 10), 38000, 24900, 10), 1);
        Cart miaItem = cart(11L, variant(2L, product(mia, ProductGroupBuyStatus.IN_PROGRESS, 10), 24000, 16800, 10), 2);

        givenCart(List.of(jennyItem, miaItem));

        CartDto.CartListResponse response = cartService.getCart(USERNAME, null);

        assertThat(response.getGroups()).hasSize(2);

        CartDto.CartGroup jennyGroup = groupOf(response, 5L);
        assertThat(jennyGroup.getMarketName()).isEqualTo("제니의 뷰티룸");
        assertThat(jennyGroup.getIsClosed()).isFalse();
        assertThat(jennyGroup.getShipping().getIsFreeShipping()).isFalse();
        assertThat(jennyGroup.getShipping().getChargedDeliveryFee()).isEqualTo(3000);
        assertThat(jennyGroup.getShipping().getAmountToFreeShipping()).isEqualTo(5_100L);

        // 16,800 x 2 = 33,600원 → 무료배송 기준 30,000원을 넘겨 배송비가 붙지 않는다
        CartDto.CartGroup miaGroup = groupOf(response, 6L);
        assertThat(miaGroup.getShipping().getIsFreeShipping()).isTrue();
        assertThat(miaGroup.getShipping().getChargedDeliveryFee()).isZero();
        assertThat(miaGroup.getShipping().getAmountToFreeShipping()).isNull();

        // 배송비는 그룹마다 따로 매겨진다 — 제니 그룹의 3,000원만 부과된다
        assertThat(response.getSummary().getDeliveryFeeTotal()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("그룹 전체가 마감이면 isClosed가 켜진다 — 끝난 공구에 D-day를 남기지 않기 위해서다")
    void groupIsMarkedClosedWhenEveryItemIsClosed() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        givenCart(List.of(
                cart(10L, variant(1L, product(market, ProductGroupBuyStatus.NOT_CONNECTED, 10), 38000, 24900, 10), 1),
                cart(11L, variant(2L, product(market, ProductGroupBuyStatus.NOT_CONNECTED, 10), 26000, 17500, 10), 1)
        ));

        CartDto.CartListResponse response = cartService.getCart(USERNAME, null);

        assertThat(response.getGroups().get(0).getIsClosed()).isTrue();
    }

    @Test
    @DisplayName("마감된 항목은 수량을 바꿀 수 없다 — 못 사는 상품에 조작할 컨트롤을 남기지 않는다")
    void closedItemCannotBeUpdated() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        Cart closed = cart(10L, variant(1L, product(market, ProductGroupBuyStatus.NOT_CONNECTED, 10), 38000, 24900, 10), 1);

        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(cartRepository.findByIdAndUser(10L, user)).willReturn(Optional.of(closed));

        CartDto.UpdateCartRequest request = CartDto.UpdateCartRequest.builder().quantity(2).build();

        assertThatThrownBy(() -> cartService.updateCart(USERNAME, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("바꾸려는 옵션이 품절이면 옵션 변경도 막는다")
    void optionChangeToSoldOutVariantIsRejected() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        Product product = product(market, ProductGroupBuyStatus.IN_PROGRESS, 10);
        Cart alive = cart(10L, variant(1L, product, 38000, 24900, 10), 1);
        ProductVariant soldOutVariant = variant(2L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 0), 38000, 24900, 0);

        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(cartRepository.findByIdAndUser(10L, user)).willReturn(Optional.of(alive));
        given(productVariantRepository.findByVariantId(2L)).willReturn(Optional.of(soldOutVariant));

        CartDto.UpdateCartRequest request = CartDto.UpdateCartRequest.builder().variantId(2L).build();

        assertThatThrownBy(() -> cartService.updateCart(USERNAME, 10L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("마감된 공구의 상품은 애초에 담기지 않는다")
    void closedProductCannotBeAdded() {
        Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
        ProductVariant closedVariant = variant(1L, product(market, ProductGroupBuyStatus.NOT_CONNECTED, 10), 38000, 24900, 10);

        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(productVariantRepository.findByVariantId(1L)).willReturn(Optional.of(closedVariant));

        List<CartDto.AddCartRequest> requests = List.of(
                CartDto.AddCartRequest.builder().productId(1L).variantId(1L).quantity(1).build()
        );

        assertThatThrownBy(() -> cartService.addCartBulk(USERNAME, requests))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_PURCHASABLE);
    }

    // ------------------------------------------------------------------ 픽스처

    private void givenCart(List<Cart> carts) {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(cartRepository.findAllByUser(user)).willReturn(carts);
    }

    private Market market(Long id, String name, Integer deliveryFee, Integer freeShippingThreshold) {
        Market market = new Market();
        market.setId(id);
        market.setMarketName(name);
        market.setDefaultDeliveryFee(deliveryFee);
        market.setFreeShippingThreshold(freeShippingThreshold);
        return market;
    }

    private Product product(Market market, ProductGroupBuyStatus groupBuyStatus, int stock) {
        Product product = new Product();
        product.setProductId(1024L);
        product.setName("시카 리페어 앰플 30ml 리필 2개 세트");
        product.setMarket(market);
        product.setGroupBuyStatus(groupBuyStatus);
        product.setDisplayStatus(ProductDisplayStatus.DISPLAY);
        product.setIsOutOfStockForced(false);
        product.setRegularPrice(38000);
        product.setSalePrice(24900);
        return product;
    }

    private ProductVariant variant(Long variantId, Product product, int regularPrice, int salePrice, int stock) {
        ProductVariant variant = new ProductVariant(product, "기본", regularPrice, salePrice, stock, true);
        variant.setVariantId(variantId);
        return variant;
    }

    private Cart cart(Long cartId, ProductVariant variant, int quantity) {
        Cart cart = new Cart(user, variant, quantity);
        ReflectionTestUtils.setField(cart, "id", cartId);
        return cart;
    }

    private CartDto.CartItem itemOf(CartDto.CartListResponse response, long cartId) {
        return response.getGroups().stream()
                .flatMap(group -> group.getItems().stream())
                .filter(item -> item.getCartId() == cartId)
                .findFirst()
                .orElseThrow();
    }

    private CartDto.CartGroup groupOf(CartDto.CartListResponse response, long marketId) {
        return response.getGroups().stream()
                .filter(group -> group.getMarketId() == marketId)
                .findFirst()
                .orElseThrow();
    }
}
