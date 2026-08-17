package showroomz.domain.product.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.type.ProductDisplayStatus;
import showroomz.domain.product.type.ProductGender;
import showroomz.domain.product.type.ProductGroupBuyStatus;
import showroomz.domain.product.type.ProductListSortType;

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

    /**
     * 상품 상세의 "함께 판매 중" 목록 (C7).
     * 진열중이면서 공구에 연결된 상품만 담는다 — 공구 없는 상품은 상세로 들어갈 수 없으므로
     * 목록에 두면 누를 수 없는 카드가 된다.
     */
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

    /**
     * 셀러 백스테이지 공구 상태별 상품 건수
     * - keyword, displayStatus 반영 / groupBuyStatus 필터 미반영
     * @return [ProductGroupBuyStatus, count]
     */
    List<Object[]> countSellerProductsByGroupBuyStatus(
            Long marketId,
            ProductDisplayStatus displayStatus,
            String keyword
    );

    /**
     * 관리자 상품 목록 (전체 마켓, 필터 + 정렬)
     * - keyword: 상품명 / 상품번호 / 브랜드(마켓)명
     */
    Page<Product> searchAdminProducts(
            ProductDisplayStatus displayStatus,
            ProductGroupBuyStatus groupBuyStatus,
            String keyword,
            ProductListSortType sortType,
            Pageable pageable
    );

    /**
     * 관리자 진열 상태별 상품 건수
     * - keyword, groupBuyStatus 반영 / displayStatus 필터 미반영
     * @return [ProductDisplayStatus, count]
     */
    List<Object[]> countAdminProductsByDisplayStatus(
            ProductGroupBuyStatus groupBuyStatus,
            String keyword
    );

    /**
     * 관리자 공구 상태별 상품 건수
     * - keyword, displayStatus 반영 / groupBuyStatus 필터 미반영
     * @return [ProductGroupBuyStatus, count]
     */
    List<Object[]> countAdminProductsByGroupBuyStatus(
            ProductDisplayStatus displayStatus,
            String keyword
    );
}
