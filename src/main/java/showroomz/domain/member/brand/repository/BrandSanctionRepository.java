package showroomz.domain.member.brand.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.brand.entity.BrandSanction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandSanctionRepository extends JpaRepository<BrandSanction, Long> {

    /** 열린 정지 — 마켓당 최대 1건(52 설계서 1-2). */
    Optional<BrandSanction> findByMarketIdAndReleasedAtIsNull(Long marketId);

    boolean existsByMarketIdAndReleasedAtIsNull(Long marketId);

    /** 지급 정지가 걸린 열린 정지가 있는가 — 정산 포트 {@code BrandPayoutHoldReader}. */
    @Query("SELECT COUNT(s) > 0 FROM BrandSanction s WHERE s.marketId = :marketId AND s.releasedAt IS NULL "
            + "AND s.payoutHeld = true")
    boolean existsOpenPayoutHold(@Param("marketId") Long marketId);

    @Query("SELECT s FROM BrandSanction s WHERE s.marketId IN :marketIds AND s.releasedAt IS NULL")
    List<BrandSanction> findOpenByMarketIds(@Param("marketIds") Collection<Long> marketIds);
}
