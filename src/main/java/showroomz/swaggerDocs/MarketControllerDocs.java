package showroomz.swaggerDocs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import showroomz.Market.DTO.MarketDto;
import showroomz.auth.DTO.ErrorResponse;
import showroomz.auth.DTO.ValidationErrorResponse;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Market", description = "마켓 API")
public interface MarketControllerDocs {

    @Operation(
            summary = "마켓명 중복 확인",
            description = "마켓명의 중복 여부를 확인합니다.\n\n" +
                    "**응답:**\n" +
                    "- `isAvailable`: true면 사용 가능, false면 중복\n" +
                    "- `code`: 응답 코드 (AVAILABLE: 사용 가능, DUPLICATE: 중복)\n" +
                    "- `message`: 결과 메시지"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "중복 체크 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketDto.CheckMarketNameResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용 가능한 경우",
                                            value = "{\n" +
                                                    "  \"isAvailable\": true,\n" +
                                                    "  \"code\": \"AVAILABLE\",\n" +
                                                    "  \"message\": \"사용 가능한 마켓명입니다.\"\n" +
                                                    "}",
                                            description = "마켓명을 사용할 수 있습니다."
                                    ),
                                    @ExampleObject(
                                            name = "중복인 경우",
                                            value = "{\n" +
                                                    "  \"isAvailable\": false,\n" +
                                                    "  \"code\": \"DUPLICATE\",\n" +
                                                    "  \"message\": \"이미 사용 중인 마켓명입니다.\"\n" +
                                                    "}",
                                            description = "마켓명이 이미 사용 중입니다."
                                    )
                            }
                    )
            )
    })
    ResponseEntity<MarketDto.CheckMarketNameResponse> checkMarketName(
            @Parameter(
                    description = "중복 체크할 마켓명",
                    required = true,
                    example = "쇼룸즈"
            )
            @RequestParam("marketName") String marketName
    );

    @Operation(
            summary = "내 마켓 정보 조회",
            description = "현재 로그인한 관리자의 마켓 정보를 조회합니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "마켓 정보 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MarketDto.MarketProfileResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"marketId\": 1,\n" +
                                                    "  \"marketName\": \"쇼룸즈\",\n" +
                                                    "  \"csNumber\": \"02-1234-5678\",\n" +
                                                    "  \"marketImageUrl\": \"https://example.com/image.jpg\",\n" +
                                                    "  \"marketImageStatus\": \"APPROVED\",\n" +
                                                    "  \"marketDescription\": \"최고의 쇼핑몰\",\n" +
                                                    "  \"marketUrl\": \"https://showroomz.shop/market/1\",\n" +
                                                    "  \"mainCategory\": \"패션\",\n" +
                                                    "  \"snsLinks\": [\n" +
                                                    "    {\n" +
                                                    "      \"snsType\": \"INSTAGRAM\",\n" +
                                                    "      \"snsUrl\": \"https://instagram.com/showroomz\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"snsType\": \"YOUTUBE\",\n" +
                                                    "      \"snsUrl\": \"https://youtube.com/showroomz\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓 정보를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<MarketDto.MarketProfileResponse> getMyMarket();

    @Operation(
            summary = "마켓 프로필 수정",
            description = "현재 로그인한 관리자의 마켓 프로필을 수정합니다.\n\n" +
                    "**수정 가능한 항목:**\n" +
                    "- marketName: 마켓명 (한글만 허용, 공백 불가)\n" +
                    "- marketDescription: 마켓 소개 (최대 30자, 줄바꿈 불가)\n" +
                    "- marketImageUrl: 마켓 대표 이미지 URL\n" +
                    "- mainCategory: 대표 카테고리\n" +
                    "- snsLinks: SNS 링크 (최대 3개)\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "마켓 프로필 수정 성공",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "입력값 오류 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"marketName\",\n" +
                                                    "      \"reason\": \"마켓명은 한글만 입력 가능하며 공백을 포함할 수 없습니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"marketDescription\",\n" +
                                                    "      \"reason\": \"마켓 소개는 최대 30자까지 입력 가능합니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"snsLinks[0].snsUrl\",\n" +
                                                    "      \"reason\": \"올바른 URL 형식이 아닙니다.\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "마켓명 중복",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓명 중복 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_MARKET_NAME\",\n" +
                                                    "  \"message\": \"이미 사용 중인 마켓명입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "줄바꿈 포함 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "줄바꿈 오류 예시",
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
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "마켓 정보를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "마켓 없음 예시",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "마켓 프로필 수정 정보\n\n" +
                    "**필수 항목:** 없음 (수정할 항목만 전달)\n\n" +
                    "**제약사항:**\n" +
                    "- marketName: 한글만 허용, 공백 불가\n" +
                    "- marketDescription: 최대 30자, 줄바꿈 불가\n" +
                    "- snsLinks: 최대 3개, 각 URL은 http:// 또는 https://로 시작해야 함",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MarketDto.UpdateMarketProfileRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "요청 예시",
                                    value = "{\n" +
                                            "  \"marketName\": \"쇼룸즈\",\n" +
                                            "  \"marketDescription\": \"최고의 쇼핑몰\",\n" +
                                            "  \"marketImageUrl\": \"https://example.com/image.jpg\",\n" +
                                            "  \"mainCategory\": \"패션\",\n" +
                                            "  \"snsLinks\": [\n" +
                                            "    {\n" +
                                            "      \"snsType\": \"INSTAGRAM\",\n" +
                                            "      \"snsUrl\": \"https://instagram.com/showroomz\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"snsType\": \"YOUTUBE\",\n" +
                                            "      \"snsUrl\": \"https://youtube.com/showroomz\"\n" +
                                            "    }\n" +
                                            "  ]\n" +
                                            "}"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateMarketProfile(
            @RequestBody MarketDto.UpdateMarketProfileRequest request
    );
}

