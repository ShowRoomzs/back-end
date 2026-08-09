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
    private Long requestId;
    private String requestCode;
    private ChangeRequestType type;
    private ChangeRequestStatus status;
    private LocalDateTime requestedAt;

    @Schema(description = "결과 안내 수신 이메일 — 로그인 이메일이며 tax 이메일이 아니다.")
    private String notifyEmail;
}
