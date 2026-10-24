package showroomz.domain.member.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.common.entity.PrivacyAccessSession;
import showroomz.domain.member.user.type.UserPrivacyAccessReason;

import java.time.LocalDateTime;

/**
 * 소비자 본인확인 정보 열람 로그 1행 = 마스킹 해제 세션 1개(ui-admin-13 M2 · 55 계획서 M3-3 · V216). 세션 규칙은
 * {@link PrivacyAccessSession}이 정본이고 인플루언서 로그({@code CreatorPrivacyAccessLog})와 같다 — 이 소비자 1건 · 이 운영자 · 10분 고정
 * 만료 · [지금 잠금].
 *
 * <p>열리는 값은 이름 · 생년월일 · 휴대폰과 <b>배송지 상세</b>(상세주소 · 우편번호 · 수령인 · 연락처)다 — 시안 A4 「주소는 본인확인 정보와 같은
 * 열람 통제를 받는다」. CI/DI 는 화면에 없고 해제 대상도 아니다. 보존 기간 미정이라 삭제 배치 없음.
 */
@Entity
@Table(name = "user_privacy_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPrivacyAccessLog implements PrivacyAccessSession {

    public static final String FIELDS = "NAME,BIRTHDAY,PHONE,ADDRESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private UserPrivacyAccessReason reason;

    @Column(name = "fields", nullable = false, length = 255)
    private String fields;

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    public static UserPrivacyAccessLog open(Long userId, Long operatorId, UserPrivacyAccessReason reason,
                                            LocalDateTime now) {
        UserPrivacyAccessLog log = new UserPrivacyAccessLog();
        log.userId = userId;
        log.operatorId = operatorId;
        log.reason = reason;
        log.fields = FIELDS;
        log.accessedAt = now;
        log.expiresAt = now.plus(TTL);
        return log;
    }

    @Override
    public void lock(LocalDateTime now) {
        this.lockedAt = now;
    }
}
