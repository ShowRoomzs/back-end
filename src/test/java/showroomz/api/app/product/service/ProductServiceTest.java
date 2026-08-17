package showroomz.api.app.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.app.product.DTO.ProductDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.api.app.wishlist.service.WishlistService;
import showroomz.domain.category.service.CategoryHierarchyService;
import showroomz.domain.filter.repository.FilterRepository;
import showroomz.domain.product.entity.Product;
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
}
