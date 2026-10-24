package showroomz.domain.member.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.member.user.entity.IdentityVerification;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;
import showroomz.domain.member.user.type.IdentityVerificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, String> {

    /** 소비할 행 — 같은 사용자 · 같은 용도 · VERIFIED · 미만료 중 가장 최근(57 설계서 1-3). */
    @Query("""
            SELECT v FROM IdentityVerification v
            WHERE v.userId = :userId AND v.purpose = :purpose AND v.status = 'VERIFIED' AND v.expiresAt > :now
            ORDER BY v.verifiedAt DESC
            LIMIT 1
            """)
    Optional<IdentityVerification> findLatestVerifiedUnconsumed(@Param("userId") Long userId,
                                                                @Param("purpose") IdentityVerificationPurpose purpose,
                                                                @Param("now") LocalDateTime now);

    /** 연타 멱등(3-1) — 같은 사용자 · 같은 용도의 READY 행이 {@code since} 이후에 있으면 그대로 돌려준다. */
    @Query("""
            SELECT v FROM IdentityVerification v
            WHERE v.userId = :userId AND v.purpose = :purpose AND v.status = 'READY' AND v.requestedAt >= :since
            ORDER BY v.requestedAt DESC
            LIMIT 1
            """)
    Optional<IdentityVerification> findRecentReady(@Param("userId") Long userId,
                                                   @Param("purpose") IdentityVerificationPurpose purpose,
                                                   @Param("since") LocalDateTime since);

    /** 탈퇴 — 그 사용자의 행에 남은 개인정보를 즉시 비운다(5절). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IdentityVerification v
            SET v.verifiedName = NULL, v.verifiedBirthDate = NULL, v.verifiedGender = NULL, v.verifiedPhone = NULL,
                v.verifiedOperator = NULL, v.verifiedForeigner = NULL, v.verifiedCiEnc = NULL, v.verifiedDiEnc = NULL
            WHERE v.userId = :userId
            """)
    int purgePersonalDataByUser(@Param("userId") Long userId);

    /** 보존 기간이 지난 미소비 · 만료 행 — 개인정보가 남아 있는 것만(1-2 파기 배치). */
    @Query("""
            SELECT v FROM IdentityVerification v
            WHERE v.status IN :statuses AND v.requestedAt < :before AND v.verifiedDiEnc IS NOT NULL
            ORDER BY v.requestedAt ASC
            LIMIT :limit
            """)
    List<IdentityVerification> findStaleWithPersonalData(@Param("statuses") List<IdentityVerificationStatus> statuses,
                                                         @Param("before") LocalDateTime before,
                                                         @Param("limit") int limit);
}
