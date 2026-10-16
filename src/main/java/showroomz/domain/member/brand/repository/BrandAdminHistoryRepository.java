package showroomz.domain.member.brand.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.brand.entity.BrandAdminHistory;
import showroomz.domain.member.brand.type.BrandAdminEventType;

import java.util.List;

public interface BrandAdminHistoryRepository extends JpaRepository<BrandAdminHistory, Long> {

    List<BrandAdminHistory> findByMarketIdOrderByCreatedAtDescIdDesc(Long marketId);

    List<BrandAdminHistory> findByMarketIdAndEventTypeOrderByCreatedAtDesc(Long marketId, BrandAdminEventType eventType);
}
