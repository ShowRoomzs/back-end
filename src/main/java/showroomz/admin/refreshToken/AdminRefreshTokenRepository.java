package showroomz.admin.refreshToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshToken, Long> {
    AdminRefreshToken findByAdminEmail(String adminEmail);
    AdminRefreshToken findByAdminEmailAndRefreshToken(String adminEmail, String refreshToken);
}

