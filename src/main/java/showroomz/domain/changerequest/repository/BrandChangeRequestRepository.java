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
     * 처리 완료 건은 요청일시 최신순으로 내려간다. 요청자(브랜드 · 인플루언서)가 섞여도 한 정렬이다 · 검색은 브랜드명 · 쇼룸명(rev.7).
     */
    @Query(value =
            "SELECT * FROM brand_change_request r " +
            "WHERE r.status IN (:statuses) " +
            "AND (:keyword IS NULL OR :keyword = '' OR EXISTS (" +
            "    SELECT 1 FROM market m WHERE m.market_id = r.market_id AND m.market_name LIKE CONCAT('%', :keyword, '%')" +
            ") OR EXISTS (" +
            "    SELECT 1 FROM creator c WHERE c.creator_id = r.creator_id AND c.showroom_name LIKE CONCAT('%', :keyword, '%')" +
            ")) " +
            "ORDER BY (r.status = 'PENDING') DESC, CASE WHEN r.status = 'PENDING' THEN r.requested_at END ASC, r.requested_at DESC",
            countQuery =
            "SELECT COUNT(*) FROM brand_change_request r " +
            "WHERE r.status IN (:statuses) " +
            "AND (:keyword IS NULL OR :keyword = '' OR EXISTS (" +
            "    SELECT 1 FROM market m WHERE m.market_id = r.market_id AND m.market_name LIKE CONCAT('%', :keyword, '%')" +
            ") OR EXISTS (" +
            "    SELECT 1 FROM creator c WHERE c.creator_id = r.creator_id AND c.showroom_name LIKE CONCAT('%', :keyword, '%')" +
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

    /** 탭 건수 — 인플루언서 요청은 market 이 없으므로 LEFT JOIN 이어야 빠지지 않는다(16 설계서 2-4). */
    @Query("SELECT r.status, COUNT(r) FROM BrandChangeRequest r LEFT JOIN r.market m LEFT JOIN r.creator c " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR m.marketName LIKE CONCAT('%', :keyword, '%') " +
            "    OR c.showroomName LIKE CONCAT('%', :keyword, '%')) " +
            "GROUP BY r.status")
    List<Object[]> countByStatusGroup(@Param("keyword") String keyword);

    long countByStatus(ChangeRequestStatus status);

    /** 어드민 브랜드 상세 — 정산 정보 카드 「변경 요청 대기 N건」(52 설계서 2-2). 유형 무관 · 오래된 순. */
    List<BrandChangeRequest> findByMarket_IdAndStatusOrderByRequestedAtAsc(Long marketId, ChangeRequestStatus status);

    /** 어드민 브랜드 상세 처리 이력의 파생 행(52 설계서 5-2). */
    List<BrandChangeRequest> findByMarket_IdOrderByRequestedAtDesc(Long marketId);

    long countByRequestCode(String requestCode);

    // ------------------------------------------------------------------ 인플루언서 사업자 구분 변경(§16-2a · 50 설계서 7절)

    /** 열린 요청 — 인플루언서당 1건(방향 무관 · 운영 DB 생성 컬럼 UNIQUE). 어드민 인플루언서 상세의 「변경 요청 확인 중」도 이것. */
    Optional<BrandChangeRequest> findFirstByCreator_IdAndStatusOrderByRequestedAtDesc(Long creatorId, ChangeRequestStatus status);

    /** 정산 정보 탭 배너 후보 — PENDING 이거나, 처리완료인데 아직 확인하지 않은 최신 1건(브랜드 배너와 같은 식). */
    @Query("SELECT r FROM BrandChangeRequest r WHERE r.creator.id = :creatorId " +
            "AND (r.status = :pending OR (r.status IN :terminalStatuses AND r.resultAcknowledgedAt IS NULL)) " +
            "ORDER BY r.requestedAt DESC, r.id DESC")
    List<BrandChangeRequest> findCreatorBannerCandidates(@Param("creatorId") Long creatorId,
                                                          @Param("pending") ChangeRequestStatus pending,
                                                          @Param("terminalStatuses") Collection<ChangeRequestStatus> terminalStatuses,
                                                          Pageable pageable);

    /** 처리가 끝났고 아직 확인하지 않은 인플루언서 요청 — 새 요청이 옛 결과 배너를 대신한다(50 설계서 7-1 ④). */
    List<BrandChangeRequest> findByCreator_IdAndStatusInAndResultAcknowledgedAtIsNull(Long creatorId,
                                                                                    Collection<ChangeRequestStatus> statuses);

    /** 최근 승인된 구분 변경 — 정산 정보 탭 「확정일」 · 승인된 등록증 메타. */
    Optional<BrandChangeRequest> findFirstByCreator_IdAndStatusOrderByProcessedAtDesc(Long creatorId, ChangeRequestStatus status);

    Optional<BrandChangeRequest> findFirstByCreator_IdAndTypeAndStatusOrderByProcessedAtDesc(Long creatorId,
                                                                                         ChangeRequestType type,
                                                                                         ChangeRequestStatus status);
}
