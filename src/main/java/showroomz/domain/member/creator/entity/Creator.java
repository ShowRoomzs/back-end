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

    /** §13-6 — 연결코드는 쇼룸별 고정(영구) 발급, 인플루언서가 원하면 재발급 가능(값 자체는 애플리케이션에서 생성해 전달). */
    public void reissueConnectionCode(String connectionCode) {
        this.connectionCode = connectionCode;
        this.connectionCodeIssuedAt = LocalDateTime.now();
    }
}
