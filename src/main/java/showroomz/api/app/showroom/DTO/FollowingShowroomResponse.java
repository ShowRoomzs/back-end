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
@Schema(description = "팔로우한 쇼룸 항목")
public class FollowingShowroomResponse {

    @Schema(description = "쇼룸(크리에이터) ID", example = "1")
    private Long showroomId;

    @Schema(description = "쇼룸명", example = "제니의 뷰티룸")
    private String showroomName;

    @Schema(description = "쇼룸 프로필 이미지 URL", example = "https://example.com/showroom1.jpg")
    private String showroomImageUrl;

    @Schema(description = "진행 중인 공구 보유 여부 (아바타 링 표시용)", example = "true")
    private Boolean hasOngoingGroupBuy;

    @Schema(description = "팔로우한 시각", example = "2026-08-01T10:20:30")
    private LocalDateTime followedAt;
}
