package showroomz.api.app.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import showroomz.api.app.address.dto.DeliveryAddressDto;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.DTO.ValidationErrorResponse;

import java.util.List;

@Tag(name = "Delivery Address", description = "배송지 관리 API")
public interface DeliveryAddressControllerDocs {

    @Operation(
            summary = "배송지 목록 조회",
            description = "현재 로그인한 사용자의 등록된 배송지 목록을 조회합니다.\n\n" +
                    "**정렬 규칙:**\n" +
                    "- 기본 배송지(`isDefault: true`)가 가장 먼저 표시됩니다.\n" +
                    "- 그 다음 최근 수정일 기준 내림차순으로 정렬됩니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 - Status: 200 OK",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DeliveryAddressDto.Response.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 시 (배송지 있음)",
                                            value = "[\n" +
                                                    "  {\n" +
                                                    "    \"id\": 1,\n" +
                                                    "    \"recipientName\": \"홍길동\",\n" +
                                                    "    \"zipCode\": \"12345\",\n" +
                                                    "    \"address\": \"서울특별시 강남구 테헤란로 123\",\n" +
                                                    "    \"detailAddress\": \"101동 101호\",\n" +
                                                    "    \"phoneNumber\": \"010-1234-5678\",\n" +
                                                    "    \"isDefault\": true\n" +
                                                    "  },\n" +
                                                    "  {\n" +
                                                    "    \"id\": 2,\n" +
                                                    "    \"recipientName\": \"홍길동\",\n" +
                                                    "    \"zipCode\": \"54321\",\n" +
                                                    "    \"address\": \"부산광역시 해운대구 해운대해변로 456\",\n" +
                                                    "    \"detailAddress\": \"202동 202호\",\n" +
                                                    "    \"phoneNumber\": \"010-9876-5432\",\n" +
                                                    "    \"isDefault\": false\n" +
                                                    "  }\n" +
                                                    "]"
                                    ),
                                    @ExampleObject(
                                            name = "성공 시 (배송지 없음)",
                                            value = "[]"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
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
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<List<DeliveryAddressDto.Response>> getAddressList();

    @Operation(
            summary = "배송지 추가",
            description = "새로운 배송지를 등록합니다.\n\n" +
                    "**배송지 등록 규칙:**\n" +
                    "- 최대 10개까지 등록 가능합니다.\n" +
                    "- 첫 번째 배송지는 자동으로 기본 배송지로 설정됩니다.\n" +
                    "- `isDefault: true`로 설정하면 기존 기본 배송지는 자동으로 해제됩니다.\n\n" +
                    "**입력값 검증:**\n" +
                    "- `recipientName`: 필수, 최대 64자\n" +
                    "- `zipCode`: 필수, 최대 10자\n" +
                    "- `address`: 필수, 최대 255자\n" +
                    "- `detailAddress`: 필수, 최대 255자\n" +
                    "- `phoneNumber`: 필수, 형식: `010-1234-5678` (하이픈 포함)\n" +
                    "- `isDefault`: 선택, 기본값: `false`\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "등록 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효성 검증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"recipientName\",\n" +
                                                    "      \"message\": \"수령인 이름은 필수입니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"phoneNumber\",\n" +
                                                    "      \"message\": \"전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "최대 개수 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"MAX_ADDRESS_LIMIT_EXCEEDED\",\n" +
                                                    "  \"message\": \"배송지는 최대 10개까지만 등록 가능합니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
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
                    description = "사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
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
            description = "배송지 등록 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = DeliveryAddressDto.Request.class),
                    examples = {
                            @ExampleObject(
                                    name = "기본 배송지로 등록",
                                    value = "{\n" +
                                            "  \"recipientName\": \"홍길동\",\n" +
                                            "  \"zipCode\": \"12345\",\n" +
                                            "  \"address\": \"서울특별시 강남구 테헤란로 123\",\n" +
                                            "  \"detailAddress\": \"101동 101호\",\n" +
                                            "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                            "  \"isDefault\": true\n" +
                                            "}",
                                    description = "기본 배송지로 등록하는 경우"
                            ),
                            @ExampleObject(
                                    name = "일반 배송지로 등록",
                                    value = "{\n" +
                                            "  \"recipientName\": \"홍길동\",\n" +
                                            "  \"zipCode\": \"54321\",\n" +
                                            "  \"address\": \"부산광역시 해운대구 해운대해변로 456\",\n" +
                                            "  \"detailAddress\": \"202동 202호\",\n" +
                                            "  \"phoneNumber\": \"010-9876-5432\",\n" +
                                            "  \"isDefault\": false\n" +
                                            "}",
                                    description = "일반 배송지로 등록하는 경우"
                            )
                    }
            )
    )
    ResponseEntity<Void> addAddress(@RequestBody DeliveryAddressDto.Request request);

    @Operation(
            summary = "배송지 삭제",
            description = "등록된 배송지를 삭제합니다.\n\n" +
                    "**삭제 규칙:**\n" +
                    "- 본인이 등록한 배송지만 삭제할 수 있습니다.\n" +
                    "- 기본 배송지는 다른 배송지가 있을 경우 삭제할 수 없습니다.\n" +
                    "- 배송지가 1개만 남은 경우, 기본 배송지라도 삭제 가능합니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "기본 배송지 삭제 불가 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "기본 배송지 삭제 불가",
                                            value = "{\n" +
                                                    "  \"code\": \"DEFAULT_ADDRESS_DELETE_NOT_ALLOWED\",\n" +
                                                    "  \"message\": \"기본 배송지는 삭제할 수 없습니다. 다른 배송지를 기본으로 지정 후 삭제해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ADDRESS_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 배송지에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송지 또는 사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "배송지 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ADDRESS_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 배송지입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> deleteAddress(
            @Parameter(description = "삭제할 배송지 ID", required = true, example = "1")
            @PathVariable Long addressId
    );

    @Operation(
            summary = "배송지 수정",
            description = "등록된 배송지 정보를 수정합니다.\n\n" +
                    "**수정 규칙:**\n" +
                    "- 본인이 등록한 배송지만 수정할 수 있습니다.\n" +
                    "- `isDefault: true`로 변경하면 기존 기본 배송지는 자동으로 해제됩니다.\n\n" +
                    "**입력값 검증:**\n" +
                    "- `recipientName`: 필수, 최대 64자\n" +
                    "- `zipCode`: 필수, 최대 10자\n" +
                    "- `address`: 필수, 최대 255자\n" +
                    "- `detailAddress`: 필수, 최대 255자\n" +
                    "- `phoneNumber`: 필수, 형식: `010-1234-5678` (하이픈 포함)\n" +
                    "- `isDefault`: 선택, 기본값: `false`\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 - Status: 400 Bad Request",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ValidationErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효성 검증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"INVALID_INPUT\",\n" +
                                                    "  \"message\": \"입력값이 올바르지 않습니다.\",\n" +
                                                    "  \"errors\": [\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"recipientName\",\n" +
                                                    "      \"message\": \"수령인 이름은 필수입니다.\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"field\": \"phoneNumber\",\n" +
                                                    "      \"message\": \"전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ADDRESS_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 배송지에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송지 또는 사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "배송지 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ADDRESS_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 배송지입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "사용자 없음",
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
            description = "배송지 수정 요청",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = DeliveryAddressDto.Request.class),
                    examples = {
                            @ExampleObject(
                                    name = "배송지 정보 수정",
                                    value = "{\n" +
                                            "  \"recipientName\": \"홍길동\",\n" +
                                            "  \"zipCode\": \"12345\",\n" +
                                            "  \"address\": \"서울특별시 강남구 테헤란로 123\",\n" +
                                            "  \"detailAddress\": \"101동 101호\",\n" +
                                            "  \"phoneNumber\": \"010-1234-5678\",\n" +
                                            "  \"isDefault\": true\n" +
                                            "}",
                                    description = "배송지 정보를 수정하고 기본 배송지로 설정"
                            )
                    }
            )
    )
    ResponseEntity<Void> updateAddress(
            @Parameter(description = "수정할 배송지 ID", required = true, example = "1")
            @PathVariable Long addressId,
            @RequestBody DeliveryAddressDto.Request request
    );

    @Operation(
            summary = "기본 배송지로 지정",
            description = "등록된 배송지를 기본 배송지로 설정합니다.\n\n" +
                    "**기본 배송지 설정 규칙:**\n" +
                    "- 본인이 등록한 배송지만 기본 배송지로 설정할 수 있습니다.\n" +
                    "- 기본 배송지로 설정하면 기존 기본 배송지는 자동으로 해제됩니다.\n" +
                    "- 이미 기본 배송지인 경우 변경 사항이 없습니다.\n\n" +
                    "**권한:** USER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "설정 성공 - Status: 200 OK (응답 본문 없음)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 정보가 유효하지 않음 - Status: 401 Unauthorized",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음 - Status: 403 Forbidden",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ADDRESS_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 배송지에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "배송지 또는 사용자를 찾을 수 없음 - Status: 404 Not Found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "배송지 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ADDRESS_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 배송지입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"USER_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 회원입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> setDefaultAddress(
            @Parameter(description = "기본 배송지로 설정할 배송지 ID", required = true, example = "1")
            @PathVariable Long addressId
    );
}
