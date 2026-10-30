package showroomz.domain.member.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.member.user.type.IdentityFailReason;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;
import showroomz.domain.member.user.type.IdentityVerificationStatus;

import java.time.LocalDateTime;

/**
 * 본인인증 1회 = 1행(57 설계서 1-2). id 는 포트원에 보내는 {@code identityVerificationId} 그대로다.
 *
 * <p>포트원 응답의 개인정보({@code verified_*} · 암호문)는 {@code VERIFIED} 동안만 들고 있다가 {@link #consume} 때 비운다 — 값은
 * {@link Users} 로 갔고 여기 남길 이유가 없다. 14세 미만 · 중복 · DI 누락으로 닫는 행은 처음부터 개인정보를 쓰지 않는다.
 * {@code pgRawResponse} 는 CI · DI 가 평문으로 들어 있어 저장하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "identity_verification", indexes = @Index(name = "idx_idv_user", columnList = "user_id, requested_at"))
public class IdentityVerification {

    @Id
    @Column(name = "identity_verification_id", length = 40)
    private String id;

    @Column(name = "identity_verification_tx_id", length = 64)
    private String identityVerificationTxId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 10)
    private IdentityVerificationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private IdentityVerificationStatus status;

    @Column(name = "channel_key", nullable = false, length = 64)
    private String channelKey;

    @Column(name = "pg_tx_id", length = 64)
    private String pgTxId;

    @Column(name = "verified_name", length = 64)
    private String verifiedName;

    @Column(name = "verified_birth_date", length = 10)
    private String verifiedBirthDate;

    @Column(name = "verified_gender", length = 10)
    private String verifiedGender;

    @Column(name = "verified_phone", length = 20)
    private String verifiedPhone;

    @Column(name = "verified_operator", length = 10)
    private String verifiedOperator;

    @Column(name = "verified_foreigner")
    private Boolean verifiedForeigner;

    @Column(name = "verified_ci_enc", length = 512)
    private String verifiedCiEnc;

    @Column(name = "verified_di_enc", length = 512)
    private String verifiedDiEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", length = 40)
    private IdentityFailReason failReason;

    @Column(name = "pg_code", length = 64)
    private String pgCode;

    @Column(name = "pg_message", length = 255)
    private String pgMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private IdentityVerification(String id, Long userId, IdentityVerificationPurpose purpose, String channelKey,
                                 LocalDateTime requestedAt, LocalDateTime expiresAt) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.status = IdentityVerificationStatus.READY;
        this.channelKey = channelKey;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
    }

    public static IdentityVerification ready(String id, Long userId, IdentityVerificationPurpose purpose, String channelKey,
                                             LocalDateTime requestedAt, LocalDateTime expiresAt) {
        return new IdentityVerification(id, userId, purpose, channelKey, requestedAt, expiresAt);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /** {@code READY · VERIFIED} 가 유효 시간을 넘겼는가 — 저장하지 않고 읽을 때 판정한다(0-7). */
    public boolean isExpired(LocalDateTime now) {
        return (status == IdentityVerificationStatus.READY || status == IdentityVerificationStatus.VERIFIED)
                && !now.isBefore(expiresAt);
    }

    public boolean isUsable(LocalDateTime now) {
        return status == IdentityVerificationStatus.VERIFIED && now.isBefore(expiresAt);
    }

    /** 앱이 SDK 응답을 실어 보낸 경우 기록한다 — 판정에는 쓰지 않는다(3-2). */
    public void recordClientResult(String txId, String code, String pgCode) {
        if (txId != null && !txId.isBlank()) {
            this.identityVerificationTxId = truncate(txId, 64);
        }
        if (this.pgCode == null && pgCode != null && !pgCode.isBlank()) {
            this.pgCode = truncate(pgCode, 64);
        }
        if (this.pgMessage == null && code != null && !code.isBlank()) {
            this.pgMessage = truncate("client:" + code, 255);
        }
    }

    /** 포트원 VERIFIED — 암호문은 호출자가 만들어 넘긴다(엔티티는 키를 모른다). */
    public void verified(String name, String birthDate, String gender, String phone, String operator, Boolean foreigner,
                         String ciEnc, String diEnc, String pgTxId, LocalDateTime verifiedAt) {
        this.status = IdentityVerificationStatus.VERIFIED;
        this.verifiedName = name;
        this.verifiedBirthDate = birthDate;
        this.verifiedGender = gender;
        this.verifiedPhone = phone;
        this.verifiedOperator = operator;
        this.verifiedForeigner = foreigner;
        this.verifiedCiEnc = ciEnc;
        this.verifiedDiEnc = diEnc;
        this.pgTxId = pgTxId;
        this.verifiedAt = verifiedAt;
    }

    /** 닫는다 — 개인정보 열은 쓰지 않는다. 포트원 FAILED 면 pg_code · pg_message 를 보존한다. */
    public void failed(IdentityFailReason reason, String pgCode, String pgMessage, String pgTxId) {
        this.status = IdentityVerificationStatus.FAILED;
        this.failReason = reason;
        if (pgCode != null) {
            this.pgCode = truncate(pgCode, 64);
        }
        if (pgMessage != null) {
            this.pgMessage = truncate(pgMessage, 255);
        }
        if (pgTxId != null) {
            this.pgTxId = pgTxId;
        }
        purgePersonalData();
    }

    /** Users 로 옮겼다 — 개인정보 열을 비운다(1-2). */
    public void consume(LocalDateTime now) {
        this.status = IdentityVerificationStatus.CONSUMED;
        this.consumedAt = now;
        purgePersonalData();
    }

    /** 탈퇴 · 보존 기간 만료 때 — 상태는 그대로 두고 개인정보만 지운다. */
    public void purgePersonalData() {
        this.verifiedName = null;
        this.verifiedBirthDate = null;
        this.verifiedGender = null;
        this.verifiedPhone = null;
        this.verifiedOperator = null;
        this.verifiedForeigner = null;
        this.verifiedCiEnc = null;
        this.verifiedDiEnc = null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
