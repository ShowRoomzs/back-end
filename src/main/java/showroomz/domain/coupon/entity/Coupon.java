package showroomz.domain.coupon.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.coupon.type.*;
import showroomz.domain.member.seller.entity.Seller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupon")
public class Coupon extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "coupon_issue_number", nullable = false, unique = true, length = 50)
    private String couponIssueNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false, length = 20)
    private CouponType couponType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", nullable = false, length = 20)
    private TargetAudience targetAudience;

    @Column(name = "showroom_id")
    private Long showroomId;

    @Column(name = "is_quantity_limited", nullable = false)
    private Boolean isQuantityLimited;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_unit", nullable = false, length = 20)
    private DiscountUnit discountUnit;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    @Column(name = "is_min_order_amount_limited", nullable = false)
    private Boolean isMinOrderAmountLimited;

    @Column(name = "issue_start_date", nullable = false)
    private LocalDateTime issueStartDate;

    @Column(name = "issue_end_date", nullable = false)
    private LocalDateTime issueEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "validity_type", nullable = false, length = 30)
    private ValidityType validityType;

    @Column(name = "valid_start_date")
    private LocalDateTime validStartDate;

    @Column(name = "valid_end_date")
    private LocalDateTime validEndDate;

    @Column(name = "valid_days")
    private Integer validDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @OneToMany(mappedBy = "coupon", fetch = FetchType.LAZY)
    private List<CouponProduct> couponProducts = new ArrayList<>();

    public Coupon(String name, String couponIssueNumber, CouponType couponType, TargetAudience targetAudience,
                  Long showroomId, Boolean isQuantityLimited, DiscountUnit discountUnit, BigDecimal discountValue,
                  BigDecimal minOrderAmount, Integer maxDiscountAmount, Boolean isMinOrderAmountLimited,
                  LocalDateTime issueStartDate, LocalDateTime issueEndDate, ValidityType validityType,
                  LocalDateTime validStartDate, LocalDateTime validEndDate, Integer validDays,
                  CouponStatus status, Integer totalQuantity, Integer remainingQuantity, Seller seller) {
        this.name = name;
        this.couponIssueNumber = couponIssueNumber;
        this.couponType = couponType;
        this.targetAudience = targetAudience;
        this.showroomId = showroomId;
        this.isQuantityLimited = isQuantityLimited;
        this.discountUnit = discountUnit;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.isMinOrderAmountLimited = isMinOrderAmountLimited;
        this.issueStartDate = issueStartDate;
        this.issueEndDate = issueEndDate;
        this.validityType = validityType;
        this.validStartDate = validStartDate;
        this.validEndDate = validEndDate;
        this.validDays = validDays;
        this.status = status;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.seller = seller;
    }

    public void decreaseRemainingForIssuance() {
        if (remainingQuantity == null) {
            return;
        }
        this.remainingQuantity = remainingQuantity - 1;
    }

    public void updateAdminFields(String name, CouponType couponType, TargetAudience targetAudience,
                                  Long showroomId, Boolean isQuantityLimited, DiscountUnit discountUnit,
                                  BigDecimal discountValue, BigDecimal minOrderAmount, Integer maxDiscountAmount,
                                  Boolean isMinOrderAmountLimited, LocalDateTime issueStartDate,
                                  LocalDateTime issueEndDate, ValidityType validityType, LocalDateTime validStartDate,
                                  LocalDateTime validEndDate, Integer validDays, CouponStatus status) {
        this.name = name;
        this.couponType = couponType;
        this.targetAudience = targetAudience;
        this.showroomId = showroomId;
        this.isQuantityLimited = isQuantityLimited;
        this.discountUnit = discountUnit;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.isMinOrderAmountLimited = isMinOrderAmountLimited;
        this.issueStartDate = issueStartDate;
        this.issueEndDate = issueEndDate;
        this.validityType = validityType;
        this.validStartDate = validStartDate;
        this.validEndDate = validEndDate;
        this.validDays = validDays;
        this.status = status;
    }

    public void stop() {
        this.status = CouponStatus.STOPPED;
    }

    // Backward-compatible accessors for legacy coupon usages.
    public String getCode() {
        return couponIssueNumber;
    }

    public LocalDateTime getStartAt() {
        return issueStartDate;
    }

    public LocalDateTime getEndAt() {
        return issueEndDate;
    }

    public DiscountType getDiscountType() {
        return discountUnit == DiscountUnit.PERCENT ? DiscountType.PERCENTAGE : DiscountType.FIXED_AMOUNT;
    }
}
