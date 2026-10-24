package showroomz.domain.member.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.user.entity.UserPrivacyAccessLog;

import java.util.List;
import java.util.Optional;

public interface UserPrivacyAccessLogRepository extends JpaRepository<UserPrivacyAccessLog, Long> {

    /** 이 운영자가 이 소비자에 연 가장 최근 세션 — 유효 여부는 호출자가 {@code isActive(now)}로 본다. */
    Optional<UserPrivacyAccessLog> findFirstByUserIdAndOperatorIdOrderByAccessedAtDescIdDesc(Long userId, Long operatorId);

    /** 직전 해제 사유(M2 「무조작 10분 경과」 재해제 프리필) — 운영자 무관 최신. */
    Optional<UserPrivacyAccessLog> findFirstByUserIdOrderByAccessedAtDescIdDesc(Long userId);

    List<UserPrivacyAccessLog> findByUserId(Long userId);
}
