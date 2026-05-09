package showroomz.domain.coupon.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.coupon.entity.Coupon;
import showroomz.domain.coupon.type.CouponStatus;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long>, CouponRepositoryCustom {

    @Query("SELECT c FROM Coupon c WHERE c.couponIssueNumber = :code")
    Optional<Coupon> findByCode(@Param("code") String code);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Coupon c WHERE c.couponIssueNumber = :code")
    boolean existsByCode(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.couponIssueNumber = :code")
    Optional<Coupon> findByCodeForUpdate(@Param("code") String code);

    long countByIdIn(Collection<Long> ids);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.status = :status WHERE c.id IN :couponIds")
    int bulkUpdateStatus(@Param("couponIds") Collection<Long> couponIds, @Param("status") CouponStatus status);
}
