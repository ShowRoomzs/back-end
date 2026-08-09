package showroomz.api.common.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompleteAttachmentRequest {

    @Schema(description = "영상 재생시간(초) — FE가 <video>의 loadedmetadata로 읽어 전달하는 표시용 참고값(§4-4). " +
            "정책 판정에 쓰이지 않으므로 없으면 생략 가능",
            example = "58", nullable = true)
    @Positive
    private Integer durationSeconds;
}
