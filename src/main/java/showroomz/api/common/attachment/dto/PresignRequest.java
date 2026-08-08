package showroomz.api.common.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PresignRequest {

    @Schema(description = "원본 파일명(확장자 포함) — 확장자 화이트리스트 판정에 사용(§2-1)", example = "촬영본.mp4")
    @NotBlank
    private String fileName;

    @Schema(description = "파일 Content-Type", example = "video/mp4")
    @NotBlank
    private String contentType;

    @Schema(description = "파일 크기(byte) — 서버 재검증의 1차 기준값, 최종 확정은 HeadObject 실측값(§4-1 ③)", example = "31457280")
    @NotNull
    @Positive
    private Long sizeBytes;
}
