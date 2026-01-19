package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import showroomz.domain.history.entity.LoginHistory;

import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long>, LoginHistoryRepositoryCustom {

    // 국가와 도시를 함께 조회 (중복 제거)
    // 결과는 Object[] 형태: [0]=country, [1]=city
    @Query("SELECT DISTINCT h.country, h.city FROM LoginHistory h " +
           "WHERE h.country IS NOT NULL AND h.city IS NOT NULL " +
           "ORDER BY h.country, h.city")
    List<Object[]> findDistinctLocations();
}
