package showroomz.api.creator.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.member.creator.entity.Creator;

@Getter
@AllArgsConstructor
@Schema(description = "레지스터 토큰 기반 크리에이터 등록 정보 조회 응답")
public class CreatorRegistrationInfoResponse {

    @Schema(description = "SNS 계정 아이디", example = "my_channel")
    private final String accountId;

    @Schema(description = "본인확인 실명", example = "홍길동")
    private final String realName;

    public static CreatorRegistrationInfoResponse from(Creator creator) {
        return new CreatorRegistrationInfoResponse(creator.getAccountId(), creator.getRealName());
    }
}
