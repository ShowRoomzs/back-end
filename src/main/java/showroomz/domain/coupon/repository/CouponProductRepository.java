package showroomz.domain.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import showroomz.domain.coupon.entity.CouponProduct;

@Repository
public interface CouponProductRepository extends JpaRepository<CouponProduct, Long> {
}
