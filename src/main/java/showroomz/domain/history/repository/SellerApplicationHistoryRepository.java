package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.history.entity.SellerApplicationHistory;

import java.util.List;

public interface SellerApplicationHistoryRepository extends JpaRepository<SellerApplicationHistory, Long> {

    List<SellerApplicationHistory> findByApplication_IdOrderByCreatedAtAsc(Long applicationId);

    @Query("SELECT h FROM SellerApplicationHistory h " +
           "JOIN h.application a " +
           "WHERE a.seller.id = :sellerId " +
           "ORDER BY h.createdAt ASC")
    List<SellerApplicationHistory> findBySellerIdOrderByCreatedAtAsc(@Param("sellerId") Long sellerId);
}
