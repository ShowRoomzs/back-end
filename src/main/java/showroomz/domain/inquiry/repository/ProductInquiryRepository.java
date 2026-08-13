package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    long countByUser_Id(Long userId);

    /** 문의 내역 탭 건수 — 상태별 (C12 [답변 대기만] 필터·탭 배지) */
    long countByUser_IdAndStatus(Long userId, InquiryStatus status);

    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.user.id = :userId " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi WHERE pi.user.id = :userId")
    Page<ProductInquiry> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 내 상품 문의 조회 — [답변 대기만] 필터 적용 (C12) */
    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.user.id = :userId AND pi.status = :status " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi WHERE pi.user.id = :userId AND pi.status = :status")
    Page<ProductInquiry> findByUserIdAndStatus(@Param("userId") Long userId,
                                               @Param("status") InquiryStatus status,
                                               Pageable pageable);

    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "WHERE p.market.id = :marketId " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi WHERE pi.product.market.id = :marketId")
    Page<ProductInquiry> findByMarketId(@Param("marketId") Long marketId, Pageable pageable);

    @Query("SELECT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.user u " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.id = :inquiryId")
    java.util.Optional<ProductInquiry> findByIdWithUserAndProduct(@Param("inquiryId") Long inquiryId);
}
