package showroomz.api.seller.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.member.seller.entity.Seller;

import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByEmail(String email);
    boolean existsByEmail(String email);
    
    /**
     * REJECTED 상태가 아닌 이메일 존재 여부 확인
     * 반려된 계정의 이메일은 재사용 가능하도록 제외
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Seller s " +
           "WHERE s.email = :email AND s.status != :rejectedStatus")
    boolean existsByEmailAndStatusNotRejected(@Param("email") String email, 
                                               @Param("rejectedStatus") SellerStatus rejectedStatus);
}

