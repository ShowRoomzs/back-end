package showroomz.api.app.auth.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.AuthReqModel;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.SignUpRequest;
import showroomz.api.app.auth.DTO.TokenResponse;

import java.util.Map;

@Tag(name = "Z Test", description = "테스트용 API")
public interface LocalAuthControllerDocs {

    @Operation(
            summary = "로컬 회원가입 (테스트)",
            description = "아이디·비밀번호로 USER 계정을 만듭니다. 소셜 로그인 없이 테스트 계정을 만들 때 씁니다.\n\n" +
                    "아이디 → 이메일 → 닉네임 순으로 검증하고, 닉네임 규칙은 `GET /v1/user/check-nickname`과 같습니다.\n\n" +
                    "**권한:** 없음 (테스트 전용)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"message\": \"회원가입이 완료되었습니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 · 아이디 중복(`DUPLICATE_USERNAME`) · 닉네임 형식 오류 · 부적절한 닉네임",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "아이디 중복",
                                            value = "{\"code\": \"DUPLICATE_USERNAME\", \"message\": \"이미 사용 중인 아이디입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "닉네임 형식 오류",
                                            value = "{\"code\": \"INVALID_INPUT\", \"message\": \"닉네임 형식이 올바르지 않습니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이메일 중복(`DUPLICATE_EMAIL`) · 닉네임 중복(`DUPLICATE_NICKNAME`)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이메일 중복",
                                            value = "{\"code\": \"DUPLICATE_EMAIL\", \"message\": \"이미 다른 계정에서 사용 중인 이메일입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "닉네임 중복",
                                            value = "{\"code\": \"DUPLICATE_NICKNAME\", \"message\": \"이미 사용 중인 닉네임입니다.\"}"
                                    )
                            }
                    )
            )
    })
    Map<String, String> registerUser(@Valid @RequestBody SignUpRequest signUpRequest);

    @Operation(
            summary = "로컬 로그인 (테스트)",
            description = "로컬 회원가입으로 만든 계정의 아이디(`id`)·비밀번호로 토큰을 발급합니다.\n\n" +
                    "로그인 실패(아이디·비밀번호 불일치 포함)는 전역 예외 처리기에서 **500**으로 응답합니다.\n\n" +
                    "**권한:** 없음 (테스트 전용)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "로그인 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "로그인 실패",
                                    value = "{\"code\": \"INTERNAL_SERVER_ERROR\", \"message\": \"일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.\"}"
                            )
                    )
            )
    })
    TokenResponse login(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response,
            @RequestBody AuthReqModel authReqModel
    );
}
