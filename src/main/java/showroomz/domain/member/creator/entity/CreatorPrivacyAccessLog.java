package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.creator.type.CreatorPrivacyAccessReason;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 본인확인 정보 열람 로그 1행 = 마스킹 해제 세션 1개(§53-2 · 53 설계서 0-6 · 2-3). 세션은 <b>이 인플루언서 1건 · 이 운영자</b>에 묶이고,
 * 무조작 10분은 {@code accessed_at + 10분} 고정 만료로 둔다(슬라이딩이면 조회마다 쓰기가 든다 · 9-2 #7). [지금 잠금]은 {@code locked_at}.
 *
 * <p>상세 사유 · 기타 없음 · 보존 기간 미정이라 삭제 배치 없음.
 */
@Entity
@Table(name = "creator_privacy_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorPrivacyAccessLog {

    public static final Duration SESSION_TTL = Duration.ofMinutes(10);
    public static final String FIELDS = "NAME,BIRTHDAY,PHONE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private CreatorPrivacyAccessReason reason;

    @Column(name = "fields", nullable = false, length = 255)
    private String fields;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    public static CreatorPrivacyAccessLog open(Long creatorId, Long operatorId, CreatorPrivacyAccessReason reason,
                                               LocalDateTime now) {
        CreatorPrivacyAccessLog log = new CreatorPrivacyAccessLog();
        log.creatorId = creatorId;
        log.operatorId = operatorId;
        log.reason = reason;
        log.fields = FIELDS;
        log.accessedAt = now;
        log.expiresAt = now.plus(SESSION_TTL);
        return log;
    }

    public boolean isActive(LocalDateTime now) {
        return lockedAt == null && expiresAt.isAfter(now);
    }

    public void lock(LocalDateTime now) {
        this.lockedAt = now;
    }
}
