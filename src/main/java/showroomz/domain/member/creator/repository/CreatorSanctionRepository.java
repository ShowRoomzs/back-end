package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.creator.entity.CreatorSanction;

import java.util.Optional;

public interface CreatorSanctionRepository extends JpaRepository<CreatorSanction, Long> {

    /** 열린 정지 — 인플루언서당 최대 1건. */
    Optional<CreatorSanction> findByCreatorIdAndReleasedAtIsNull(Long creatorId);

    /** 가장 최근 정지 — 알림이 정지 · 해제 카드 위치를 찾는다. */
    Optional<CreatorSanction> findFirstByCreatorIdOrderBySuspendedAtDesc(Long creatorId);

    boolean existsByCreatorIdAndReleasedAtIsNull(Long creatorId);

    /** 리워드 지급 정지가 걸린 열린 정지가 있는가 — 정산 포트 {@code PayoutHoldReader}. */
    @Query("SELECT COUNT(s) > 0 FROM CreatorSanction s WHERE s.creatorId = :creatorId AND s.releasedAt IS NULL "
            + "AND s.payoutHeld = true")
    boolean existsOpenPayoutHold(@Param("creatorId") Long creatorId);
}
