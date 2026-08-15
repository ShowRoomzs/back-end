package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;

import java.time.LocalDateTime;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    long countByUser_IdAndExposureStatusNot(Long userId, InquiryExposureStatus exposureStatus);

    /** 문의 내역 탭 건수 — 상태별 (C12 [답변 대기만] 필터·탭 배지) */
    long countByUser_IdAndStatusAndExposureStatusNot(Long userId, InquiryStatus status,
                                                     InquiryExposureStatus exposureStatus);

    /**
     * 내 상품 문의 조회 — 삭제 집행된 문의는 질문·답변이 함께 소비자 화면에서 내려간다 (§23-5).
     */
    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.user.id = :userId AND pi.exposureStatus <> 'DELETED' " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi " +
                        "WHERE pi.user.id = :userId AND pi.exposureStatus <> 'DELETED'")
    Page<ProductInquiry> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /** 내 상품 문의 조회 — [답변 대기만] 필터 적용 (C12) */
    @Query(value = "SELECT DISTINCT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.user.id = :userId AND pi.status = :status AND pi.exposureStatus <> 'DELETED' " +
           "ORDER BY pi.createdAt DESC",
           countQuery = "SELECT COUNT(pi) FROM ProductInquiry pi " +
                        "WHERE pi.user.id = :userId AND pi.status = :status AND pi.exposureStatus <> 'DELETED'")
    Page<ProductInquiry> findByUserIdAndStatus(@Param("userId") Long userId,
                                               @Param("status") InquiryStatus status,
                                               Pageable pageable);

    @Query("SELECT pi FROM ProductInquiry pi " +
           "JOIN FETCH pi.user u " +
           "JOIN FETCH pi.product p " +
           "JOIN FETCH p.market m " +
           "WHERE pi.id = :inquiryId")
    java.util.Optional<ProductInquiry> findByIdWithUserAndProduct(@Param("inquiryId") Long inquiryId);

    /** 문의번호(QNA-YYYYMMDD-NNN)의 일자 내 순번 (§23-3) */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIdLessThanEqual(
            LocalDateTime from, LocalDateTime to, Long inquiryId);
}
