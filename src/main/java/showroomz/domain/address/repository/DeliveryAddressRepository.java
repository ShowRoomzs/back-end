package showroomz.domain.address.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.address.entity.DeliveryAddress;
import showroomz.domain.member.user.entity.Users;

import java.util.List;
import java.util.Optional;

public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, Long> {
    
    // 사용자의 모든 배송지 조회 (기본 배송지 우선 정렬 등은 서비스나 쿼리에서 처리)
    List<DeliveryAddress> findAllByUserOrderByIsDefaultDescModifiedAtDesc(Users user);

    // 사용자의 현재 기본 배송지 조회
    Optional<DeliveryAddress> findByUserAndIsDefaultTrue(Users user);

    // 배송지 개수 제한 확인용
    long countByUser(Users user);
}
