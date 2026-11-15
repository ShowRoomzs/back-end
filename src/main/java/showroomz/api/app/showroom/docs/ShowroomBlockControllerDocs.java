package showroomz.api.app.showroom.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.showroom.DTO.BlockedShowroomResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "User - Showroom Block", description = "사용자 쇼룸 차단 API (App Store 심사 1.2)")
public interface ShowroomBlockControllerDocs {

    String BLOCK_EFFECT = "**차단하면:**\n" +
            "- 그 쇼룸의 게시물이 전체 피드(`GET /v1/user/showrooms/posts`) · 팔로잉 피드 · 추천 피드에서 빠진다\n" +
            "- 쇼룸 목록(`GET /v1/user/showrooms`) · 쇼룸 검색 · 활동 중인 쇼룸 · 자동완성에서 빠진다\n" +
            "- 팔로우 중이면 팔로우가 끊긴다(차단을 풀어도 되살리지 않는다)\n" +
            "- 쇼룸 프로필 · 게시물 상세는 그대로 열린다 — 쇼룸 프로필의 `isBlocked` 로 「차단 해제」 버튼을 그린다\n\n" +
            "**권한:** USER/CREATOR (로그인 필수)";

    @Operation(
            summary = "쇼룸 차단",
            description = "쇼룸(크리에이터)을 차단한다. 이미 차단했으면 아무 동작도 하지 않는다(204).\n\n" + BLOCK_EFFECT,
            parameters = @Parameter(name = "showroomId", description = "쇼룸(크리에이터) ID", required = true, example = "1",
                    in = ParameterIn.PATH)
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "차단됨"),
            @ApiResponse(responseCode = "400", description = "본인 쇼룸 — INVALID_INPUT_VALUE",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "쇼룸 없음 — CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> blockShowroom(@Parameter(hidden = true) UserPrincipal userPrincipal,
                                       @PathVariable("showroomId") Long showroomId);

    @Operation(
            summary = "쇼룸 차단 해제",
            description = "차단을 푼다. 차단하지 않았으면 아무 동작도 하지 않는다(204). 차단 때 끊긴 팔로우는 되살리지 않는다.",
            parameters = @Parameter(name = "showroomId", description = "쇼룸(크리에이터) ID", required = true, example = "1",
                    in = ParameterIn.PATH)
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "해제됨"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "쇼룸 없음 — CREATOR_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> unblockShowroom(@Parameter(hidden = true) UserPrincipal userPrincipal,
                                         @PathVariable("showroomId") Long showroomId);

    @Operation(
            summary = "차단한 쇼룸 목록",
            description = "내가 차단한 쇼룸 — 최근 차단순. 설정 「차단 관리」 화면용.\n\n**권한:** USER/CREATOR (로그인 필수)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<BlockedShowroomResponse>> getBlockedShowrooms(
            @Parameter(hidden = true) UserPrincipal userPrincipal,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest);
}
