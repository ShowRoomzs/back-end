package showroomz.domain.member.seller.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.member.seller.entity.SellerApplication;

import java.util.List;
import java.util.Optional;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    boolean existsBySeller_IdAndStatus(Long sellerId, SellerStatus status);

    Optional<SellerApplication> findTopBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, SellerStatus status);

    Optional<SellerApplication> findTopBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    List<SellerApplication> findBySeller_IdOrderByCreatedAtAsc(Long sellerId);

    /**
     * 판매자 입점 신청서 목록 조회 (신청서 단위, 브랜드명 검색 + 상태 필터)
     */
    @Query(
            value = "SELECT sa FROM SellerApplication sa JOIN FETCH sa.seller s " +
                    "WHERE s.roleType = :roleType " +
                    "AND (:status IS NULL OR sa.status = :status) " +
                    "AND (:keyword IS NULL OR :keyword = '' OR sa.marketName LIKE CONCAT('%', :keyword, '%'))",
            countQuery = "SELECT COUNT(sa) FROM SellerApplication sa JOIN sa.seller s " +
                    "WHERE s.roleType = :roleType " +
                    "AND (:status IS NULL OR sa.status = :status) " +
                    "AND (:keyword IS NULL OR :keyword = '' OR sa.marketName LIKE CONCAT('%', :keyword, '%'))"
    )
    Page<SellerApplication> searchSellerApplications(@Param("roleType") RoleType roleType,
                                                     @Param("status") SellerStatus status,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);

    /**
     * 판매자 입점 신청서 상태별 건수 (브랜드명 검색 반영, 상태 필터 미반영)
     */
    @Query("SELECT sa.status, COUNT(sa) FROM SellerApplication sa JOIN sa.seller s " +
           "WHERE s.roleType = :roleType " +
           "AND (:keyword IS NULL OR :keyword = '' OR sa.marketName LIKE CONCAT('%', :keyword, '%')) " +
           "GROUP BY sa.status")
    List<Object[]> countByStatus(@Param("roleType") RoleType roleType,
                                 @Param("keyword") String keyword);
}
