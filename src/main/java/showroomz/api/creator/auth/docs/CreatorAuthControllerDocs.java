package showroomz.api.creator.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.auth.DTO.CreatorSignUpRequest;

import java.util.Map;

@Tag(name = "Creator - Auth", description = "Creator Auth API")
public interface CreatorAuthControllerDocs {

    @Operation(
            summary = "크리에이터 회원가입 요청",
            description = "크리에이터 전용 회원가입 API입니다.\n\n" +
                    "**특징:**\n" +
                    "- 활동명(activityName)을 함께 저장합니다.\n" +
                    "- 마켓 타입은 SHOWROOM으로 저장됩니다.\n" +
                    "- SNS 플랫폼 정보와 URL을 함께 등록합니다.\n" +
                    "  - `snsType`: SNS 타입 (INSTAGRAM, TIKTOK, X, YOUTUBE)\n\n" +
                    "**승인 플로우:**\n" +
                    "- 판매자와 동일하게 승인 대기(PENDING) 상태로 생성되며, 관리자 승인 후 로그인 가능합니다.\n\n" +
                    "**권한:** 없음 (회원가입은 인증 불필요)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "크리에이터 회원가입 신청 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신규 크리에이터 가입 성공 예시",
                                            value = "{\n" +
                                                    "  \"message\": \"쇼룸 개설 신청이 완료되었습니다. 관리자 승인 후 로그인이 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 중복 데이터",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "크리에이터 회원가입 정보\n\n" +
                    "**SNS 정보:**\n" +
                    "- snsType: SNS 타입 (INSTAGRAM, TIKTOK, X, YOUTUBE)\n" +
                    "- snsUrl: SNS 프로필/채널 URL",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreatorSignUpRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"email\": \"creator@example.com\",\n" +
                                            "  \"password\": \"Creator123!\",\n" +
                                            "  \"passwordConfirm\": \"Creator123!\",\n" +
                                            "  \"sellerName\": \"김창작\",\n" +
                                            "  \"sellerContact\": \"010-1234-5678\",\n" +
                                            "  \"marketName\": \"myshowroom\",\n" +
                                            "  \"activityName\": \"뷰티크리에이터\",\n" +
                                            "  \"snsType\": \"INSTAGRAM\",\n" +
                                            "  \"snsUrl\": \"https://instagram.com/my_id\"\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<?> signUp(@RequestBody CreatorSignUpRequest request);
}
