package showroomz.api.creator.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SendMessageRequest {

    @Schema(description = "FE가 발급한 멱등키(UUID 권장) — 재전송 시 동일 값으로 재요청(§13-10)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    @NotBlank
    @Size(max = 64)
    private String clientMessageId;

    @Schema(description = "메시지 본문 — attachmentIds가 있으면 생략 가능(§13-11)",
            example = "네, 확인했습니다", nullable = true)
    private String content;

    @Schema(description = "complete까지 마친 첨부 ID 목록 — 배열 순서가 곧 표시 순서(§4-5)",
            example = "[501, 502]", nullable = true)
    private List<Long> attachmentIds;
}
