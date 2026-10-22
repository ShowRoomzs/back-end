package showroomz.domain.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.order.entity.RemoteAreaPostcode;

import java.util.List;

public interface RemoteAreaPostcodeRepository extends JpaRepository<RemoteAreaPostcode, Long> {

    @Query("SELECT p FROM RemoteAreaPostcode p WHERE p.postcodeFrom <= :postcode AND p.postcodeTo >= :postcode")
    List<RemoteAreaPostcode> findCovering(@Param("postcode") String postcode);
}
