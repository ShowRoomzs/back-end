package showroomz.api.admin.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import showroomz.api.admin.creator.type.CreatorRejectionReasonType;

@Getter
@Setter
@Schema(description = "크리에이터 심사 반려 처리 요청")
public class CreatorApplicationRejectRequest {

    @NotNull(message = "반려 사유(유형) 선택은 필수입니다.")
    @Schema(description = "반려 사유 유형", example = "CHANNEL_INFO_MISMATCH")
    private CreatorRejectionReasonType rejectReasonType;

    @Schema(description = "기타/상세 반려 사유 (선택사항)", example = "제출하신 인스타그램 계정이 비공개 상태입니다.")
    private String rejectReasonDetail;
}
