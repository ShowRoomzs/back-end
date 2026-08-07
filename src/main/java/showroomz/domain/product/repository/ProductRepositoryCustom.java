package showroomz.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductInspectionStatus;
import showroomz.domain.product.type.ProductListSortType;

import java.time.Instant;
import java.util.List;

public interface ProductRepositoryCustom {
    Page<Product> searchProductsForUser(
            String keyword,
            List<Long> categoryIds,
            Long marketId,
            List<ProductFilterCriteria> filters,
            String sortType,
            Pageable pageable
    );

    Page<Product> findRelatedProducts(
            Long productId,
            List<Long> categoryIds,
            ProductGender gender,
            Pageable pageable
    );

    Page<Product> findRecommendedProducts(
            List<Long> categoryIds,
            ProductGender userGender,
            Pageable pageable
    );

    /**
     * 특정 마켓의 인기 상품 상위 N개 조회
     * - wishCount(Wishlist 수) DESC, createdAt DESC
     * - displayStatus=DISPLAY인 상품만
     */
    List<Product> findPopularProductsByMarketId(Long marketId, int limit);

    /**
     * 관리자 상품 검수 목록 (전 마켓, 미승인 포함)
     */
    Page<Product> searchAdminInspection(
            ProductInspectionStatus inspectionStatus,
            Instant createdFrom,
            Instant createdTo,
            String keyword,
            Long marketId,
            Pageable pageable
    );

    /**
     * 셀러 백스테이지 상품 목록 (필터 + 정렬)
     */
    Page<Product> searchSellerProducts(
            Long marketId,
            ProductDisplayStatus displayStatus,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword,
            ProductListSortType sortType,
            Pageable pageable
    );

    /**
     * 셀러 백스테이지 진열 상태별 상품 건수
     * - keyword, groupBuyStatus 반영 / displayStatus 필터 미반영
     * @return [ProductDisplayStatus, count]
     */
    List<Object[]> countSellerProductsByDisplayStatus(
            Long marketId,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword
    );
}
