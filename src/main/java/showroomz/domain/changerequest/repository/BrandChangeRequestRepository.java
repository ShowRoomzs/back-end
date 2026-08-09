package showroomz.domain.changerequest.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BrandChangeRequestRepository extends JpaRepository<BrandChangeRequest, Long> {

    Optional<BrandChangeRequest> findByMarket_IdAndTypeAndStatus(Long marketId, ChangeRequestType type, ChangeRequestStatus status);

    long countByRequestCodeStartingWith(String prefix);

    /** §3-2 배너 후보 — PENDING이거나, 처리완료인데 아직 확인(acknowledge)하지 않은 최신 1건. */
    @Query("SELECT r FROM BrandChangeRequest r WHERE r.market.id = :marketId AND r.type = :type " +
            "AND (r.status = :pending OR (r.status IN :terminalStatuses AND r.resultAcknowledgedAt IS NULL)) " +
            "ORDER BY r.requestedAt DESC")
    List<BrandChangeRequest> findBannerCandidates(@Param("marketId") Long marketId,
                                                   @Param("type") ChangeRequestType type,
                                                   @Param("pending") ChangeRequestStatus pending,
                                                   @Param("terminalStatuses") Collection<ChangeRequestStatus> terminalStatuses,
                                                   Pageable pageable);

    /**
     * 어드민 목록(§16-1) — 검토 대기가 항상 위, 그 안에서 경과 내림차순(=요청일시 오름차순),
     * 처리 완료 건은 요청일시 최신순으로 내려간다.
     */
    @Query(value =
            "SELECT * FROM brand_change_request r " +
            "WHERE r.status IN (:statuses) " +
            "AND (:keyword IS NULL OR :keyword = '' OR EXISTS (" +
            "    SELECT 1 FROM market m WHERE m.market_id = r.market_id AND m.market_name LIKE CONCAT('%', :keyword, '%')" +
            ")) " +
            "ORDER BY (r.status = 'PENDING') DESC, CASE WHEN r.status = 'PENDING' THEN r.requested_at END ASC, r.requested_at DESC",
            countQuery =
            "SELECT COUNT(*) FROM brand_change_request r " +
            "WHERE r.status IN (:statuses) " +
            "AND (:keyword IS NULL OR :keyword = '' OR EXISTS (" +
            "    SELECT 1 FROM market m WHERE m.market_id = r.market_id AND m.market_name LIKE CONCAT('%', :keyword, '%')" +
            "))",
            nativeQuery = true)
    Page<BrandChangeRequest> search(@Param("statuses") Collection<String> statuses,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

    /** 상세의 이전/다음(§16-2) — 현재 탭과 동일한 정렬의 전체 id 목록. */
    @Query(value =
            "SELECT r.request_id FROM brand_change_request r " +
            "WHERE r.status IN (:statuses) " +
            "ORDER BY (r.status = 'PENDING') DESC, CASE WHEN r.status = 'PENDING' THEN r.requested_at END ASC, r.requested_at DESC",
            nativeQuery = true)
    List<Long> findOrderedIds(@Param("statuses") Collection<String> statuses);

    @Query("SELECT r.status, COUNT(r) FROM BrandChangeRequest r JOIN r.market m " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR m.marketName LIKE CONCAT('%', :keyword, '%')) " +
            "GROUP BY r.status")
    List<Object[]> countByStatusGroup(@Param("keyword") String keyword);

    long countByStatus(ChangeRequestStatus status);
}
