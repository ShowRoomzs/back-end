package showroomz.api.seller.changerequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * §A-5 — 1-E·1-F·1-G·2-D·2-E·2-F 배너 공용 스키마. {@code status}가 APPROVED/REJECTED인데
 * 브랜드가 이미 확인(acknowledge)한 건은 이 객체 자체를 내리지 않고 {@code null}로 응답한다.
 */
@Getter
@Builder
@Schema(description = "변경 요청 배너")
public class ChangeRequestBannerResponse {

    @Schema(example = "12")
    private Long requestId;
    @Schema(example = "CHG-2026-0001")
    private String requestCode;
    @Schema(example = "BUSINESS_INFO")
    private ChangeRequestType type;
    @Schema(example = "PENDING")
    private ChangeRequestStatus status;

    @Schema(description = "요청한 변경 항목 라벨 목록", example = "[\"대표자명\", \"사업장 주소\"]")
    private List<String> changedFieldLabels;

    @Schema(example = "2026-08-09T14:22:10")
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    @Schema(description = "PENDING일 때만 true — [요청 취소] 버튼 노출 조건", example = "true")
    private boolean cancelable;

    @Schema(description = "정형 반려 사유 완성 문구. REJECTED가 아니면 null", example = "변경 사유 불충분")
    private String rejectReason;

    @Schema(description = "상세 반려 사유. 미입력이면 null")
    private String rejectReasonDetail;

    @Schema(description = "SETTLEMENT_ACCOUNT 요청일 때만 채워지는, 요청한 새 계좌(마스킹)")
    private RequestedAccount requestedAccount;

    @Getter
    @Builder
    @Schema(description = "요청한 새 정산 계좌(마스킹)")
    public static class RequestedAccount {
        @Schema(example = "신한은행")
        private String bankName;
        @Schema(example = "110***456789")
        private String maskedAccountNumber;
    }
}
