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

@Tag(name = "Common - Notice", description = "공용 공지 API (기획 §20 · C17 공지사항)\n\n")
public interface NoticeControllerDocs {

    @Operation(
            summary = "공지 목록 조회",
            description = "공지 목록을 페이징 조회합니다.\n\n" +
                    "**동작 방식:**\n" +
                    "- 상태가 게시(PUBLISHED)인 공지만 조회됩니다. 게시 종료(ENDED)된 공지는 내려갑니다.\n" +
                    "- 정렬: **중요 고정 상단 + 등록일 최신순** (pinned DESC, createdAt DESC)\n" +
                    "- `pinned`가 true인 공지에는 [중요] 배지를 붙이고 목록 상단에 고정합니다.\n\n" +
                    "**권한:** 비회원/회원 모두 조회 가능 (인증 불필요)\n\n" +
                    "**쿼리 파라미터:**\n" +
                    "- page: 페이지 번호 (1부터 시작, 기본값 1)\n" +
                    "- size: 페이지당 항목 수 (기본값 20)"
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
                                            "      \"title\": \"개인정보 처리방침 개정 안내\",\n" +
                                            "      \"pinned\": true,\n" +
                                            "      \"createdDate\": \"2026-07-05T10:15:00Z\"\n" +
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
                    "- 상태가 게시(PUBLISHED)인 공지만 조회 가능합니다.\n" +
                    "- 게시 종료(ENDED)된 공지에 URL로 직접 접근하면 404로 응답됩니다.\n" +
                    "- `content`는 어드민 리치 에디터에서 작성한 HTML이 그대로 실립니다 (이미지·표 포함).\n\n" +
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
                                            "  \"content\": \"<p>2026년 7월 20일 02:00~04:00 점검 예정입니다.</p>\",\n" +
                                            "  \"pinned\": false,\n" +
                                            "  \"createdDate\": \"2026-07-18T10:00:00Z\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "공지를 찾을 수 없음 (존재하지 않거나 게시 종료됨)",
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
