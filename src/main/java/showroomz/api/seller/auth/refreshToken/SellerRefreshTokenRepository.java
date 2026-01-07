package showroomz.api.seller.auth.refreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerRefreshTokenRepository extends JpaRepository<SellerRefreshToken, Long> {
    SellerRefreshToken findByAdminEmail(String adminEmail);
    SellerRefreshToken findByAdminEmailAndRefreshToken(String adminEmail, String refreshToken);
}

