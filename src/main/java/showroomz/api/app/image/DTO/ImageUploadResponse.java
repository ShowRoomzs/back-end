package showroomz.api.app.image.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "이미지 업로드 응답")
public class ImageUploadResponse {

    @Schema(description = "업로드된 이미지 URL")
    private String imageUrl;

    /**
     * 가로·세로 픽셀 — 게시물 사진에만 값이 있다.
     *
     * <p>§24-2의 비율 검증을 클라이언트와 서버가 <b>같은 값</b>으로 해야 하기 때문에 내려준다.
     * 클라이언트가 잰 크기와 서버가 읽은 크기가 다르면 게시 단계에서 알 수 없는 이유로 거절된다.
     */
    @Schema(description = "가로 픽셀 — 이미지로 판독된 경우에만", example = "1080", nullable = true)
    private Integer width;

    @Schema(description = "세로 픽셀", example = "1350", nullable = true)
    private Integer height;

    public ImageUploadResponse(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
