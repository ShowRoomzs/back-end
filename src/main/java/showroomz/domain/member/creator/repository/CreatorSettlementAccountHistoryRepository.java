package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.CreatorSettlementAccountHistory;

import java.util.Optional;

public interface CreatorSettlementAccountHistoryRepository extends JpaRepository<CreatorSettlementAccountHistory, Long> {

    Optional<CreatorSettlementAccountHistory> findFirstByCreatorIdOrderByChangedAtDescIdDesc(Long creatorId);
}
