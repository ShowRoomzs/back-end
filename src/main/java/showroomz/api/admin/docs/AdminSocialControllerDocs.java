package showroomz.api.admin.docs;

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
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.ProviderType;

@Tag(name = "Admin - Social Login Management", description = "관리자 소셜 로그인 활성/비활성 관리 API\n\n" +
        "이 API는 사용자 소셜 로그인 기능의 활성화/비활성화를 관리합니다.\n\n" +
        "**주요 기능:**\n" +
        "- 특정 소셜 로그인 제공자(GOOGLE, NAVER, KAKAO, APPLE 등)의 활성 상태를 변경할 수 있습니다.\n" +
        "- 비활성화된 소셜 로그인은 사용자가 로그인 시도 시 HTTP status: 403, code: `DISABLED_SOCIAL_VENDOR` 에러가 발생하여 차단됩니다.\n" +
        "- DB에 정책 데이터가 없는 경우 기본값으로 활성 상태로 처리됩니다.\n\n" +
        "**권한:** ADMIN\n" +
        "**요청 헤더:** Authorization: Bearer {accessToken}")
public interface AdminSocialControllerDocs {

    @Operation(
            summary = "소셜 로그인 상태 변경",
            description = "특정 소셜 로그인 제공자의 활성화/비활성화 상태를 변경합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "1. `active=true`: 해당 소셜 로그인을 활성화합니다. 사용자가 정상적으로 로그인할 수 있습니다.\n" +
                    "2. `active=false`: 해당 소셜 로그인을 일시 중단합니다. 사용자가 로그인 시도 시 HTTP status: 403, code: `DISABLED_SOCIAL_VENDOR` 에러가 발생하여 차단됩니다.\n\n" +
                    "**제약사항:**\n" +
                    "- DB에 해당 제공자의 정책 데이터가 없는 경우, 새로 생성됩니다.\n" +
                    "- 기존 정책 데이터가 있는 경우, 상태만 업데이트됩니다.\n" +
                    "- 지원되는 ProviderType: GOOGLE, NAVER, KAKAO, APPLE, FACEBOOK, LOCAL\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "소셜 로그인 상태 변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "활성화 성공",
                                            value = "\"GOOGLE 로그인이 활성화 되었습니다.\""
                                    ),
                                    @ExampleObject(
                                            name = "비활성화 성공",
                                            value = "\"NAVER 로그인이 일시 중단 되었습니다.\""
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 파라미터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 ProviderType",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_SOCIAL_PROVIDER\",\n" +
                                                    "  \"message\": \"지원하지 않는 소셜 공급자입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "잘못된 파라미터 형식",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 - 유효하지 않은 토큰 또는 토큰 만료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "권한 없음 - ADMIN 권한이 필요합니다",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"FORBIDDEN\",\n" +
                                                    "  \"message\": \"접근 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<String> updateSocialStatus(
            @Parameter(
                    description = "소셜 로그인 제공자 타입\n\n" +
                            "**지원되는 값:**\n" +
                            "- `GOOGLE`: 구글 로그인\n" +
                            "- `NAVER`: 네이버 로그인\n" +
                            "- `KAKAO`: 카카오 로그인\n" +
                            "- `APPLE`: 애플 로그인\n" +
                            "- `FACEBOOK`: 페이스북 로그인\n" +
                            "- `LOCAL`: 로컬 로그인",
                    required = true,
                    example = "GOOGLE",
                    in = ParameterIn.PATH
            )
            ProviderType providerType,
            @Parameter(
                    description = "활성화 여부\n\n" +
                            "**값 설명:**\n" +
                            "- `true`: 소셜 로그인 활성화 (사용자가 로그인 가능)\n" +
                            "- `false`: 소셜 로그인 일시 중단 (사용자 로그인 차단)",
                    required = true,
                    example = "true",
                    in = ParameterIn.QUERY
            )
            boolean active
    );
}
