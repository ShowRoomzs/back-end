package showroomz.domain.member.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.user.entity.UserSanction;

import java.util.List;
import java.util.Optional;

public interface UserSanctionRepository extends JpaRepository<UserSanction, Long> {

    /** 열린 정지 — 회원당 최대 1건. */
    Optional<UserSanction> findByUserIdAndReleasedAtIsNull(Long userId);

    boolean existsByUserIdAndReleasedAtIsNull(Long userId);

    /** 가장 최근 정지 — 해제 토스트(3-1 {@code releaseNotice})는 이 행만 본다. */
    Optional<UserSanction> findFirstByUserIdOrderBySuspendedAtDescIdDesc(Long userId);

    /** 해제 토스트를 아직 확인하지 않은 해제 행 — 확인은 한 번에 전부(3-2). */
    List<UserSanction> findByUserIdAndReleasedAtIsNotNullAndReleaseNoticeAckedAtIsNull(Long userId);

    /** 정지 기간이 끝난 열린 정지의 회원 id(1021 기-02 자동 해제). */
    @org.springframework.data.jpa.repository.Query("SELECT s.userId FROM UserSanction s WHERE s.releasedAt IS NULL "
            + "AND s.endsOn IS NOT NULL AND s.endsOn < :today ORDER BY s.id ASC")
    java.util.List<Long> findMemberIdsToAutoRelease(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today);
}
