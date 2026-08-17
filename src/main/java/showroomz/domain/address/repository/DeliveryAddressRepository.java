package showroomz.domain.address.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * §22-4 지역 분포 — 팔로워의 기본 배송지 주소 문자열(시·도만 뽑아 쓴다).
     *
     * <p>§22-5 수집 한계: 지역은 별도 수집 항목이 없어 배송지로만 추정한다. 배송지를 등록하지 않은
     * 팔로워는 집계에서 빠지므로 표본 편향이 있고, 별도 수집 도입 여부는 아직 미결이다.
     * 주소 원문은 개인 정보라 서비스 밖으로 나가지 않는다 — 시·도로 접은 비율만 응답에 담는다.
     */
    @Query("SELECT d.address FROM DeliveryAddress d " +
           "WHERE d.isDefault = true " +
           "AND d.user.id IN (SELECT cf.user.id FROM CreatorFollow cf WHERE cf.creator.id = :creatorId)")
    List<String> findDefaultAddressesOfFollowers(@Param("creatorId") Long creatorId);
}
