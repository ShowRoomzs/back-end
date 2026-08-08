package showroomz.api.creator.connection.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.connection.dto.ConnectionCodeResponse;
import showroomz.api.creator.connection.dto.ConnectionRequestItem;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Connection", description = "쇼룸 스튜디오 연결 요청함 API (§14)")
public interface CreatorConnectionControllerDocs {

    @Operation(
            summary = "받은 연결 요청 목록",
            description = "연결은 항상 브랜드가 발신한다(§13-6·§14-2) — 인플루언서는 수락/거절만 한다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<ConnectionRequestItem>> getRequests(
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "연결 요청 수락",
            description = "카드별로 개별 처리한다(일괄 처리 없음, §14-4). 수락 시 CONNECTED로 전이한다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "수락 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인에게 온 요청이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 연결 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 처리된 요청(REQUESTED 상태 아님)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> accept(
            @Parameter(description = "연결 ID", required = true) @PathVariable("connectionId") Long connectionId
    );

    @Operation(
            summary = "연결 요청 거절",
            description = "카드별로 개별 처리한다(일괄 처리 없음, §14-4). 거절 시 REJECTED로 전이한다.\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "거절 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인에게 온 요청이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 연결 요청",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 처리된 요청(REQUESTED 상태 아님)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> reject(
            @Parameter(description = "연결 ID", required = true) @PathVariable("connectionId") Long connectionId
    );

    @Operation(
            summary = "내 연결코드 조회",
            description = "쇼룸별로 고정(영구) 발급된 연결코드를 조회한다(§13-6). 화면은 아직 미설계(§13-12 #1·§14-8)이나 " +
                    "API는 먼저 열어둔다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConnectionCodeResponse> getMyConnectionCode();

    @Operation(
            summary = "연결코드 재발급",
            description = "인플루언서가 원하면 언제든 재발급할 수 있다(§13-6). 기존 코드는 즉시 무효화된다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConnectionCodeResponse> reissueConnectionCode();
}
