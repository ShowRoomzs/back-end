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

    @Column(name = "IS_NEW_MEMBER", nullable = false)
    @Builder.Default
    private Boolean isNewMember = true;

    @Column(name = "SHOWROOM_NAME", length = 100)
    private String showroomName;

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
}
