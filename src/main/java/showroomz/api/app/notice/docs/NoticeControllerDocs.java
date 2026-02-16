package showroomz.api.app.notice.docs;

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
import showroomz.global.dto.PagingRequest;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.notice.dto.NoticeDetailResponse;
import showroomz.api.app.notice.dto.NoticeResponse;
import showroomz.global.dto.PageResponse;

@Tag(name = "Common - Notice", description = "공용 공지 API\n\n")
public interface NoticeControllerDocs {

    @Operation(
            summary = "공지 목록 조회",
            description = "공지 목록을 최신순(createdAt 내림차순)으로 페이징 조회합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 노출 여부(isVisible)가 true인 공지만 조회됩니다.\n" +
                    "- 정렬: 최신 등록순(createdAt DESC)\n\n" +
                    "**권한:** 비회원/회원 모두 조회 가능 (인증 불필요)\n\n" +
                    "**쿼리 파라미터:**\n" +
                    "- page: 페이지 번호 (0부터 시작, 기본값 0)\n" +
                    "- size: 페이지당 항목 수 (기본값 20)\n" +
                    "- sort: 정렬 (기본값 createdAt,desc)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(
                                    name = "공지 목록 응답",
                                    value = "{\n" +
                                            "  \"content\": [\n" +
                                            "    {\n" +
                                            "      \"id\": 1,\n" +
                                            "      \"title\": \"서비스 점검 안내\",\n" +
                                            "      \"createdDate\": \"2025-01-15T10:00:00\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"pageInfo\": {\n" +
                                            "    \"currentPage\": 1,\n" +
                                            "    \"totalPages\": 1,\n" +
                                            "    \"totalResults\": 1,\n" +
                                            "    \"limit\": 20,\n" +
                                            "    \"hasNext\": false\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<PageResponse<NoticeResponse>> getNoticeList(
            PagingRequest pagingRequest
    );

    @Operation(
            summary = "공지 상세 조회",
            description = "공지 ID로 상세 내용을 조회합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 노출 여부(isVisible)가 true인 공지만 조회 가능합니다.\n" +
                    "- 비공개 공지는 404로 응답됩니다.\n\n" +
                    "**권한:** 비회원/회원 모두 조회 가능 (인증 불필요)",
            parameters = {
                    @Parameter(name = "noticeId", description = "공지 ID", required = true, example = "1", in = ParameterIn.PATH)
            }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NoticeDetailResponse.class),
                            examples = @ExampleObject(
                                    name = "공지 상세 응답",
                                    value = "{\n" +
                                            "  \"id\": 1,\n" +
                                            "  \"title\": \"서비스 점검 안내\",\n" +
                                            "  \"content\": \"2025년 1월 20일 02:00~04:00 점검 예정입니다.\",\n" +
                                            "  \"createdDate\": \"2025-01-15T10:00:00\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "공지를 찾을 수 없음 (존재하지 않거나 비공개)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "공지 없음",
                                    value = "{\n" +
                                            "  \"code\": \"NOT_FOUND_DATA\",\n" +
                                            "  \"message\": \"데이터를 찾을 수 없습니다.\"\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<NoticeDetailResponse> getNoticeDetail(
            @Parameter(name = "noticeId", description = "공지 ID", required = true, example = "1", in = ParameterIn.PATH)
            Long noticeId
    );
}
