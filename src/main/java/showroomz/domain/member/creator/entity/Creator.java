package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

@Entity
@Table(name = "CREATOR")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Creator extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CREATOR_ID")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false, unique = true)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "SNS_TYPE", nullable = false, length = 20)
    private SnsType snsType;

    @Column(name = "CHANNEL_URL", nullable = false, length = 512)
    private String channelUrl;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 100)
    private String accountId;

    @Column(name = "FOLLOWER_COUNT", nullable = false)
    private Integer followerCount;

    @Column(name = "BUSINESS_EMAIL", nullable = false, length = 512)
    private String businessEmail;

    @Column(name = "REAL_NAME", length = 64)
    private String realName;

    @Column(name = "BIRTHDAY", length = 10)
    private String birthday;

    @Column(name = "PHONE_NUMBER", length = 20)
    private String phoneNumber;

    @Column(name = "IS_NEW_MEMBER", nullable = false)
    @Builder.Default
    private Boolean isNewMember = true;

    @Column(name = "SHOWROOM_NAME", length = 100)
    private String showroomName;

    /** §22-1 쇼룸 프로필 이미지 — 소비자 앱 계정(Users.profileImageUrl)과 공유하지 않는 별개 값이다. */
    @Column(name = "PROFILE_IMAGE_URL", length = 1024)
    private String profileImageUrl;

    /** §22-1 쇼룸 주소 핸들 — 가입 시 쇼룸명 기준으로 자동 생성되고, 이후 쇼룸명을 바꿔도 따라 바뀌지 않는다. */
    @Column(name = "SHOWROOM_ADDRESS", length = 64, unique = true)
    private String showroomAddress;

    /** §22-1 쇼룸 소개글 — 최대 50자. */
    @Column(name = "INTRODUCTION", length = 50)
    private String introduction;

    /** §22-1 인스타그램 URL — 소비자 노출용 공개 필드. 기본정보 관리(#9)의 활동 채널과는 별개 데이터다. */
    @Column(name = "INSTAGRAM_URL", length = 512)
    private String instagramUrl;

    @Column(name = "CONNECTION_CODE", length = 16, unique = true)
    private String connectionCode;

    @Column(name = "CONNECTION_CODE_ISSUED_AT")
    private LocalDateTime connectionCodeIssuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "BUSINESS_TYPE", length = 30)
    private CreatorBusinessType businessType;

    @Column(name = "BUSINESS_REGISTRATION_NUMBER", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "BUSINESS_LICENSE_IMAGE_URL", length = 1024)
    private String businessLicenseImageUrl;

    @Column(name = "BANK_NAME", length = 50)
    private String bankName;

    @Column(name = "ACCOUNT_NUMBER", length = 100)
    private String accountNumber;

    @Column(name = "BANKBOOK_IMAGE_URL", length = 1024)
    private String bankbookImageUrl;

    // PG 지급대행 셀러 연결(44 토스 설계서 3-1) — 인플루언서 = 토스 셀러 1건
    @Column(name = "PG_PARTNER_ID", length = 64)
    private String pgPartnerId;

    @Column(name = "PG_PARTNER_REF", length = 20)
    private String pgPartnerRef;

    @Column(name = "PG_PARTNER_ENV", length = 8)
    private String pgPartnerEnv;

    @Column(name = "PG_PARTNER_STATUS", length = 32)
    private String pgPartnerStatus;

    @Column(name = "PG_PARTNER_SYNCED_AT")
    private LocalDateTime pgPartnerSyncedAt;

    /** PG 에 보낸 계좌의 sha256(bank|number|holder) — 평문 비교 없이 변경을 감지한다. */
    @Column(name = "PG_PARTNER_ACCOUNT_HASH", length = 64)
    private String pgPartnerAccountHash;

    /**
     * 인플루언서 회원 상태(53 설계서 0-1) — 소비자 계정의 {@code Users.status}와 다른 축이다. 전이는 서비스의 조건부 UPDATE
     * ({@code CreatorRepository.transitionStatus})가 집행하고, 정지 사유 · 처리자는 {@code creator_sanction} 행이 든다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    @Builder.Default
    private CreatorStatus status = CreatorStatus.ACTIVE;

    @Column(name = "WITHDRAWN_AT")
    private LocalDateTime withdrawnAt;

    @Column(name = "WITHDRAWN_BY")
    private Long withdrawnBy;

    /** 탈퇴 승인 즉시 파기를 수행한 시각(0-10) — 값이 있으면 본인확인 · 정산 카드는 「— 파기됨」이다. */
    @Column(name = "PRIVACY_PURGED_AT")
    private LocalDateTime privacyPurgedAt;

    public void linkPgPartner(String externalId, String ref, String env, String status, String accountHash, LocalDateTime at) {
        this.pgPartnerId = externalId;
        this.pgPartnerRef = ref;
        this.pgPartnerEnv = env;
        this.pgPartnerStatus = status;
        this.pgPartnerAccountHash = accountHash;
        this.pgPartnerSyncedAt = at;
    }

    /** 웹훅({@code seller.changed}) — 상태만. */
    public void updatePgPartnerStatus(String status, LocalDateTime at) {
        this.pgPartnerStatus = status;
        this.pgPartnerSyncedAt = at;
    }

    /**
     * 주민등록번호 암호문 — 비사업자만(1009 기획 수정본 7-a). 원천징수 신고에만 복호화한다. 응답에 내리지 않는다.
     * 저장 형식은 {@code PersonalDataCipher} 참조.
     */
    @Column(name = "RESIDENT_REGISTRATION_NUMBER_ENC", length = 255)
    private String residentRegistrationNumberEnc;

    /** 표시용 마스킹 — {@code 900101-1******}. 화면은 원문 대신 이 값을 읽는다. */
    @Column(name = "RESIDENT_REGISTRATION_NUMBER_MASKED", length = 14)
    private String residentRegistrationNumberMasked;

    public void completeRegistration(
            String showroomName,
            CreatorBusinessType businessType,
            String businessRegistrationNumber,
            String businessLicenseImageUrl,
            String bankName,
            String accountNumber,
            String bankbookImageUrl) {
        this.showroomName = showroomName;
        this.businessType = businessType;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.businessLicenseImageUrl = businessLicenseImageUrl;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.bankbookImageUrl = bankbookImageUrl;
        this.isNewMember = false;
    }

    /** 주민등록번호 등록 — 온보딩이 유일한 수집 지점이다. 사업자는 null 로 지운다. */
    public void registerResidentNumber(String encrypted, String masked) {
        this.residentRegistrationNumberEnc = encrypted;
        this.residentRegistrationNumberMasked = masked;
    }

    public boolean isResidentNumberRegistered() {
        return residentRegistrationNumberEnc != null;
    }

    /**
     * §22-1 쇼룸 프로필 수정 — 소비자에게 공개되는 값만 바꾼다.
     * 쇼룸 주소는 여기에 없다. 자동 생성 후 수정 불가이며, 쇼룸명을 바꿔도 따라 바뀌지 않는다.
     */
    public void updateShowroomProfile(String showroomName, String introduction, String instagramUrl) {
        this.showroomName = showroomName;
        this.introduction = introduction;
        this.instagramUrl = instagramUrl;
    }

    /** §22-1 프로필 이미지 변경·삭제 — 삭제는 null로 되돌려 기본 이미지를 쓰게 한다. */
    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * §22-1 — 인스타그램 URL의 기본값은 가입 온보딩에서 받은 채널 주소다(이후 독립 수정 가능).
     * 인스타그램이 아닌 채널로 가입했다면 가져올 값이 없으므로 비워 둔다.
     */
    public void initializeInstagramUrlFromChannel() {
        if (this.instagramUrl == null && this.snsType == SnsType.INSTAGRAM) {
            this.instagramUrl = this.channelUrl;
        }
    }

    /** §22-1 쇼룸 주소는 발급 시점에 한 번만 정해진다 — 이미 있으면 덮어쓰지 않는다. */
    public void assignShowroomAddressIfAbsent(String showroomAddress) {
        if (this.showroomAddress == null || this.showroomAddress.isBlank()) {
            this.showroomAddress = showroomAddress;
        }
    }

    /** 정지 — 호출자가 행을 잠그고 {@code ACTIVE}임을 확인한 뒤 부른다. 사유 · 처리자는 {@code creator_sanction} 행이 든다. */
    public void markSuspended() {
        this.status = CreatorStatus.SUSPENDED;
    }

    /** 정지 해제 — 호출자가 행을 잠그고 {@code SUSPENDED}임을 확인한 뒤 부른다. */
    public void markActive() {
        this.status = CreatorStatus.ACTIVE;
    }

    public boolean isWithdrawn() {
        return status == CreatorStatus.WITHDRAWN;
    }

    public boolean isSuspended() {
        return status == CreatorStatus.SUSPENDED;
    }

    public boolean isPrivacyPurged() {
        return privacyPurgedAt != null;
    }

    /**
     * 탈퇴 승인 — 즉시 파기 집합(53 설계서 0-10). 본인확인(이름 · 생년월일 · 휴대폰) · 정산 계좌 · 서류 이미지 · 인스타 · 연결코드를 지운다.
     *
     * <p><b>보존</b>: 주민등록번호 암호문 · 사업자등록번호 · 사업자 여부(원천세 신고 · 지급명세서 · 세금계산서 기록의 식별자 — 보존 기간은
     * 쟁점 G 자문 대기) · 업무 이메일(시안대로) · 쇼룸명 · 쇼룸 주소 · 프로필 이미지 · PG 셀러 연결 id. 상태 전이는 호출자가 조건부
     * UPDATE 로 먼저 끝낸다 — 여기서는 같은 값을 엔티티에도 맞춘다.
     */
    public void purgeOnWithdrawal(Long operatorId, LocalDateTime now) {
        this.status = CreatorStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.withdrawnBy = operatorId;
        this.realName = null;
        this.birthday = null;
        this.phoneNumber = null;
        this.bankName = null;
        this.accountNumber = null;
        this.bankbookImageUrl = null;
        this.businessLicenseImageUrl = null;
        this.instagramUrl = null;
        this.channelUrl = "";
        this.connectionCode = null;
        this.connectionCodeIssuedAt = null;
        this.privacyPurgedAt = now;
    }

    /** §13-6 — 연결코드는 쇼룸별 고정(영구) 발급, 인플루언서가 원하면 재발급 가능(값 자체는 애플리케이션에서 생성해 전달). */
    public void reissueConnectionCode(String connectionCode) {
        this.connectionCode = connectionCode;
        this.connectionCodeIssuedAt = LocalDateTime.now();
    }
}
