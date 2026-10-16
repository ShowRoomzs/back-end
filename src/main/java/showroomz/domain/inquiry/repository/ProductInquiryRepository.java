package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.inquiry.entity.ProductInquiry;
import showroomz.domain.inquiry.type.InquiryExposureStatus;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.inquiry.type.ProductInquiryType;

import java.time.LocalDateTime;
import java.util.Collection;

public interface ProductInquiryRepository extends JpaRepository<ProductInquiry, Long> {

    /** 어드민 공구 B3 「요청 후 CS 문의」의 상품 문의 쪽 — 계약 상품 · 기준 시각 이후(32 설계 4-5). */
    @Query("SELECT COUNT(pi) FROM ProductInquiry pi "
            + "WHERE pi.product.productId IN :productIds AND pi.createdAt >= :since")
    long countByProductIdsSince(@Param("productIds") Collection<Long> productIds,
                                @Param("since") LocalDateTime since);

    /** 어드민 공구 B4 「요청 후 품절 문의」 — 재입고 유형만(32 설계 4-5). */
    @Query("SELECT COUNT(pi) FROM ProductInquiry pi "
            + "WHERE pi.product.productId IN :productIds AND pi.type = :type AND pi.createdAt >= :since")
    long countByProductIdsAndTypeSince(@Param("productIds") Collection<Long> productIds,
                                       @Param("type") ProductInquiryType type,
                                       @Param("since") LocalDateTime since);

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

    /**
     * 성과 「문의 응답 평균」(48 파트너 설계서 2-4 · 미정 ④ 잠정 = 상품 문의만) — 기간 안에 들어온 답변된 상품 문의의
     * [등록 시각, 답변 시각]. 평균은 호출자가 낸다(DB 함수 방언 차이를 피한다).
     */
    @Query("SELECT i.createdAt, i.answeredAt FROM ProductInquiry i "
            + "WHERE i.product.productId IN :productIds AND i.createdAt >= :from AND i.createdAt < :to "
            + "AND i.answeredAt IS NOT NULL")
    java.util.List<Object[]> findAnswerTimes(@Param("productIds") Collection<Long> productIds,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /** 브랜드의 미답변 상품 문의 — 어드민 브랜드 상세 「진행 중」 칩(52 설계서 2-5). 삭제된 문의는 세지 않는다. */
    @Query("SELECT COUNT(i) FROM ProductInquiry i WHERE i.product.market.id = :marketId "
            + "AND i.status = showroomz.domain.inquiry.type.InquiryStatus.WAITING "
            + "AND i.exposureStatus <> showroomz.domain.inquiry.type.InquiryExposureStatus.DELETED")
    long countUnansweredByMarket(@Param("marketId") Long marketId);
}
