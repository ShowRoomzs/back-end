package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.market.type.SnsType;

import java.time.LocalDateTime;

@Getter
@Schema(description = "크리에이터 지원 목록 상세 응답")
public class CreatorApplicationResponse {

    @Schema(description = "지원 신청 일련번호", example = "12")
    private final Long applicationId;

    @Schema(description = "유저 닉네임", example = "뷰티마스터")
    private final String nickname;

    @Schema(description = "업무 이메일(지원서 제출 이메일)", example = "business@creator.com")
    private final String email;

    @Schema(description = "활동 SNS 플랫폼", example = "YOUTUBE")
    private final SnsType snsType;

    @Schema(description = "활동 채널 주소", example = "https://youtube.com/c/example")
    private final String channelUrl;

    @Schema(description = "계정 아이디", example = "my_channel")
    private final String accountId;

    @Schema(description = "팔로워 수", example = "155000")
    private final Integer followerCount;

    @Schema(description = "신청 일시")
    private final LocalDateTime appliedAt;

    @Schema(description = "승인/반려 처리 일시")
    private final LocalDateTime processedAt;

    @Schema(description = "신청 상태 (PENDING, APPROVED, REJECTED)")
    private final CreatorApplicationStatus status;

    @Schema(description = "반려 사유")
    private final String rejectReason;

    public CreatorApplicationResponse(CreatorApplication ca) {
        this.applicationId = ca.getId();
        this.nickname = ca.getUser().getNickname();
        this.email = ca.getBusinessEmail();
        this.snsType = ca.getSnsType();
        this.channelUrl = ca.getChannelUrl();
        this.accountId = ca.getAccountId();
        this.followerCount = ca.getFollowerCount();
        this.appliedAt = ca.getCreatedAt();
        this.processedAt = ca.getProcessedAt();
        this.status = ca.getStatus();
        this.rejectReason = ca.getRejectReason();
    }
}
