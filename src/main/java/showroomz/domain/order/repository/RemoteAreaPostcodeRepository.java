package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.RemoteAreaPostcode;

import java.util.List;

public interface RemoteAreaPostcodeRepository extends JpaRepository<RemoteAreaPostcode, Long> {

    @Query("SELECT p FROM RemoteAreaPostcode p WHERE p.postcodeFrom <= :postcode AND p.postcodeTo >= :postcode")
    List<RemoteAreaPostcode> findCovering(@Param("postcode") String postcode);

    /** 어드민 등록 — 우편번호 순(55 계획서 P4). */
    List<RemoteAreaPostcode> findAllByOrderByPostcodeFromAscIdAsc();

    /** [from, to]와 한 번호라도 겹치는 등록 범위 — 5자리 고정 문자열이라 사전 순 비교가 숫자 비교와 같다. */
    @Query("SELECT p FROM RemoteAreaPostcode p WHERE p.postcodeFrom <= :to AND p.postcodeTo >= :from "
            + "ORDER BY p.postcodeFrom ASC, p.id ASC")
    List<RemoteAreaPostcode> findOverlapping(@Param("from") String from, @Param("to") String to);
}
