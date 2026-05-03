package showroomz.domain.productannouncement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.productannouncement.entity.ProductAnnouncement;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAnnouncementRepository extends JpaRepository<ProductAnnouncement, Long>, ProductAnnouncementRepositoryCustom {

    @Query("""
            SELECT DISTINCT a FROM ProductAnnouncement a
            LEFT JOIN FETCH a.targets t
            LEFT JOIN FETCH t.product
            WHERE a.id = :id AND a.market.id = :marketId
            """)
    Optional<ProductAnnouncement> findByIdWithTargetsAndProductsAndMarketId(
            @Param("id") Long id,
            @Param("marketId") Long marketId
    );

    List<ProductAnnouncement> findAllByIdInAndMarket_Id(Collection<Long> ids, Long marketId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ProductAnnouncement p WHERE p.id IN :ids AND p.market.id = :marketId")
    int deleteByIdInAndMarketId(@Param("ids") Collection<Long> ids, @Param("marketId") Long marketId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductAnnouncement p SET p.displayStatus = :status WHERE p.id IN :ids AND p.market.id = :marketId")
    int updateDisplayStatusByIdInAndMarketId(
            @Param("ids") Collection<Long> ids,
            @Param("status") ProductAnnouncementDisplayStatus status,
            @Param("marketId") Long marketId
    );
}
