package showroomz.domain.member.creator.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.market.type.SnsType;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "CREATOR_APPLICATION")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatorApplication extends BaseTimeEntity {

    public static final int REAPPLY_COOLDOWN_DAYS = 14;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CREATOR_APPLICATION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private CreatorApplicationStatus status;

    @Column(name = "PROCESSED_AT")
    private LocalDateTime processedAt;

    @Column(name = "PROCESSOR_EMAIL", length = 512)
    private String processorEmail;

    @Column(name = "REJECT_REASON", length = 500)
    private String rejectReason;

    @Column(name = "REJECT_REASON_TYPE", length = 50)
    private String rejectReasonType;

    @Column(name = "REJECT_REASON_DETAIL", length = 1000)
    private String rejectReasonDetail;

    public static CreatorApplication createApplication(
            Users user,
            SnsType snsType,
            String channelUrl,
            String accountId,
            Integer followerCount,
            String businessEmail) {
        return CreatorApplication.builder()
                .user(user)
                .snsType(snsType)
                .channelUrl(channelUrl)
                .accountId(accountId)
                .followerCount(followerCount)
                .businessEmail(businessEmail)
                .status(CreatorApplicationStatus.PENDING)
                .build();
    }

    public void approve(String processorEmail) {
        this.status = CreatorApplicationStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
        this.processorEmail = processorEmail;
    }

    public void reject(String rejectReasonType, String rejectReasonDetail, String rejectReason, String processorEmail) {
        this.status = CreatorApplicationStatus.REJECTED;
        this.rejectReasonType = rejectReasonType;
        this.rejectReasonDetail = rejectReasonDetail;
        this.rejectReason = rejectReason;
        this.processedAt = LocalDateTime.now();
        this.processorEmail = processorEmail;
    }

    /** 반려 처리일 기준 재신청 가능 일시 (처리일 없으면 신청일 기준) */
    public LocalDateTime resolveReapplyAvailableAt() {
        LocalDateTime rejectedAt = processedAt != null ? processedAt : getCreatedAt();
        return rejectedAt.plusDays(REAPPLY_COOLDOWN_DAYS);
    }
}
