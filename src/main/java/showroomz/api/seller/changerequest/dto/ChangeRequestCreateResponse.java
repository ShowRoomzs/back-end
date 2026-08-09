package showroomz.api.seller.changerequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "변경 요청 접수 완료(M3 모달)")
public class ChangeRequestCreateResponse {
    @Schema(example = "12")
    private Long requestId;
    @Schema(example = "CHG-2026-0001")
    private String requestCode;
    @Schema(example = "BUSINESS_INFO")
    private ChangeRequestType type;
    @Schema(example = "PENDING")
    private ChangeRequestStatus status;
    @Schema(example = "2026-08-09T14:22:10")
    private LocalDateTime requestedAt;

    @Schema(description = "결과 안내 받을 이메일 — 로그인 이메일이며 tax 이메일이 아니다.", example = "seller@example.com")
    private String notifyEmail;
}
