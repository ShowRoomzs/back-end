package showroomz.api.creator.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;

import java.time.LocalDateTime;

@Getter
@Schema(description = "내 크리에이터 지원서 응답")
public class MyCreatorApplicationResponse {

    @Schema(description = "지원 신청 일련번호", example = "12")
    private final Long applicationId;

    @Schema(description = "실명 (회원가입 시 본인인증 결과)", example = "홍길동")
    private final String name;

    @Schema(description = "활동 SNS 플랫폼", example = "INSTAGRAM")
    private final SnsType snsType;

    @Schema(description = "활동 채널 주소", example = "https://instagram.com/my_channel")
    private final String channelUrl;

    @Schema(description = "계정 아이디", example = "my_channel")
    private final String accountId;

    @Schema(description = "팔로워 수", example = "10000")
    private final Integer followerCount;

    @Schema(description = "업무 이메일", example = "business@creator.com")
    private final String businessEmail;

    @Schema(description = "신청 일시")
    private final LocalDateTime appliedAt;

    @Schema(description = "승인/반려 처리 일시")
    private final LocalDateTime processedAt;

    @Schema(description = "신청 상태 (PENDING, APPROVED, REJECTED)", example = "REJECTED")
    private final CreatorApplicationStatus status;

    @Schema(description = "반려 사유", example = "허위 팔로워 의심")
    private final String rejectReason;

    @Schema(description = "재신청 가능 일시 (반려 처리일 + 14일)", example = "2026-08-05T15:30:00")
    private final LocalDateTime reapplyAvailableAt;

    @Schema(description = "현재 재신청 가능 여부", example = "false")
    private final boolean canReapply;

    public MyCreatorApplicationResponse(CreatorApplication ca, LocalDateTime reapplyAvailableAt) {
        this.applicationId = ca.getId();
        this.name = ca.getUser().getName();
        this.snsType = ca.getSnsType();
        this.channelUrl = ca.getChannelUrl();
        this.accountId = ca.getAccountId();
        this.followerCount = ca.getFollowerCount();
        this.businessEmail = ca.getBusinessEmail();
        this.appliedAt = ca.getCreatedAt();
        this.processedAt = ca.getProcessedAt();
        this.status = ca.getStatus();
        this.rejectReason = ca.getRejectReason();
        this.reapplyAvailableAt = reapplyAvailableAt;
        this.canReapply = !LocalDateTime.now().isBefore(reapplyAvailableAt);
    }
}
