package showroomz.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductInspectionStatus;

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
     * - isDisplay=true인 상품만
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
}
