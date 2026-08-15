package showroomz.api.creator.showroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * §22-1 쇼룸 프로필 수정 요청.
 *
 * <p>쇼룸 주소는 없다 — 자동 생성 후 수정 불가다. 프로필 이미지는 업로드 API가 돌려준 URL을 넣고,
 * <b>삭제는 빈 값</b>으로 보낸다(null은 "안 바꿈"이 아니라 삭제와 구분되지 않으므로 아래 규칙을 따른다).
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "쇼룸 프로필(공개 정보) 수정 요청 — 화면의 값 전체를 그대로 보낸다")
public class ShowroomProfileUpdateRequest {

    @Schema(description = "쇼룸명 — 2~20자, 한글·영문·숫자·공백만, 중복 불가",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "뷰티_소연")
    @NotBlank
    private String showroomName;

    @Schema(description = "쇼룸 프로필 이미지 URL — 삭제하려면 빈 문자열, 유지하려면 현재 값을 그대로 보낸다",
            example = "https://cdn.showroomz.co.kr/showroom/profile/abc.jpg", nullable = true)
    private String profileImageUrl;

    @Schema(description = "쇼룸 소개글 — 최대 50자", example = "뷰티 소품을 좋아하는 소연입니다 🌸", nullable = true)
    @Size(max = 50)
    private String introduction;

    @Schema(description = "인스타그램 URL — https:// 형식", example = "https://www.instagram.com/beauty_soyeon",
            nullable = true)
    @Size(max = 512)
    private String instagramUrl;
}
