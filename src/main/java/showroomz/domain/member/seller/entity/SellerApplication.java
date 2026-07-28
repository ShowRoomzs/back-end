package showroomz.domain.member.seller.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.global.utils.BusinessRegistrationNumberHasher;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "seller_application")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerApplication extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "market_name", length = 255)
    private String marketName;

    @Column(name = "cs_number", length = 255)
    private String csNumber;

    @Column(name = "seller_name", length = 64)
    private String sellerName;

    @Column(name = "seller_contact", length = 20)
    private String sellerContact;

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "representative_name", length = 64)
    private String representativeName;

    @Column(name = "representative_contact", length = 20)
    private String representativeContact;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "business_reg_number", length = 128)
    private String businessRegistrationNumber;

    @Column(name = "business_condition", length = 100)
    private String businessCondition;

    @Column(name = "business_address", length = 255)
    private String businessAddress;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @Column(name = "tax_email", length = 512)
    private String taxEmail;

    @Column(name = "business_license_url", length = 1024)
    private String businessLicenseImageUrl;

    @Column(name = "mail_order_reg_url", length = 1024)
    private String mailOrderRegImageUrl;

    @Column(name = "mail_order_reg_num", length = 100)
    private String mailOrderRegNumber;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "account_holder", length = 64)
    private String accountHolder;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "bankbook_url", length = 1024)
    private String bankbookImageUrl;

    @Column(name = "agree_privacy_policy")
    private Boolean agreePrivacyPolicy;

    @Column(name = "agree_terms_of_service")
    private Boolean agreeTermsOfService;

    @Column(name = "agree_operation_policy")
    private Boolean agreeOperationPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "reject_reason_detail", length = 1000)
    private String rejectReasonDetail;

    public static SellerApplication createFrom(Seller seller, String marketName, String csNumber) {
        return SellerApplication.builder()
                .seller(seller)
                .marketName(marketName)
                .csNumber(csNumber)
                .sellerName(seller.getName())
                .sellerContact(seller.getPhoneNumber())
                .businessType(seller.getBusinessType())
                .representativeName(seller.getRepresentativeName())
                .representativeContact(seller.getRepresentativeContact())
                .companyName(seller.getCompanyName())
                .businessRegistrationNumber(seller.getBusinessRegistrationNumber())
                .businessCondition(seller.getBusinessCondition())
                .businessAddress(seller.getBusinessAddress())
                .detailAddress(seller.getDetailAddress())
                .taxEmail(seller.getTaxEmail())
                .businessLicenseImageUrl(seller.getBusinessLicenseImageUrl())
                .mailOrderRegImageUrl(seller.getMailOrderRegImageUrl())
                .mailOrderRegNumber(seller.getMailOrderRegNumber())
                .bankName(seller.getBankName())
                .accountHolder(seller.getAccountHolder())
                .accountNumber(seller.getAccountNumber())
                .bankbookImageUrl(seller.getBankbookImageUrl())
                .agreePrivacyPolicy(seller.getAgreePrivacyPolicy())
                .agreeTermsOfService(seller.getAgreeTermsOfService())
                .agreeOperationPolicy(seller.getAgreeOperationPolicy())
                .status(SellerStatus.PENDING)
                .build();
    }

    public void approve() {
        this.status = SellerStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = null;
        this.rejectReasonDetail = null;
    }

    /**
     * 반려 처리: 브랜드명({@code marketName})과 사업자등록번호 해시만 보존하고 나머지 신청 데이터를 파기합니다.
     */
    public void reject(String rejectReason, String rejectReasonDetail) {
        this.status = SellerStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.rejectReasonDetail = rejectReasonDetail;
        this.processedAt = LocalDateTime.now();
        this.businessRegistrationNumber = BusinessRegistrationNumberHasher.hash(this.businessRegistrationNumber);
        purgePersonalData();
    }

    private void purgePersonalData() {
        this.csNumber = null;
        this.sellerName = null;
        this.sellerContact = null;
        this.businessType = null;
        this.representativeName = null;
        this.representativeContact = null;
        this.companyName = null;
        this.businessCondition = null;
        this.businessAddress = null;
        this.detailAddress = null;
        this.taxEmail = null;
        this.businessLicenseImageUrl = null;
        this.mailOrderRegImageUrl = null;
        this.mailOrderRegNumber = null;
        this.bankName = null;
        this.accountHolder = null;
        this.accountNumber = null;
        this.bankbookImageUrl = null;
        this.agreePrivacyPolicy = null;
        this.agreeTermsOfService = null;
        this.agreeOperationPolicy = null;
    }
}
