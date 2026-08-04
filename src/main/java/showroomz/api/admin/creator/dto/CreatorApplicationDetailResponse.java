package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "크리에이터 입점 관리 상세 응답")
public class CreatorApplicationDetailResponse {

    /** 본인인증 연동 전까지 사용하는 더미 값 */
    public static final String DUMMY_REAL_NAME = "홍길동";
    public static final String DUMMY_BIRTHDAY = "1990-01-01";
    public static final String DUMMY_PHONE_NUMBER = "010-1234-5678";
    public static final String VERIFICATION_METHOD_PASS = "PASS";
    public static final String VERIFICATION_METHOD_PASS_LABEL = "휴대폰 본인인증(PASS)";

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "처리 이력 항목")
    public static class ProcessingHistoryItem {

        @Schema(description = "처리 유형", example = "APPLICATION_APPROVED",
                allowableValues = {"APPLICATION_RECEIVED", "APPLICATION_APPROVED", "APPLICATION_REJECTED"})
        private String type;

        @Schema(description = "처리 내용", example = "승인 처리")
        private String label;

        @Schema(description = "처리 일시", example = "2026-07-09T11:20:00")
        private LocalDateTime processedAt;

        @Schema(description = "처리 운영자 이메일 (승인/반려 시에만)", example = "admin@showroomz.com")
        private String processorEmail;
    }

    // ── 심사 상태 ──

    @Schema(description = "신청번호 (지원서 PK)", example = "12")
    private Long applicationId;

    @Schema(description = "심사 상태 (PENDING, APPROVED, REJECTED)", example = "PENDING")
    private CreatorApplicationStatus status;

    @Schema(description = "신청일", example = "2026-07-13T18:04:00")
    private LocalDateTime appliedAt;

    @Schema(description = "승인/반려 처리 일시", example = "2026-07-15T10:00:00")
    private LocalDateTime processedAt;

    @Schema(description = "처리 운영자 이메일 (승인/반려 시에만)", example = "admin@showroomz.com")
    private String processorEmail;

    @Schema(description = "반려 사유 유형 (반려 시에만)", example = "FOLLOWER_COUNT_SHORTFALL",
            allowableValues = {"CHANNEL_INFO_MISMATCH", "FOLLOWER_COUNT_SHORTFALL", "OTHER"})
    private String rejectReasonType;

    @Schema(description = "반려 상세 사유 (반려 시에만, 선택)", example = "제출하신 채널의 팔로워 수가 기준에 미달합니다.")
    private String rejectReasonDetail;

    // ── 본인 인증 (더미) ──

    @Schema(description = "실명 (본인인증 연동 전 더미)", example = "홍길동")
    private String name;

    @Schema(description = "생년월일 (본인인증 연동 전 더미, YYYY-MM-DD)", example = "1990-01-01")
    private String birthday;

    @Schema(description = "연락처 (본인인증 연동 전 더미)", example = "010-1234-5678")
    private String phoneNumber;

    @Schema(description = "인증 수단 코드 (현재 PASS만 예정)", example = "PASS")
    private String verificationMethod;

    @Schema(description = "인증 수단 표시명", example = "휴대폰 본인인증(PASS)")
    private String verificationMethodLabel;

    // ── 플랫폼 ──

    @Schema(description = "활동 SNS 플랫폼", example = "INSTAGRAM")
    private SnsType snsType;

    @Schema(description = "채널 주소", example = "https://instagram.com/my_channel")
    private String channelUrl;

    @Schema(description = "계정 아이디", example = "my_channel")
    private String accountId;

    @Schema(description = "팔로워 수", example = "10000")
    private Integer followerCount;

    @Schema(description = "업무용 이메일", example = "business@creator.com")
    private String businessEmail;

    @Schema(description = "마케팅 정보 수신 동의", example = "true")
    private Boolean marketingAgree;

    // ── 처리 이력 ──

    @Schema(description = "처리 이력 (신청 접수, 승인/반려 등 시간순)")
    private List<ProcessingHistoryItem> processingHistory;
}
