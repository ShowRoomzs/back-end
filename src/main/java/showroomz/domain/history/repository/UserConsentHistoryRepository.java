package showroomz.domain.history.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.history.entity.UserConsentHistory;

public interface UserConsentHistoryRepository extends JpaRepository<UserConsentHistory, Long> {

    /** 그 소비자의 그 항목 마지막 동의 · 철회 기록. */
    java.util.Optional<UserConsentHistory> findFirstByUser_IdAndConsentTypeOrderByCreatedAtDescIdDesc(
            Long userId, showroomz.domain.history.type.ConsentType consentType);
}
