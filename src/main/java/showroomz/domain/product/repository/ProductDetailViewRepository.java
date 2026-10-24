package showroomz.domain.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.product.entity.ProductDetailView;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductDetailViewRepository extends JpaRepository<ProductDetailView, Long> {

    /** 30분 세션 판정 — 직전 세션 안에 같은 사람이 같은 상품을 이미 봤으면 적재하지 않는다. */
    boolean existsByProductIdAndViewerKeyAndViewedAtAfter(Long productId, String viewerKey, LocalDateTime since);

    /** 성과 상품별 — [productId, 조회(회), 고유 조회자(명)]. 공구 기간 안 · 그 공구에 귀속된 조회만. */
    @Query("SELECT v.productId, COUNT(v), COUNT(DISTINCT v.viewerKey) FROM ProductDetailView v "
            + "WHERE v.groupBuyId = :groupBuyId AND v.viewedAt >= :from AND v.viewedAt < :to "
            + "GROUP BY v.productId")
    List<Object[]> countByProductForGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /** 공구 전체 고유 조회자(명) — 상품 간 중복 제거. 상품별 고유 조회자의 합보다 작다(48 파트너 설계서 「조회자 합계」). */
    @Query("SELECT COUNT(DISTINCT v.viewerKey) FROM ProductDetailView v "
            + "WHERE v.groupBuyId = :groupBuyId AND v.viewedAt >= :from AND v.viewedAt < :to")
    long countUniqueViewersForGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    /** 게시물 → 상품 상세 이동(회) — 라스트 터치가 그 게시물인 이 공구 상품 조회. */
    @Query("SELECT COUNT(v) FROM ProductDetailView v WHERE v.groupBuyId = :groupBuyId "
            + "AND v.attributedPostId = :postId AND v.viewedAt >= :from AND v.viewedAt < :to")
    long countAttributedForGroupBuy(@Param("groupBuyId") Long groupBuyId,
                                    @Param("postId") Long postId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
