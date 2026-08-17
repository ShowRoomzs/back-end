package showroomz.api.app.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.app.wishlist.service.WishlistService;
import showroomz.domain.category.entity.Category;
import showroomz.domain.category.service.CategoryHierarchyService;
import showroomz.domain.filter.repository.FilterRepository;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductOptionGroupRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.review.repository.ReviewRepository;
import showroomz.domain.wishlist.repository.WishlistRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * C7 상품 상세 — 진열중이면서 공구에 연결된 상품만 게시된다.
 *
 * <p>진입 경로가 공구 게시물의 상품 카드뿐이라 화면상으로는 도달할 수 없지만, 상품 ID가
 * 순번이라 주소만 바꾸면 열리므로 서버가 두 조건을 모두 확인하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceTest {

    private static final long PRODUCT_ID = 1024L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryHierarchyService categoryHierarchyService;
    @Mock
    private FilterRepository filterRepository;
    @Mock
    private ProductOptionGroupRepository productOptionGroupRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WishlistRepository wishlistRepository;
    @Mock
    private WishlistService wishlistService;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductService productService;

    private Product product(ProductGroupBuyStatus groupBuyStatus) {
        return product(groupBuyStatus, ProductDisplayStatus.DISPLAY);
    }

    private Product product(ProductGroupBuyStatus groupBuyStatus, ProductDisplayStatus displayStatus) {
        Product product = new Product();
        product.setProductId(PRODUCT_ID);
        product.setName("시카 리페어 앰플 30ml 리필 2개 세트");
        product.setRegularPrice(38000);
        product.setSalePrice(24900);
        product.setGroupBuyStatus(groupBuyStatus);
        product.setDisplayStatus(displayStatus);
        return product;
    }

    @Test
    @DisplayName("공구에 연결되지 않은 상품의 상세는 404다 — 존재 자체를 드러내지 않는다")
    void detailOfProductWithoutGroupBuyIsNotFound() {
        given(productRepository.findDetailByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.NOT_CONNECTED)));

        assertThatThrownBy(() -> productService.getProductDetail(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productOptionGroupRepository, never()).findByProductIdWithOptions(anyLong());
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("공구가 아직 안 열린 준비중 상품도 상세는 열린다 — 연결 없음과 다르다")
    void detailOfPreparingGroupBuyIsServed() {
        given(productRepository.findDetailByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.PREPARING)));
        given(productOptionGroupRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
        given(productVariantRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
        given(reviewRepository.findTop3ByProductIdOrderByCreatedAtDesc(anyLong(), any())).willReturn(List.of());

        ProductDto.ProductDetailResponse response = productService.getProductDetail(PRODUCT_ID);

        assertThat(response.getId()).isEqualTo(PRODUCT_ID);
        assertThat(response.getGroupBuyStatus()).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("미진열 상품은 공구에 연결되어 있어도 상세가 404다")
    void detailOfHiddenProductIsNotFoundEvenIfGroupBuyConnected() {
        given(productRepository.findDetailByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.IN_PROGRESS, ProductDisplayStatus.HIDDEN)));

        assertThatThrownBy(() -> productService.getProductDetail(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productOptionGroupRepository, never()).findByProductIdWithOptions(anyLong());
        verifyNoInteractions(reviewRepository);
    }

    @Test
    @DisplayName("공구 연결이 끝나 NOT_CONNECTED로 돌아가면 진열중이어도 상세가 404다")
    void detailOfDisplayedProductIsNotFoundOnceGroupBuyEnds() {
        given(productRepository.findDetailByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.NOT_CONNECTED, ProductDisplayStatus.DISPLAY)));

        assertThatThrownBy(() -> productService.getProductDetail(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("옵션 시트의 재고 조회도 같은 규칙을 따른다 — 상세가 막히면 재고도 막힌다")
    void variantStocksFollowTheSameRule() {
        given(productRepository.findByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.NOT_CONNECTED)));

        assertThatThrownBy(() -> productService.getVariantStocks(PRODUCT_ID, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productVariantRepository, never()).findByProductIdAndVariantIdIn(anyLong(), any());
    }

    @Test
    @DisplayName("함께 판매 중 목록도 같은 규칙을 따른다")
    void relatedProductsFollowTheSameRule() {
        given(productRepository.findByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.NOT_CONNECTED)));

        assertThatThrownBy(() -> productService.getRelatedProducts(PRODUCT_ID, 1, 20, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);

        verify(productRepository, never()).findRelatedProducts(anyLong(), any(), any(), any());
    }

    /**
     * 가격 표시 — 할인율은 <b>서버가 계산해 내려준다</b>.
     *
     * <p>클라이언트가 정가·판매가로 직접 계산하면 반올림 방식이 화면마다 갈려 같은 상품에
     * 34%와 35%가 동시에 보인다. 그래서 계산을 한곳에 두고, 여기서 반올림과 경계를 고정한다.
     */
    @Nested
    @DisplayName("가격·할인율")
    class Pricing {

        private void givenDetailReady(Product target) {
            given(productRepository.findDetailByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            given(productOptionGroupRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
            given(productVariantRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
            given(reviewRepository.findTop3ByProductIdOrderByCreatedAtDesc(anyLong(), any())).willReturn(List.of());
        }

        @Test
        @DisplayName("상세는 정가와 판매가를 그대로 내려준다")
        void detailCarriesBothPrices() {
            givenDetailReady(product(ProductGroupBuyStatus.IN_PROGRESS));

            ProductDto.ProductDetailResponse response = productService.getProductDetail(PRODUCT_ID);

            assertThat(response.getRegularPrice()).isEqualTo(38000);
            assertThat(response.getSalePrice()).isEqualTo(24900);
        }

        @Test
        @DisplayName("옵션 재고 조회의 할인율은 옵션 가격으로 계산된다 — 상품 대표가가 아니다")
        void variantDiscountUsesVariantPrice() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            given(productRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            // 옵션 정가 40,000 → 판매가 30,000 = 25%. 상품 대표가(38,000/24,900)와 다른 값이다.
            given(productVariantRepository.findByProductIdAndVariantIdIn(PRODUCT_ID, List.of(1L)))
                    .willReturn(List.of(variant(target, 1L, 40000, 30000, 5)));

            ProductDto.ProductVariantStockResponse stock =
                    productService.getVariantStocks(PRODUCT_ID, List.of(1L)).getVariants().get(0);

            assertThat(stock.getPrice().getRegularPrice()).isEqualTo(40000);
            assertThat(stock.getPrice().getSalePrice()).isEqualTo(30000);
            assertThat(stock.getPrice().getDiscountRate()).isEqualTo(25);
        }

        @Test
        @DisplayName("할인이 없으면 할인율은 0이다 — 0% 배지를 붙이지 않기 위해서다")
        void noDiscountYieldsZero() {
            assertThat(discountRateOf(30000, 30000)).isZero();
        }

        /** 판매가가 정가보다 높은 데이터가 들어와도 음수 할인율이 화면에 나가면 안 된다. */
        @Test
        @DisplayName("판매가가 정가보다 높아도 할인율은 음수가 되지 않는다")
        void inflatedSalePriceYieldsZero() {
            assertThat(discountRateOf(20000, 30000)).isZero();
        }

        @Test
        @DisplayName("할인율은 소수점을 반올림한다")
        void discountRateIsRounded() {
            // 38,000 → 24,900 = 34.47% → 34%
            assertThat(discountRateOf(38000, 24900)).isEqualTo(34);
            // 30,000 → 19,600 = 34.67% → 35%
            assertThat(discountRateOf(30000, 19600)).isEqualTo(35);
        }

        @Test
        @DisplayName("정가가 0이거나 비어 있으면 할인율은 0이다 — 0으로 나누지 않는다")
        void zeroOrMissingRegularPriceYieldsZero() {
            assertThat(discountRateOf(0, 10000)).isZero();
            assertThat(discountRateOf(null, 10000)).isZero();
            assertThat(discountRateOf(30000, null)).isZero();
        }

        @Test
        @DisplayName("무료로 주는 상품의 할인율은 100%다")
        void freeProductIsFullyDiscounted() {
            assertThat(discountRateOf(30000, 0)).isEqualTo(100);
        }

        private Integer discountRateOf(Integer regularPrice, Integer salePrice) {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            given(productRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            given(productVariantRepository.findByProductIdAndVariantIdIn(PRODUCT_ID, List.of(1L)))
                    .willReturn(List.of(variant(target, 1L, regularPrice, salePrice, 5)));

            return productService.getVariantStocks(PRODUCT_ID, List.of(1L))
                    .getVariants().get(0).getPrice().getDiscountRate();
        }
    }

    @Nested
    @DisplayName("옵션 재고")
    class VariantStock {

        private Product givenVisibleProduct() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            given(productRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            return target;
        }

        @Test
        @DisplayName("재고가 남아 있으면 품절이 아니다")
        void stockedVariantIsAvailable() {
            Product target = givenVisibleProduct();
            given(productVariantRepository.findByProductIdAndVariantIdIn(PRODUCT_ID, List.of(1L)))
                    .willReturn(List.of(variant(target, 1L, 38000, 24900, 3)));

            ProductDto.ProductVariantStockResponse stock =
                    productService.getVariantStocks(PRODUCT_ID, List.of(1L)).getVariants().get(0);

            assertThat(stock.getStock()).isEqualTo(3);
            assertThat(stock.getIsOutOfStock()).isFalse();
        }

        @Test
        @DisplayName("재고가 0이면 품절이다")
        void zeroStockIsSoldOut() {
            Product target = givenVisibleProduct();
            given(productVariantRepository.findByProductIdAndVariantIdIn(PRODUCT_ID, List.of(1L)))
                    .willReturn(List.of(variant(target, 1L, 38000, 24900, 0)));

            assertThat(productService.getVariantStocks(PRODUCT_ID, List.of(1L))
                    .getVariants().get(0).getIsOutOfStock()).isTrue();
        }

        /** 재고가 남아 있어도 브랜드가 강제 품절로 내려둘 수 있다 — 그 의사가 화면에 그대로 반영돼야 한다. */
        @Test
        @DisplayName("강제 품절이면 재고가 남아 있어도 품절로 내려간다")
        void forcedOutOfStockOverridesStock() {
            Product target = givenVisibleProduct();
            target.setIsOutOfStockForced(true);
            given(productVariantRepository.findByProductIdAndVariantIdIn(PRODUCT_ID, List.of(1L)))
                    .willReturn(List.of(variant(target, 1L, 38000, 24900, 10)));

            ProductDto.ProductVariantStockResponse stock =
                    productService.getVariantStocks(PRODUCT_ID, List.of(1L)).getVariants().get(0);

            assertThat(stock.getStock()).isEqualTo(10);
            assertThat(stock.getIsOutOfStock()).isTrue();
            assertThat(stock.getIsOutOfStockForced()).isTrue();
        }

        @Test
        @DisplayName("여러 옵션을 한 번에 조회한다 — 옵션 시트가 요청을 나누지 않게 한다")
        void multipleVariantsAreReturnedTogether() {
            Product target = givenVisibleProduct();
            given(productVariantRepository.findByProductIdAndVariantIdIn(PRODUCT_ID, List.of(1L, 2L)))
                    .willReturn(List.of(
                            variant(target, 1L, 38000, 24900, 3),
                            variant(target, 2L, 38000, 24900, 0)));

            assertThat(productService.getVariantStocks(PRODUCT_ID, List.of(1L, 2L)).getVariants())
                    .hasSize(2)
                    .extracting(ProductDto.ProductVariantStockResponse::getVariantId)
                    .containsExactly(1L, 2L);
        }

        /** 빈 요청을 통과시키면 전체 옵션을 긁는 쿼리가 되거나 빈 응답으로 화면이 조용히 비어 버린다. */
        @Test
        @DisplayName("옵션 ID를 주지 않으면 거절한다")
        void emptyVariantIdsIsRejected() {
            assertThatThrownBy(() -> productService.getVariantStocks(PRODUCT_ID, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            assertThatThrownBy(() -> productService.getVariantStocks(PRODUCT_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("없는 상품의 재고는 조회할 수 없다")
        void unknownProductIsRejected() {
            given(productRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getVariantStocks(PRODUCT_ID, List.of(1L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("함께 판매 중 (연관 상품)")
    class RelatedProducts {

        private Product givenVisibleProduct() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            Category category = new Category();
            category.setCategoryId(7L);
            category.setName("스킨케어");
            target.setCategory(category);
            given(productRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            return target;
        }

        private void givenRelated(Product... products) {
            given(productRepository.findRelatedProducts(anyLong(), any(), any(), any()))
                    .willAnswer(invocation -> new PageImpl<>(
                            List.of(products), invocation.getArgument(3), products.length));
        }

        /** 자기 자신이 "함께 판매 중"에 섞이면 같은 상품으로 되돌아가는 링크가 된다. */
        @Test
        @DisplayName("조회 대상 상품 자신을 제외하도록 ID를 넘긴다")
        void excludesItselfByPassingOwnId() {
            givenVisibleProduct();
            givenRelated();

            productService.getRelatedProducts(PRODUCT_ID, 1, 20, null);

            verify(productRepository).findRelatedProducts(
                    org.mockito.ArgumentMatchers.eq(PRODUCT_ID), any(), any(), any());
        }

        /** 하위 카테고리까지 포함해야 "스킨케어"에서 토너·앰플이 함께 나온다. */
        @Test
        @DisplayName("카테고리는 하위까지 펼쳐 조회한다")
        void expandsCategoryHierarchy() {
            givenVisibleProduct();
            given(categoryHierarchyService.getAllSubCategoryIds(7L)).willReturn(List.of(7L, 8L, 9L));
            givenRelated();

            productService.getRelatedProducts(PRODUCT_ID, 1, 20, null);

            verify(productRepository).findRelatedProducts(
                    anyLong(), org.mockito.ArgumentMatchers.eq(List.of(7L, 8L, 9L)), any(), any());
        }

        /** 카테고리 조회가 실패해도 "함께 판매 중"이 통째로 비면 상세 화면에 빈 칸이 남는다. */
        @Test
        @DisplayName("카테고리 조회가 실패해도 자기 카테고리로 좁혀 계속 조회한다")
        void fallsBackToOwnCategoryOnFailure() {
            givenVisibleProduct();
            given(categoryHierarchyService.getAllSubCategoryIds(7L))
                    .willThrow(new RuntimeException("카테고리 트리 조회 실패"));
            givenRelated();

            productService.getRelatedProducts(PRODUCT_ID, 1, 20, null);

            verify(productRepository).findRelatedProducts(
                    anyLong(), org.mockito.ArgumentMatchers.eq(List.of(7L)), any(), any());
        }

        @Test
        @DisplayName("페이지 번호는 1부터 받아 0부터 세는 쿼리로 바꿔 넘긴다")
        void pageNumberIsConvertedToZeroBased() {
            givenVisibleProduct();
            givenRelated();

            productService.getRelatedProducts(PRODUCT_ID, 2, 10, null);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findRelatedProducts(anyLong(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("페이지·개수를 주지 않으면 첫 페이지 20개로 조회한다")
        void defaultsToFirstPageOfTwenty() {
            givenVisibleProduct();
            givenRelated();

            productService.getRelatedProducts(PRODUCT_ID, null, null, null);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findRelatedProducts(anyLong(), any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("연관 상품 카드에도 할인율이 계산돼 실린다")
        void relatedItemsCarryDiscountRate() {
            givenVisibleProduct();
            Product related = new Product();
            related.setProductId(2048L);
            related.setName("수분 진정 토너");
            related.setRegularPrice(40000);
            related.setSalePrice(30000);
            related.setGroupBuyStatus(ProductGroupBuyStatus.IN_PROGRESS);
            related.setDisplayStatus(ProductDisplayStatus.DISPLAY);
            givenRelated(related);

            ProductDto.ProductItem item =
                    productService.getRelatedProducts(PRODUCT_ID, 1, 20, null).getContent().get(0);

            assertThat(item.getPrice().getDiscountRate()).isEqualTo(25);
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private ProductVariant variant(Product target, Long variantId, Integer regularPrice,
                                   Integer salePrice, Integer stock) {
        ProductVariant variant = new ProductVariant(target, "기본", regularPrice, salePrice, stock, true);
        variant.setVariantId(variantId);
        return variant;
    }
}
