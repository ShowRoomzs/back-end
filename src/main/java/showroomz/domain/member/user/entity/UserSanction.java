package showroomz.domain.member.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.UserReleaseReason;
import showroomz.domain.member.user.type.UserSuspensionReason;

import java.time.LocalDateTime;

/**
 * 소비자 정지 1건 = 1행(51-6 설계서 1-2 · 브랜드 · 인플루언서 정지와 같은 골격) — 해제까지. 열린 정지는 {@code released_at IS NULL} 행이고
 * 회원당 최대 1건이다(운영 DB 는 생성 컬럼 {@code active_key} UNIQUE · 엔티티 미매핑 — 서비스도 같은 판정을 먼저 한다).
 * {@code users.status = SUSPENDED}는 이 행에서 파생해 저장한다.
 *
 * <p>브랜드 · 인플루언서와 다른 점 — 지급 정지 · 운영팀 카드가 없고(소비자 소명은 1:1 문의다), 앱의 「1회 노출」 두 가지를 서버가
 * 기록한다(0-7). 기기 저장소에 두면 재설치 · 기기 변경마다 다시 뜬다.
 */
@Entity
@Table(name = "user_sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSanction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private UserSuspensionReason reason;

    /** 「기타」면 필수 · 앱에 내리지 않는다. */
    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_by", nullable = false)
    private Long suspendedBy;

    @Column(name = "suspended_by_name", nullable = false, length = 64)
    private String suspendedByName;

    /** 정지 안내 시트(C20 1c) 자동 노출 확인 — null 이면 다음 앱 진입 때 1회. */
    @Column(name = "notice_acked_at")
    private LocalDateTime noticeAckedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_by_name", length = 64)
    private String releasedByName;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_reason", length = 40)
    private UserReleaseReason releaseReason;

    /** 해제 토스트 확인. */
    @Column(name = "release_notice_acked_at")
    private LocalDateTime releaseNoticeAckedAt;

    /** 정지 종료일(포함) — 다음 날 00:00 자동 해제(1021 기-02). null = 기간 없음(기존 행 · 수동 해제). */
    @Column(name = "ends_on")
    private java.time.LocalDate endsOn;

    /** 이의 신청 기한 — 통지 + N영업일 23:59:59(표시용 · 접수 차단 안 함). */
    @Column(name = "appeal_deadline_at")
    private LocalDateTime appealDeadlineAt;

    @org.hibernate.annotations.ColumnDefault("false")
    @Column(name = "auto_released", nullable = false)
    private boolean autoReleased;

    public static UserSanction suspend(Long userId, UserSuspensionReason reason, String reasonDetail, Long operatorId,
                                       String operatorName, LocalDateTime now) {
        UserSanction sanction = new UserSanction();
        sanction.userId = userId;
        sanction.reason = reason;
        sanction.reasonDetail = reasonDetail;
        sanction.suspendedAt = now;
        sanction.suspendedBy = operatorId;
        sanction.suspendedByName = operatorName;
        return sanction;
    }

    /** 정지 기간 · 이의 신청 기한(1021 기-02) — 정지 때 한 번. */
    public void applyTerm(java.time.LocalDate endsOn, LocalDateTime appealDeadlineAt) {
        this.endsOn = endsOn;
        this.appealDeadlineAt = appealDeadlineAt;
    }

    /** 기간이 끝났는가 — 종료일 다음 날부터. */
    public boolean isTermEnded(java.time.LocalDate today) {
        return endsOn != null && endsOn.isBefore(today);
    }

    public void markAutoReleased() {
        this.autoReleased = true;
    }

    public boolean isOpen() {
        return releasedAt == null;
    }

    public void release(UserReleaseReason reason, Long operatorId, String operatorName, LocalDateTime now) {
        this.releasedAt = now;
        this.releaseReason = reason;
        this.releasedBy = operatorId;
        this.releasedByName = operatorName;
    }

    public boolean isNoticePending() {
        return isOpen() && noticeAckedAt == null;
    }

    public void ackNotice(LocalDateTime now) {
        if (noticeAckedAt == null) {
            this.noticeAckedAt = now;
        }
    }

    /** 정지 중 탈퇴한 회원이 같은 계정으로 돌아왔다 — 안내를 다시 보여준다(6절). */
    public void resetNotice() {
        this.noticeAckedAt = null;
    }

    public boolean isReleaseNoticePending() {
        return !isOpen() && releaseNoticeAckedAt == null;
    }

    public void ackReleaseNotice(LocalDateTime now) {
        if (releaseNoticeAckedAt == null) {
            this.releaseNoticeAckedAt = now;
        }
    }

    /** 이력에 쓰는 사유 문장 — 「욕설·비방 — 상세」. */
    public String reasonText() {
        if (reasonDetail == null || reasonDetail.isBlank()) {
            return reason.getLabel();
        }
        return reason.getLabel() + " — " + reasonDetail;
    }
}
