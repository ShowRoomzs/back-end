package showroomz.api.common.policy.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.policy.dto.PolicyValuesResponse;

@Tag(name = "Common - Policy Values", description = "공용 정책 기한 값 API")
public interface CommonPolicyValuesControllerDocs {

    @Operation(
            summary = "정책 기한 값",
            description = "소비자 앱 · 파트너센터 · 스튜디오 · 어드민이 안내 문구(「N영업일 이내」 등)에 쓰는 기한 값을 한 번에 낸다. "
                    + "화면에 숫자를 고정해 두지 않고 이 값을 그린다(58 계획 6-1).\n\n"
                    + "- 값은 서버 설정 그대로다 — 기한 자체(마감 시각)는 각 상세 응답의 `…Deadline` · `…DueAt` 필드를 쓴다\n"
                    + "- 정책값이 정해지지 않은 항목은 null 이다 — 「[정책값 미정]」으로 그린다\n"
                    + "- 추적 정지 소비자 선택: 마지막 추적 이벤트 + `trackingStallAlertDays` 에 정지 판정, 그로부터 + `stallConsumerChoiceDays` 에 선택이 열린다\n\n"
                    + "인증 불요."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = PolicyValuesResponse.class)))
    })
    ResponseEntity<PolicyValuesResponse> getPolicyValues();
}
