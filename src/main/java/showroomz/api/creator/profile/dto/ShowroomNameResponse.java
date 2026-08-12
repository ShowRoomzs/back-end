package showroomz.api.creator.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import showroomz.domain.member.creator.entity.Creator;

@Getter
@Schema(description = "내 쇼룸명 조회 응답")
public class ShowroomNameResponse {

    @Schema(description = "쇼룸명", example = "감성 룩북")
    private final String showroomName;

    public ShowroomNameResponse(String showroomName) {
        this.showroomName = showroomName;
    }

    public static ShowroomNameResponse from(Creator creator) {
        return new ShowroomNameResponse(creator.getShowroomName());
    }
}
