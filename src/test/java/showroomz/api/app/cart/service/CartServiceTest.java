package showroomz.api.app.cart.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * C8 장바구니 — 담은 시점과 결제 시점 사이의 시차를 서버가 걸러 준다.
 *
 * <p>검증 대상은 셋이다. ① 담은 뒤 마감·품절된 항목은 목록에 남되 선택에서 빠져 합계·배송비에
 * 들어가지 않는다. ② 합계는 선택된 항목만으로 계산된다. ③ 살 수 없는 항목은 수량·옵션을
 * 바꿀 수 없다(화면이 컨트롤을 비활성으로 그리는 것과 같은 선에서 서버도 막는다).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    /**
     * 담기 — 같은 옵션을 다시 담으면 행이 늘지 않고 <b>수량이 합산</b>된다.
     *
     * <p>합산이 없으면 같은 상품이 장바구니에 여러 줄로 쌓여 수량 상한과 재고 검사가 줄마다
     * 따로 통과한다(1개씩 100번 담으면 재고 10개인 상품을 100개 담을 수 있다). 그래서 상한·재고
     * 검사는 <b>합산 후 수량</b>을 기준으로 한다.
     */
    @Nested
    @DisplayName("담기")
    class AddToCart {

        @Test
        @DisplayName("처음 담으면 새 항목으로 저장된다")
        void firstAddCreatesNewItem() {
            ProductVariant target = purchasableVariant(1L, 10);
            givenAddable(target);
            given(cartRepository.findByUserAndVariant(user, target)).willReturn(Optional.empty());

            cartService.addCart(USERNAME, addRequest(1L, 3));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("같은 옵션을 다시 담으면 행이 늘지 않고 수량이 합산된다")
        void repeatedAddMergesQuantity() {
            ProductVariant target = purchasableVariant(1L, 10);
            Cart existing = cart(10L, target, 2);
            givenAddable(target);
            given(cartRepository.findByUserAndVariant(user, target)).willReturn(Optional.of(existing));

            cartService.addCart(USERNAME, addRequest(1L, 3));

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(10L);
            assertThat(captor.getValue().getQuantity()).isEqualTo(5);
        }

        /** 합산 후 수량으로 재고를 보지 않으면 1개씩 나눠 담아 재고를 넘길 수 있다. */
        @Test
        @DisplayName("합산한 수량이 재고를 넘으면 거절한다 — 나눠 담아 재고를 넘길 수 없다")
        void mergedQuantityIsCheckedAgainstStock() {
            ProductVariant target = purchasableVariant(1L, 10);
            givenAddable(target);
            given(cartRepository.findByUserAndVariant(user, target)).willReturn(Optional.of(cart(10L, target, 9)));

            assertThatThrownBy(() -> cartService.addCart(USERNAME, addRequest(1L, 2)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

            verify(cartRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고와 같은 수량까지는 담을 수 있다")
        void quantityEqualToStockIsAllowed() {
            ProductVariant target = purchasableVariant(1L, 10);
            givenAddable(target);
            given(cartRepository.findByUserAndVariant(user, target)).willReturn(Optional.empty());

            cartService.addCart(USERNAME, addRequest(1L, 10));

            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("합산한 수량이 상한(99개)을 넘으면 거절한다")
        void mergedQuantityIsCheckedAgainstLimit() {
            ProductVariant target = purchasableVariant(1L, 500);
            givenAddable(target);
            given(cartRepository.findByUserAndVariant(user, target))
                    .willReturn(Optional.of(cart(10L, target, CartDto.MAX_QUANTITY)));

            assertThatThrownBy(() -> cartService.addCart(USERNAME, addRequest(1L, 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("없는 옵션은 담을 수 없다")
        void unknownVariantIsRejected() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(productVariantRepository.findByVariantId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addCart(USERNAME, addRequest(1L, 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VARIANT_NOT_FOUND);
        }

        @Test
        @DisplayName("품절된 옵션은 담을 수 없다")
        void soldOutVariantIsRejected() {
            ProductVariant soldOut = variant(1L, product(market(5L, "제니의 뷰티룸", 3000, 30000),
                    ProductGroupBuyStatus.IN_PROGRESS, 0), 38000, 24900, 0);
            givenAddable(soldOut);

            assertThatThrownBy(() -> cartService.addCart(USERNAME, addRequest(1L, 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_PURCHASABLE);
        }

        @Test
        @DisplayName("일괄 담기는 요청한 건수만큼 모두 담는다")
        void bulkAddSavesEveryRequest() {
            ProductVariant first = purchasableVariant(1L, 10);
            ProductVariant second = purchasableVariant(2L, 10);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(productVariantRepository.findByVariantId(1L)).willReturn(Optional.of(first));
            given(productVariantRepository.findByVariantId(2L)).willReturn(Optional.of(second));
            given(cartRepository.findByUserAndVariant(any(), any())).willReturn(Optional.empty());

            CartDto.BulkAddCartResponse response = cartService.addCartBulk(USERNAME,
                    List.of(addRequest(1L, 1), addRequest(2L, 2)));

            assertThat(response.getAddedCount()).isEqualTo(2);
            verify(cartRepository, times(2)).save(any(Cart.class));
        }

        @Test
        @DisplayName("빈 목록으로 일괄 담기를 부르면 거절한다")
        void emptyBulkRequestIsRejected() {
            assertThatThrownBy(() -> cartService.addCartBulk(USERNAME, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verify(userRepository, never()).findByUsername(anyString());
        }
    }

    /**
     * 수정 — 수량 변경과 옵션 변경이 같은 경로로 들어온다.
     *
     * <p>가장 까다로운 경우는 <b>이미 장바구니에 있는 옵션으로 바꾸는 것</b>이다. 그대로 두면
     * 같은 옵션이 두 줄이 되므로 기존 줄에 수량을 합치고 원래 줄을 지운다 — 이때도 합산 수량으로
     * 재고·상한을 다시 본다.
     */
    @Nested
    @DisplayName("수정")
    class UpdateCart {

        @Test
        @DisplayName("수량만 바꾸면 그 항목의 수량이 갱신된다")
        void quantityIsUpdated() {
            ProductVariant target = purchasableVariant(1L, 10);
            Cart item = cart(10L, target, 1);
            givenUpdatable(item);

            CartDto.UpdateCartResponse response = cartService.updateCart(
                    USERNAME, 10L, CartDto.UpdateCartRequest.builder().quantity(4).build());

            assertThat(item.getQuantity()).isEqualTo(4);
            assertThat(response.getQuantity()).isEqualTo(4);
        }

        @Test
        @DisplayName("수량이 재고를 넘으면 거절한다")
        void quantityOverStockIsRejected() {
            ProductVariant target = purchasableVariant(1L, 3);
            givenUpdatable(cart(10L, target, 1));

            assertThatThrownBy(() -> cartService.updateCart(
                    USERNAME, 10L, CartDto.UpdateCartRequest.builder().quantity(4).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);
        }

        /** 아무 값도 안 보낸 요청을 통과시키면 조회만 하고 성공을 돌려주는 무의미한 쓰기가 된다. */
        @Test
        @DisplayName("바꿀 값을 하나도 보내지 않으면 거절한다")
        void requestWithoutAnyChangeIsRejected() {
            givenUpdatable(cart(10L, purchasableVariant(1L, 10), 1));

            assertThatThrownBy(() -> cartService.updateCart(
                    USERNAME, 10L, CartDto.UpdateCartRequest.builder().build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("옵션을 장바구니에 없는 옵션으로 바꾸면 그 자리에서 옵션만 갈린다")
        void switchingToUnheldVariantKeepsSingleRow() {
            Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
            Product product = product(market, ProductGroupBuyStatus.IN_PROGRESS, 10);
            ProductVariant current = variant(1L, product, 38000, 24900, 10);
            ProductVariant next = variant(2L, product, 38000, 24900, 10);
            Cart item = cart(10L, current, 2);

            givenUpdatable(item);
            given(productVariantRepository.findByVariantId(2L)).willReturn(Optional.of(next));
            given(cartRepository.findByUserAndVariant(user, next)).willReturn(Optional.empty());

            cartService.updateCart(USERNAME, 10L,
                    CartDto.UpdateCartRequest.builder().variantId(2L).build());

            assertThat(item.getVariant().getVariantId()).isEqualTo(2L);
            assertThat(item.getQuantity()).isEqualTo(2);
            verify(cartRepository, never()).delete(any());
        }

        /** 같은 옵션이 두 줄로 남으면 목록에 중복이 생기고 이후 상한·재고 검사가 줄마다 따로 통과한다. */
        @Test
        @DisplayName("이미 담아 둔 옵션으로 바꾸면 기존 줄에 합치고 원래 줄을 지운다")
        void switchingToHeldVariantMergesRows() {
            Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
            Product product = product(market, ProductGroupBuyStatus.IN_PROGRESS, 10);
            ProductVariant current = variant(1L, product, 38000, 24900, 10);
            ProductVariant next = variant(2L, product, 38000, 24900, 10);
            Cart item = cart(10L, current, 2);
            Cart alreadyHeld = cart(11L, next, 3);

            givenUpdatable(item);
            given(productVariantRepository.findByVariantId(2L)).willReturn(Optional.of(next));
            given(cartRepository.findByUserAndVariant(user, next)).willReturn(Optional.of(alreadyHeld));

            CartDto.UpdateCartResponse response = cartService.updateCart(USERNAME, 10L,
                    CartDto.UpdateCartRequest.builder().variantId(2L).build());

            assertThat(alreadyHeld.getQuantity()).isEqualTo(5);
            verify(cartRepository).delete(item);
            assertThat(response.getCartId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("합쳐진 수량이 재고를 넘으면 병합도 거절한다")
        void mergeOverStockIsRejected() {
            Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
            Product product = product(market, ProductGroupBuyStatus.IN_PROGRESS, 10);
            ProductVariant current = variant(1L, product, 38000, 24900, 10);
            ProductVariant next = variant(2L, product, 38000, 24900, 4);
            Cart item = cart(10L, current, 2);

            givenUpdatable(item);
            given(productVariantRepository.findByVariantId(2L)).willReturn(Optional.of(next));
            given(cartRepository.findByUserAndVariant(user, next)).willReturn(Optional.of(cart(11L, next, 3)));

            assertThatThrownBy(() -> cartService.updateCart(USERNAME, 10L,
                    CartDto.UpdateCartRequest.builder().variantId(2L).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_STOCK);

            verify(cartRepository, never()).delete(any());
        }

        /** 조회를 (id, user)로 좁히므로 남의 항목은 애초에 잡히지 않는다. */
        @Test
        @DisplayName("남의 장바구니 항목은 수정할 수 없다")
        void othersItemIsNotUpdatable() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(cartRepository.findByIdAndUser(10L, user)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.updateCart(
                    USERNAME, 10L, CartDto.UpdateCartRequest.builder().quantity(2).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteFromCart {

        @Test
        @DisplayName("선택 삭제는 지정한 항목만 지운다")
        void selectedItemsAreDeleted() {
            ProductVariant target = purchasableVariant(1L, 10);
            Cart first = cart(10L, target, 1);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(cartRepository.findByIdInAndUser(List.of(10L), user)).willReturn(List.of(first));
            given(cartRepository.findAllByUser(user)).willReturn(List.of());

            CartDto.DeleteCartResponse response = cartService.deleteCart(USERNAME, List.of(10L));

            assertThat(response.getDeletedCount()).isEqualTo(1);
            assertThat(response.getDeletedCartItemIds()).containsExactly(10L);
            verify(cartRepository).deleteAllByIdInBatch(List.of(10L));
        }

        /**
         * 내 것과 남의 것이 섞여 오면 <b>전부 거절</b>한다 — 내 것만 골라 지우면 남의 항목이 조용히
         * 무시되고, 클라이언트는 요청한 만큼 지워졌다고 믿는다.
         */
        @Test
        @DisplayName("남의 항목 ID가 섞이면 하나도 지우지 않고 거절한다")
        void mixedOwnershipDeletesNothing() {
            ProductVariant target = purchasableVariant(1L, 10);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(cartRepository.findByIdInAndUser(List.of(10L, 999L), user))
                    .willReturn(List.of(cart(10L, target, 1)));

            assertThatThrownBy(() -> cartService.deleteCart(USERNAME, List.of(10L, 999L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            verify(cartRepository, never()).deleteAllByIdInBatch(any());
        }

        @Test
        @DisplayName("ID를 주지 않으면 전체 삭제다")
        void nullIdsClearsEverything() {
            ProductVariant target = purchasableVariant(1L, 10);
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(cartRepository.countByUser(user)).willReturn(2L);
            given(cartRepository.findAllByUser(user))
                    .willReturn(List.of(cart(10L, target, 1), cart(11L, target, 1)), List.of());

            CartDto.DeleteCartResponse response = cartService.deleteCart(USERNAME, null);

            assertThat(response.getDeletedCount()).isEqualTo(2);
            verify(cartRepository).deleteByUser(user);
        }

        /** 이미 빈 장바구니에 전체 삭제를 부르는 것은 오류가 아니다 — 결과가 같기 때문이다. */
        @Test
        @DisplayName("이미 비어 있으면 지우지 않고 비어 있다고 알려준다")
        void clearingEmptyCartIsNoOp() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
            given(cartRepository.countByUser(user)).willReturn(0L);

            CartDto.DeleteCartResponse response = cartService.deleteCart(USERNAME, null);

            assertThat(response.getDeletedCount()).isZero();
            assertThat(response.getSummary().getExpectedTotalPrice()).isZero();
            verify(cartRepository, never()).deleteByUser(any());
        }
    }

    @Nested
    @DisplayName("표시 값 계산")
    class Presentation {

        /** 방금 담은 상품을 찾으러 스크롤하지 않게 한다. */
        @Test
        @DisplayName("최근에 담은 항목이 위로 온다")
        void recentlyAddedComesFirst() {
            ProductVariant target = purchasableVariant(1L, 10);
            givenCart(List.of(cart(10L, target, 1), cart(12L, target, 1), cart(11L, target, 1)));

            CartDto.CartListResponse response = cartService.getCart(USERNAME, null);

            assertThat(response.getGroups().get(0).getItems())
                    .extracting(CartDto.CartItem::getCartId)
                    .containsExactly(12L, 11L, 10L);
        }

        @Test
        @DisplayName("할인율은 정가와 판매가로 계산해 내려준다")
        void discountRateIsCalculated() {
            Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
            // 정가 40,000 → 판매가 30,000 = 25% 할인
            givenCart(List.of(cart(10L,
                    variant(1L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 40000, 30000, 10), 1)));

            CartDto.CartItem item = itemOf(cartService.getCart(USERNAME, null), 10L);

            assertThat(item.getPrice().getRegularPrice()).isEqualTo(40000);
            assertThat(item.getPrice().getSalePrice()).isEqualTo(30000);
            assertThat(item.getPrice().getDiscountRate()).isEqualTo(25);
        }

        @Test
        @DisplayName("정가와 판매가가 같으면 할인율은 0이다 — 0% 배지를 붙이지 않기 위해서다")
        void noDiscountYieldsZeroRate() {
            Market market = market(5L, "제니의 뷰티룸", 3000, 30000);
            givenCart(List.of(cart(10L,
                    variant(1L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 30000, 30000, 10), 1)));

            assertThat(itemOf(cartService.getCart(USERNAME, null), 10L).getPrice().getDiscountRate()).isZero();
        }

        @Test
        @DisplayName("수량이 곱해진 금액으로 합계가 계산된다")
        void totalsMultiplyByQuantity() {
            Market market = market(5L, "제니의 뷰티룸", 3000, 100000);
            givenCart(List.of(cart(10L,
                    variant(1L, product(market, ProductGroupBuyStatus.IN_PROGRESS, 10), 40000, 30000, 10), 3)));

            CartDto.CartSummary summary = cartService.getCart(USERNAME, null).getSummary();

            assertThat(summary.getRegularTotal()).isEqualTo(120_000L);
            assertThat(summary.getSaleTotal()).isEqualTo(90_000L);
            assertThat(summary.getDiscountTotal()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("장바구니가 비어 있으면 그룹도 합계도 비어 있다")
        void emptyCartYieldsEmptyResponse() {
            givenCart(List.of());

            CartDto.CartListResponse response = cartService.getCart(USERNAME, null);

            assertThat(response.getGroups()).isEmpty();
            assertThat(response.getSummary().getTotalCount()).isZero();
            assertThat(response.getSummary().getFinalTotal()).isZero();
        }

        @Test
        @DisplayName("없는 회원이면 404를 낸다")
        void unknownUserIsRejected() {
            given(userRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.getCart(USERNAME, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private ProductVariant purchasableVariant(Long variantId, int stock) {
        return variant(variantId, product(market(5L, "제니의 뷰티룸", 3000, 30000),
                ProductGroupBuyStatus.IN_PROGRESS, stock), 38000, 24900, stock);
    }

    private void givenAddable(ProductVariant target) {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(productVariantRepository.findByVariantId(target.getVariantId())).willReturn(Optional.of(target));
        // 저장은 통과시킨다 — 담기 테스트의 관심사는 저장 전 검사와 합산 수량이다.
        given(cartRepository.save(any(Cart.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenUpdatable(Cart item) {
        given(userRepository.findByUsername(USERNAME)).willReturn(Optional.of(user));
        given(cartRepository.findByIdAndUser(item.getId(), user)).willReturn(Optional.of(item));
        given(cartRepository.save(org.mockito.ArgumentMatchers.any(Cart.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(cartRepository.findAllByUser(user)).willReturn(List.of(item));
    }

    private CartDto.AddCartRequest addRequest(Long variantId, int quantity) {
        return CartDto.AddCartRequest.builder().productId(1024L).variantId(variantId).quantity(quantity).build();
    }

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
