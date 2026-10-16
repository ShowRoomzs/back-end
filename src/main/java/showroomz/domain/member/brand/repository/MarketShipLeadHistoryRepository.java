package showroomz.domain.member.brand.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.brand.entity.MarketShipLeadHistory;

import java.util.Optional;

public interface MarketShipLeadHistoryRepository extends JpaRepository<MarketShipLeadHistory, Long> {

    /** 보조줄은 최신 1행만 읽는다(「08.20 2→3 변경」). */
    Optional<MarketShipLeadHistory> findFirstByMarketIdOrderByChangedAtDescIdDesc(Long marketId);
}
