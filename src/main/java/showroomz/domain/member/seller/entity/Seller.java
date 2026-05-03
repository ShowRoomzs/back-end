package showroomz.domain.member.seller.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.type.SellerStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "SELLER")
public class Seller {

    @Id
    @Column(name = "SELLER_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL", length = 512, unique = true)
    @NotNull
    @Size(max = 512)
    private String email; // 로그인 ID로 사용

    @Column(name = "PASSWORD", length = 128)
    @NotNull
    @Size(max = 128)
    private String password;

    @Column(name = "NAME", length = 64)
    @NotNull
    @Size(max = 64)
    private String name; // 판매자 실명/대표자명

    @Column(name = "PHONE_NUMBER", length = 20)
    @Size(max = 20)
    private String phoneNumber; // 담당자 연락처

    // 활동명 (관리자 식별용)
    @Column(name = "ACTIVITY_NAME", length = 100)
    private String activityName;

    // 사업자 기본 정보
    @Column(name = "BUSINESS_TYPE", length = 50)
    private String businessType;

    @Column(name = "REPRESENTATIVE_NAME", length = 64)
    private String representativeName;

    @Column(name = "REPRESENTATIVE_CONTACT", length = 20)
    private String representativeContact;

    @Column(name = "COMPANY_NAME", length = 100)
    private String companyName;

    @Column(name = "BUSINESS_REG_NUMBER", length = 20)
    private String businessRegistrationNumber;

    @Column(name = "BUSINESS_CONDITION", length = 100)
    private String businessCondition;

    @Column(name = "BUSINESS_ADDRESS", length = 255)
    private String businessAddress;

    @Column(name = "DETAIL_ADDRESS", length = 255)
    private String detailAddress;

    @Column(name = "TAX_EMAIL", length = 512)
    private String taxEmail;

    @Column(name = "BUSINESS_LICENSE_URL", length = 1024)
    private String businessLicenseImageUrl;

    @Column(name = "MAIL_ORDER_REG_URL", length = 1024)
    private String mailOrderRegImageUrl;

    @Column(name = "MAIL_ORDER_REG_NUM", length = 100)
    private String mailOrderRegNumber;

    // 정산 계좌 정보
    @Column(name = "BANK_NAME", length = 50)
    private String bankName;

    @Column(name = "ACCOUNT_HOLDER", length = 64)
    private String accountHolder;

    @Column(name = "ACCOUNT_NUMBER", length = 100)
    private String accountNumber;

    @Column(name = "BANKBOOK_URL", length = 1024)
    private String bankbookImageUrl;

    // 약관 동의 내역
    @Column(name = "AGREE_PRIVACY_POLICY")
    private Boolean agreePrivacyPolicy;

    @Column(name = "AGREE_TERMS_OF_SERVICE")
    private Boolean agreeTermsOfService;

    @Column(name = "AGREE_OPERATION_POLICY")
    private Boolean agreeOperationPolicy;

    @Column(name = "ROLE_TYPE", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private RoleType roleType; // 주로 SELLER

    @Column(name = "STATUS", length = 20)
    @Enumerated(EnumType.STRING)
    @NotNull
    private SellerStatus status;

    @Column(name = "PROCESSED_AT")
    private LocalDateTime processedAt;

    @Column(name = "REJECTION_REASON", length = 500)
    @Size(max = 500)
    private String rejectionReason; // 반려 사유 타입(Type) 저장용

    @Column(name = "REJECTION_REASON_DETAIL", length = 1000)
    @Size(max = 1000)
    private String rejectionReasonDetail; // 선택적 상세 사유

    @Column(name = "REVIEW_MEMO", length = 500)
    @Size(max = 500)
    private String reviewMemo; // 관리자용 검토 메모

    @Column(name = "CREATED_AT")
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "MODIFIED_AT")
    @NotNull
    private LocalDateTime modifiedAt;

    // 최근 접속일
    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    // 생성자 편의 메서드
    public Seller(String email, String password, String name, String phoneNumber, LocalDateTime now) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.roleType = RoleType.SELLER;
        this.status = SellerStatus.PENDING; // 기본값을 승인 대기로 설정
        this.createdAt = now;
        this.modifiedAt = now;
    }

    // 생성자 편의 메서드 (활동명 포함)
    public Seller(String email, String password, String name, String phoneNumber, String activityName, LocalDateTime now) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.activityName = activityName;
        this.roleType = RoleType.SELLER;
        this.status = SellerStatus.PENDING; // 기본값을 승인 대기로 설정
        this.createdAt = now;
        this.modifiedAt = now;
    }
}

