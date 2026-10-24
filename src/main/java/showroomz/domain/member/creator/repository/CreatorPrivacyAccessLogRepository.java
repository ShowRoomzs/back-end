package showroomz.domain.member.creator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.CreatorPrivacyAccessLog;

import java.util.List;
import java.util.Optional;

public interface CreatorPrivacyAccessLogRepository extends JpaRepository<CreatorPrivacyAccessLog, Long> {

    /** 이 운영자가 이 인플루언서에 연 가장 최근 세션 — 활성 여부는 호출자가 {@code isActive(now)}로 본다. */
    Optional<CreatorPrivacyAccessLog> findFirstByCreatorIdAndOperatorIdOrderByAccessedAtDescIdDesc(Long creatorId,
                                                                                                     Long operatorId);

    /** 직전 해제 사유(M4 프리필) — 운영자 무관 최신. */
    Optional<CreatorPrivacyAccessLog> findFirstByCreatorIdOrderByAccessedAtDescIdDesc(Long creatorId);

    List<CreatorPrivacyAccessLog> findByCreatorId(Long creatorId);
}
