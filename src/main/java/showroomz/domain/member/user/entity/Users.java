package showroomz.domain.member.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.vo.NotificationSetting;
import showroomz.domain.member.user.vo.RefundAccount;
import showroomz.domain.bank.entity.Bank;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "USERS")
public class Users {
    @JsonIgnore
    @Id
    @Column(name = "USER_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USERNAME", length = 64, unique = true)
    @NotNull
    @Size(max = 64)
    private String username; // 로그인 아이디

    @Column(name = "NICKNAME", length = 100)
    @NotNull
    @Size(max = 100)
    private String nickname;

    @Column(name = "NAME", length = 64)
    @Size(max = 64)
    private String name; // 실명(판매자 이름)

    @Column(name = "PHONE_NUMBER", length = 20)
    @Size(max = 20)
    private String phoneNumber; // 연락처

    @JsonIgnore
    @Column(name = "PASSWORD", length = 128)
    @NotNull
    @Size(max = 128)
    private String password;

    @Column(name = "EMAIL", length = 512, unique = true)
    @NotNull
    @Size(max = 512)
    private String email;

    @Column(name = "EMAIL_VERIFIED_YN", length = 1)
    @NotNull
    @Size(min = 1, max = 1)
    private String emailVerifiedYn;

    @Column(name = "PROFILE_IMAGE_URL", length = 512)
    @Size(max = 512)
    private String profileImageUrl;

    @Column(name = "GENDER", length = 10)
    private String gender; // "MALE", "FEMALE", null

    @Column(name = "BIRTHDAY", length = 10)
    private String birthday; // "YYYY-MM-DD"

    @Column(name = "PROVIDER_TYPE", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private ProviderType providerType;

    @Column(name = "ROLE_TYPE", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private RoleType roleType;

    @Column(name = "CREATED_AT")
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "MODIFIED_AT")
    @NotNull
    private LocalDateTime modifiedAt;

    @Column(name = "AGE_AGREE")
    private boolean ageAgree; // [필수] 만 14세 이상입니다

    @Column(name = "SERVICE_AGREE")
    private boolean serviceAgree;

    @Column(name = "PRIVACY_AGREE")
    private boolean privacyAgree;

    @Column(name = "MARKETING_AGREE")
    private boolean marketingAgree; // [선택] 광고성 정보 수신 — C15 설정의 토글과 같은 값

    // 광고성 정보 수신 동의/철회를 마지막으로 바꾼 시각 (철회 통지 근거)
    @Column(name = "MARKETING_AGREE_CHANGED_AT")
    private LocalDateTime marketingAgreeChangedAt;

    @Column(name = "STATUS", nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    // 최근 접속일
    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    // C0-2 본인인증(PASS) 완료 시각 (null이면 미인증)
    @Column(name = "IDENTITY_VERIFIED_AT")
    private LocalDateTime identityVerifiedAt;

    @Embedded
    private NotificationSetting notificationSetting;

    @Embedded
    private RefundAccount refundAccount; // 환불 계좌 정보 (null 가능)

    @Column(name = "ADMIN_MEMO", length = 500)
    @Size(max = 500)
    private String adminMemo;

    public Users(
            @NotNull @Size(max = 64) String username,
            @NotNull @Size(max = 100) String nickname,
            @NotNull @Size(max = 512) String email,
            @NotNull @Size(max = 1) String emailVerifiedYn,
            @Size(max = 512) String profileImageUrl,
            @NotNull ProviderType providerType,
            @NotNull RoleType roleType,
            @NotNull LocalDateTime createdAt,
            @NotNull LocalDateTime modifiedAt
    ) {
        this.username = username;
        this.nickname = nickname;
        this.password = "NO_PASS";
        this.email = email != null ? email : "NO_EMAIL";
        this.emailVerifiedYn = emailVerifiedYn;
        this.profileImageUrl = profileImageUrl; // null 허용
        this.providerType = providerType;
        this.roleType = roleType;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
        this.status = UserStatus.NORMAL; // 생성 시 기본값 설정
        this.notificationSetting = new NotificationSetting(); // 기본값으로 초기화
    }

    // 상태 변경을 위한 메서드 추가 (비즈니스 로직용)
    public void updateStatus(UserStatus status) {
        this.status = status;
    }

    // 환불 계좌 정보 업데이트 메서드
    public void updateRefundAccount(Bank bank, String accountNumber, String accountHolder) {
        this.refundAccount = RefundAccount.builder()
                .bank(bank)
                .accountNumber(accountNumber)
                .accountHolder(accountHolder)
                .build();
    }

    // 알림 설정 변경 메서드
    public void updateNotificationSettings(Boolean followPostPushAgree) {
        if (this.notificationSetting == null) {
            this.notificationSetting = new NotificationSetting();
        }
        this.notificationSetting.update(followPostPushAgree);
    }

    /**
     * 광고성 정보 수신 동의 변경. 값이 실제로 바뀐 경우에만 변경 시각을 갱신하고 true를 반환한다.
     * (같은 값을 다시 눌렀을 때 철회 일시가 덮어써지면 통지 근거가 어긋난다)
     */
    public boolean updateMarketingAgree(boolean agree) {
        if (this.marketingAgree == agree) {
            return false;
        }
        this.marketingAgree = agree;
        this.marketingAgreeChangedAt = LocalDateTime.now();
        return true;
    }

    /**
     * C15-4 본인인증 재인증 결과로 이름·생년월일·성별·휴대폰번호를 갱신한다.
     * 통신사 원장 값이라 사용자가 직접 입력하지 않는다.
     */
    public void updateIdentity(String name, String birthday, String gender, String phoneNumber, LocalDateTime verifiedAt) {
        this.name = name;
        this.birthday = birthday;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.identityVerifiedAt = verifiedAt;
        this.modifiedAt = LocalDateTime.now();
    }

    /**
     * C15-4 탈퇴 시 계정 식별 정보 파기.
     * 팔로잉·좋아요·장바구니 등 활동 기록은 각 리포지토리에서 삭제하고, 여기서는 회원 행에 남는 값만 지운다.
     * 주문·결제 기록은 전자상거래법상 법정 기간 동안 분리 보관해야 하므로 건드리지 않는다.
     */
    public void purgeOnWithdrawal() {
        this.nickname = "탈퇴한 회원" + this.id;
        this.name = null;
        this.phoneNumber = null;
        this.birthday = null;
        this.gender = null;
        this.profileImageUrl = null;
        this.identityVerifiedAt = null;
        this.refundAccount = null;
        this.marketingAgree = false;
        this.marketingAgreeChangedAt = LocalDateTime.now();
    }

    public void updateAdminMemo(String adminMemo) {
        this.adminMemo = adminMemo;
    }

    public void updateRoleType(RoleType newRoleType) {
        this.roleType = newRoleType;
    }

    /**
     * 크리에이터 입점 신청 반려 시 약관 동의 이력을 파기합니다.
     * (실명·생년월일·연락처는 CreatorApplication에서 파기/해시)
     */
    public void purgeAgreementsOnCreatorRejection() {
        this.ageAgree = false;
        this.serviceAgree = false;
        this.privacyAgree = false;
        this.marketingAgree = false;
    }
}

