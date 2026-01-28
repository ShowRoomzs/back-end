package showroomz.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.type.ProductGender;
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
            Long categoryId,
            ProductGender gender,
            Pageable pageable
    );

    Page<Product> findRecommendedProducts(
            Long categoryId,
            ProductGender userGender,
            Pageable pageable
    );
}
