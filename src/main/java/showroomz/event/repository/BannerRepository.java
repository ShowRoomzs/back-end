package showroomz.event.repository;
import org.springframework.boot.Banner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import showroomz.event.entitiy.BannerPosition;

import java.time.LocalDateTime;
import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, Long> { // ID 타입

    /**
     * 특정 위치의 '활성화된' 그리고 '기간이 유효한' 배너들을
     * '정렬 순서(sortOrder)' 오름차순으로 조회합니다.
     *
     * @param position  배너 위치 (e.g., HOME_MAIN)
     * @param now       현재 시간
     * @param pageable  limit 파라미터를 Pageable.ofSize(limit)로 변환하여 전달
     * @return
     */
    @Query("SELECT b FROM Banner b " +
           "WHERE b.position = :position " +           // 1. 요청한 위치
           "AND b.isActive = true " +                // 2. 활성화된 배너만
           "AND (b.startedAt IS NULL OR b.startedAt <= :now) " + // 3. 시작 시간이 지났거나
           "AND (b.endedAt IS NULL OR b.endedAt >= :now) " +   // 4. 종료 시간이 안 지났거나
           "ORDER BY b.sortOrder ASC, b.id DESC")      // 5. 정렬 순서로 정렬
    List<Banner> findActiveBannersByPosition(
        BannerPosition position,
        LocalDateTime now,
        Pageable pageable
    );

    // 'limit' 파라미터가 없는 경우 (전체 조회)
    List<Banner> findByPositionAndIsActiveTrueAndStartedAtBeforeAndEndedAtAfterOrderBySortOrderAsc(
        BannerPosition position,
        LocalDateTime startedAtTime,
        LocalDateTime endedAtTime
    );
}