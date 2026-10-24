package showroomz.api.creator.auth.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.global.utils.MaskingUtils;

@Getter
@AllArgsConstructor
@Schema(description = "레지스터 토큰 기반 크리에이터 등록 정보 조회 응답")
public class CreatorRegistrationInfoResponse {

    @Schema(description = "SNS 계정 아이디", example = "beauty_soyeon")
    private final String accountId;

    @Schema(description = "본인확인 실명", example = "홍길동")
    private final String realName;

    @Schema(description = "예금주 표시값 — 본인확인 실명 가운데 마스킹(온보딩 「소*연 🔒 본인 인증값」)", example = "홍*동")
    private final String maskedRealName;

    @Schema(description = "쇼룸 주소 핸들 미리보기 — 계정 아이디 기준 자동 생성(`showroomz.com/@{핸들}`). "
            + "선점이 아니므로 제출 사이에 같은 핸들이 쓰이면 등록 시 꼬리표(`_2`)가 붙을 수 있다", example = "beauty_soyeon")
    private final String showroomAddress;

    public static CreatorRegistrationInfoResponse from(Creator creator, String showroomAddress) {
        return new CreatorRegistrationInfoResponse(creator.getAccountId(), creator.getRealName(),
                MaskingUtils.maskNameMiddle(creator.getRealName()), showroomAddress);
    }
}
