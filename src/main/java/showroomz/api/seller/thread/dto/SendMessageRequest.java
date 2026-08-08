package showroomz.api.seller.thread.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SendMessageRequest {

    @Schema(description = "FE가 발급한 멱등키(UUID 권장) — 재전송 시 동일 값으로 재요청(§13-10)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 64)
    private String clientMessageId;

    @Schema(description = "메시지 본문 — P3(첨부) 적용 전까지는 필수")
    private String content;
}
