package showroomz.api.creator.profile.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.profile.dto.MyShowroomResponse;
import showroomz.api.creator.profile.dto.ShowroomNameResponse;

@Tag(name = "Creator - Profile", description = "내 쇼룸 정보 조회 API")
@SecurityRequirement(name = "Authorization")
public interface CreatorProfileControllerDocs {

    @Operation(
            summary = "내 쇼룸 정보 조회",
            description = "로그인한 크리에이터 본인의 쇼룸(크리에이터) 전체 정보를 조회합니다.\n\n" +
                    "계좌번호는 뒤 6자리만 노출됩니다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyShowroomResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 예시",
                                    value = "{\n" +
                                            "  \"creatorId\": 5,\n" +
                                            "  \"showroomName\": \"감성 룩북\",\n" +
                                            "  \"snsType\": \"INSTAGRAM\",\n" +
                                            "  \"channelUrl\": \"https://instagram.com/example\",\n" +
                                            "  \"accountId\": \"my_channel\",\n" +
                                            "  \"followerCount\": 12000,\n" +
                                            "  \"businessEmail\": \"creator@example.com\",\n" +
                                            "  \"realName\": \"홍길동\",\n" +
                                            "  \"birthday\": \"1995-01-01\",\n" +
                                            "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                            "  \"businessType\": \"INDIVIDUAL\",\n" +
                                            "  \"businessRegistrationNumber\": null,\n" +
                                            "  \"businessLicenseImageUrl\": null,\n" +
                                            "  \"bankName\": \"국민은행\",\n" +
                                            "  \"maskedAccountNumber\": \"********1234\",\n" +
                                            "  \"bankbookImageUrl\": \"https://s3.../bankbook.jpg\",\n" +
                                            "  \"connectionCode\": \"AB3K7M9X\",\n" +
                                            "  \"createdAt\": \"2026-01-01T10:00:00\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "인증 실패",
                                    value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 크리에이터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "크리에이터 없음",
                                    value = "{\"code\": \"CREATOR_NOT_FOUND\", \"message\": \"존재하지 않는 크리에이터입니다.\"}"
                            )
                    )
            )
    })
    ResponseEntity<MyShowroomResponse> getMyShowroom(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal);

    @Operation(
            summary = "내 쇼룸명 조회",
            description = "로그인한 크리에이터 본인의 쇼룸명만 조회합니다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ShowroomNameResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 예시",
                                    value = "{\n  \"showroomName\": \"감성 룩북\"\n}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "인증 실패",
                                    value = "{\"code\": \"UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 크리에이터",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "크리에이터 없음",
                                    value = "{\"code\": \"CREATOR_NOT_FOUND\", \"message\": \"존재하지 않는 크리에이터입니다.\"}"
                            )
                    )
            )
    })
    ResponseEntity<ShowroomNameResponse> getMyShowroomName(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal);
}
