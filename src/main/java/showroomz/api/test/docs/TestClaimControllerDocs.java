package showroomz.api.test.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.test.dto.TestClaimDto;

@Tag(name = "Z Test", description = "테스트용 API")
public interface TestClaimControllerDocs {

    @Operation(
            summary = "클레임 재발송 도착 처리 (테스트)",
            description = "**재발송 중(RESHIPPING)** 클레임을 재발송 도착으로 종결합니다. 테스트 송장은 택배 조회에 이벤트가 없어 " +
                    "재발송이 끝나지 않으므로 이 API로 대신합니다.\n\n" +
                    "재발송 송장 추적이 도착을 잡았을 때와 같은 전이입니다 — 반려 건이면 `REJECTED`(반송 완료), 교환이면 `EXCHANGED`" +
                    "(교환 완료 · 구매확정 7일이 지금부터 다시 시작)로 끝나고 이력 행위자는 택배 추적입니다.\n\n" +
                    "**권한:** 없음 (테스트 전용)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "재발송 도착 처리 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TestClaimDto.ReshipDeliveredResponse.class),
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"claimId\": 3021,\n" +
                                            "  \"claimNumber\": \"CLM-3021\",\n" +
                                            "  \"status\": \"COMPLETED\",\n" +
                                            "  \"result\": \"EXCHANGED\",\n" +
                                            "  \"deliveredAt\": \"2026-10-29T05:30:00Z\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "클레임 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "미존재",
                                    value = "{\"code\": \"CLAIM_NOT_FOUND\", \"message\": \"반품·교환 요청을 찾을 수 없습니다.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "재발송 중이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "재발송 중 아님",
                                    value = "{\"code\": \"CLAIM_STATE_CHANGED\", " +
                                            "\"message\": \"재발송 중(RESHIPPING)인 클레임만 처리합니다. 현재: CLM-3021 RESHIP_READY\"}"
                            )
                    )
            )
    })
    ResponseEntity<TestClaimDto.ReshipDeliveredResponse> markReshipDelivered(
            @Parameter(
                    name = "claimId",
                    description = "클레임 id",
                    required = true,
                    example = "3021",
                    in = ParameterIn.PATH
            )
            @PathVariable("claimId") Long claimId
    );
}
