package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.inquiry.entity.ProductInquiry;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.user.id = :userId " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi WHERE pi.user.id = :userId")
    Page<ProductInquiry> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "WHERE p.market.id = :marketId " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi WHERE pi.product.market.id = :marketId")
    Page<ProductInquiry> findByMarketId(@Param("marketId") Long marketId, Pageable pageable);
}
