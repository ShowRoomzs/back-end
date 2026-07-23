package showroomz.domain.member.seller.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.member.seller.entity.SellerApplication;

import java.util.List;
import java.util.Optional;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    boolean existsBySeller_IdAndStatus(Long sellerId, SellerStatus status);

    Optional<SellerApplication> findTopBySeller_IdAndStatusOrderByCreatedAtDesc(Long sellerId, SellerStatus status);

    Optional<SellerApplication> findTopBySeller_IdOrderByCreatedAtDesc(Long sellerId);

    List<SellerApplication> findBySeller_IdOrderByCreatedAtAsc(Long sellerId);
}
