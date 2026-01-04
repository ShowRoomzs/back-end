package showroomz.market.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import showroomz.market.entity.Market;

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    boolean existsByMarketName(String marketName);
    java.util.Optional<Market> findByAdmin(showroomz.admin.entity.Admin admin);
}

