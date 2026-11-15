package showroomz.api.app.showroom.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "차단한 쇼룸 항목")
public class BlockedShowroomResponse {

    @Schema(description = "쇼룸(크리에이터) ID", example = "1")
    private Long showroomId;

    @Schema(description = "쇼룸명 — 등록 전이면 계정 닉네임", example = "제니의 뷰티룸")
    private String showroomName;

    @Schema(description = "쇼룸 프로필 이미지 URL", example = "https://media.showroomz.co.kr/uploads/showroom1.jpg", nullable = true)
    private String showroomImageUrl;

    @Schema(description = "차단한 시각", example = "2026-11-15T01:20:30Z")
    private LocalDateTime blockedAt;
}
