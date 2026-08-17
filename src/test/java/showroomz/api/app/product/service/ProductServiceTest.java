package showroomz.api.app.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.domain.category.service.CategoryHierarchyService;
import showroomz.domain.filter.repository.FilterRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.entity.ProductVariant;
import showroomz.domain.product.repository.ProductOptionGroupRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.domain.product.repository.ProductVariantRepository;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGroupBuyStatus;
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
    private WishlistRepository wishlistRepository;
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
    }

    @Test
    @DisplayName("공구가 아직 안 열린 준비중 상품도 상세는 열린다 — 연결 없음과 다르다")
    void detailOfPreparingGroupBuyIsServed() {
        given(productRepository.findDetailByProductId(PRODUCT_ID))
                .willReturn(Optional.of(product(ProductGroupBuyStatus.PREPARING)));
        given(productOptionGroupRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
        given(productVariantRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());

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

    /**
     * 상세 응답은 <b>C7 화면이 그리는 값만</b> 담는다.
     *
     * <p>브랜드 줄·배송 블록·판매자 정보 탭은 상품이 아니라 <b>마켓과 셀러</b>에서 온다. 셀러 정보가
     * 아직 채워지지 않은 마켓도 있어, 없을 때 응답이 통째로 깨지지 않는지도 함께 본다.
     */
    @Nested
    @DisplayName("상세 응답 구성")
    class DetailPayload {

        private Product givenDetailReady(Product target) {
            given(productRepository.findDetailByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            given(productOptionGroupRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
            given(productVariantRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
            return target;
        }

        @Test
        @DisplayName("브랜드 줄은 마켓명과 브랜드 사이트 링크를 함께 내려준다")
        void brandLineCarriesMarketAndSiteUrl() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setMarket(market());
            givenDetailReady(target);

            ProductDto.ProductDetailResponse response = productService.getProductDetail(PRODUCT_ID);

            assertThat(response.getMarketName()).isEqualTo("라보에이치");
            assertThat(response.getBrandSiteUrl()).isEqualTo("https://labo-h.example.com");
        }

        /** 할인율을 클라이언트가 다시 계산하면 화면마다 34%와 35%로 갈린다. */
        @Test
        @DisplayName("상세는 할인율까지 계산해 내려준다")
        void detailCarriesDiscountRate() {
            givenDetailReady(product(ProductGroupBuyStatus.IN_PROGRESS));

            // 38,000 → 24,900 = 34.47% → 34%
            assertThat(productService.getProductDetail(PRODUCT_ID).getDiscountRate()).isEqualTo(34);
        }

        @Test
        @DisplayName("배송 블록은 마켓의 배송·교환·반품 설정을 그대로 싣는다")
        void deliveryBlockComesFromMarketSettings() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setMarket(market());
            givenDetailReady(target);

            ProductDto.DeliveryInfo delivery = productService.getProductDetail(PRODUCT_ID).getDelivery();

            assertThat(delivery.getShippingLeadDays()).isEqualTo(2);
            assertThat(delivery.getDeliveryFee()).isEqualTo(3000);
            assertThat(delivery.getFreeShippingThreshold()).isEqualTo(30000);
            assertThat(delivery.getRemoteAreaSurcharge()).isEqualTo(5000);
            assertThat(delivery.getReturnFee()).isEqualTo(3000);
            assertThat(delivery.getExchangeFee()).isEqualTo(6000);
        }

        @Test
        @DisplayName("판매자 정보는 셀러의 사업자 정보와 마켓의 고객센터 번호를 합쳐 만든다")
        void sellerInfoMergesSellerAndMarket() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setMarket(market());
            givenDetailReady(target);

            ProductDto.SellerInfo sellerInfo = productService.getProductDetail(PRODUCT_ID).getSellerInfo();

            assertThat(sellerInfo.getCompanyName()).isEqualTo("주식회사 라보에이치");
            assertThat(sellerInfo.getRepresentativeName()).isEqualTo("홍길동");
            assertThat(sellerInfo.getBusinessRegistrationNumber()).isEqualTo("000-00-00000");
            assertThat(sellerInfo.getMailOrderRegNumber()).isEqualTo("제0000-서울강남-00000호");
            assertThat(sellerInfo.getCsNumber()).isEqualTo("000-0000-0000");
            assertThat(sellerInfo.getEmail()).isEqualTo("brand@example.com");
        }

        /** 사업장 소재지는 기본 주소와 상세 주소가 나뉘어 저장되지만 화면에는 한 줄로 나간다. */
        @Test
        @DisplayName("사업장 소재지는 기본 주소와 상세 주소를 한 줄로 합친다")
        void businessAddressIsJoinedIntoOneLine() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setMarket(market());
            givenDetailReady(target);

            assertThat(productService.getProductDetail(PRODUCT_ID).getSellerInfo().getBusinessAddress())
                    .isEqualTo("서울특별시 강남구 ○○로 00 4층");
        }

        @Test
        @DisplayName("상세 주소가 비어 있으면 기본 주소만 내려간다")
        void blankDetailAddressIsDropped() {
            Market market = market();
            market.getSeller().setDetailAddress("   ");
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setMarket(market);
            givenDetailReady(target);

            assertThat(productService.getProductDetail(PRODUCT_ID).getSellerInfo().getBusinessAddress())
                    .isEqualTo("서울특별시 강남구 ○○로 00");
        }

        /** 승인 직후라 사업자 정보가 아직 없는 마켓에서도 상세가 500으로 죽으면 안 된다. */
        @Test
        @DisplayName("셀러 정보가 없어도 고객센터 번호는 내려주고 나머지는 비운다")
        void missingSellerLeavesOnlyCsNumber() {
            Market market = market();
            market.setSeller(null);
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setMarket(market);
            givenDetailReady(target);

            ProductDto.SellerInfo sellerInfo = productService.getProductDetail(PRODUCT_ID).getSellerInfo();

            assertThat(sellerInfo.getCsNumber()).isEqualTo("000-0000-0000");
            assertThat(sellerInfo.getCompanyName()).isNull();
        }

        /** 품절 옵션도 목록에서 지우지 않는다 — 없어지면 원래 없던 옵션인지 팔린 것인지 알 수 없다. */
        @Test
        @DisplayName("옵션 목록은 품절 여부를 함께 실어 보낸다")
        void variantsCarrySoldOutFlag() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            given(productRepository.findDetailByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            given(productOptionGroupRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
            given(productVariantRepository.findByProductIdWithOptions(PRODUCT_ID))
                    .willReturn(List.of(
                            variant(target, 1L, 38000, 24900, 3),
                            variant(target, 2L, 38000, 24900, 0)));

            List<ProductDto.VariantInfo> variants = productService.getProductDetail(PRODUCT_ID).getVariants();

            assertThat(variants).hasSize(2);
            assertThat(variants.get(0).getIsOutOfStock()).isFalse();
            assertThat(variants.get(1).getIsOutOfStock()).isTrue();
        }

        @Test
        @DisplayName("강제 품절이면 재고가 남아 있어도 모든 옵션이 품절로 나간다")
        void forcedOutOfStockMarksEveryVariant() {
            Product target = product(ProductGroupBuyStatus.IN_PROGRESS);
            target.setIsOutOfStockForced(true);
            given(productRepository.findDetailByProductId(PRODUCT_ID)).willReturn(Optional.of(target));
            given(productOptionGroupRepository.findByProductIdWithOptions(PRODUCT_ID)).willReturn(List.of());
            given(productVariantRepository.findByProductIdWithOptions(PRODUCT_ID))
                    .willReturn(List.of(variant(target, 1L, 38000, 24900, 10)));

            assertThat(productService.getProductDetail(PRODUCT_ID).getVariants().get(0).getIsOutOfStock())
                    .isTrue();
        }
    }

    // ------------------------------------------------------------------ 픽스처

    private ProductVariant variant(Product target, Long variantId, Integer regularPrice,
                                   Integer salePrice, Integer stock) {
        ProductVariant variant = new ProductVariant(target, "기본", regularPrice, salePrice, stock, true);
        variant.setVariantId(variantId);
        return variant;
    }

    private Market market() {
        Seller seller = new Seller();
        seller.setCompanyName("주식회사 라보에이치");
        seller.setRepresentativeName("홍길동");
        seller.setBusinessRegistrationNumber("000-00-00000");
        seller.setMailOrderRegNumber("제0000-서울강남-00000호");
        seller.setBusinessAddress("서울특별시 강남구 ○○로 00");
        seller.setDetailAddress("4층");
        seller.setEmail("brand@example.com");

        Market market = new Market(seller, "라보에이치", "000-0000-0000");
        market.setId(5L);
        market.setBrandSiteUrl("https://labo-h.example.com");
        market.setShippingLeadDays(2);
        market.setDefaultDeliveryFee(3000);
        market.setFreeShippingThreshold(30000);
        market.setRemoteAreaSurcharge(5000);
        market.setReturnFee(3000);
        market.setExchangeFee(6000);
        return market;
    }
}
