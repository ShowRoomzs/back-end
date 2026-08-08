package showroomz.api.creator.thread.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.creator.thread.dto.MessageItem;
import showroomz.api.creator.thread.dto.MessageListResponse;
import showroomz.api.creator.thread.dto.SendMessageRequest;
import showroomz.api.creator.thread.dto.ThreadListItem;
import showroomz.api.creator.thread.dto.ThreadSummaryResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Thread", description = "쇼룸 스튜디오 연결·소통 스레드/메시지 API (§14)")
public interface CreatorThreadControllerDocs {

    @Operation(
            summary = "`연결됨` 탭 — 스레드 목록",
            description = "운영자 채널이 항상 최상단 고정되고, 그 아래 연결됨(OPEN) 상태의 브랜드만 최근 메시지순으로 노출된다.\n\n" +
                    "휴면(DORMANT) 스레드는 목록에서 완전히 제외된다(§13-12 #2 해소).\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<ThreadListItem>> getThreads(@ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 배지용 안 읽은 수·미처리 요청 수",
            description = "폴링 대상 경량 엔드포인트(§0). `연결됨` 탭 배지(unreadCount)와 `요청함` 탭 배지(pendingRequestCount)를 " +
                    "한 번에 내려준다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ThreadSummaryResponse> getSummary();

    @Operation(
            summary = "메시지 조회(커서 페이징)",
            description = "최신순으로 내려주며, cursor 미지정 시 최신 페이지부터 시작한다.\n\n**권한:** CREATOR(본인 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<MessageListResponse> getMessages(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId,
            @Parameter(description = "커서(직전 응답의 nextCursor)") @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "페이지 크기(기본 30)") @RequestParam(value = "size", required = false) Integer size
    );

    @Operation(
            summary = "메시지 전송",
            description = "clientMessageId는 FE가 발급한 멱등키다. 같은 키로 재전송하면 신규 저장 대신 " +
                    "기존 메시지를 200으로 반환한다(§13-10) — 신규 전송은 201.\n\n" +
                    "**권한:** CREATOR(본인 스레드만, 휴면 스레드는 작성 불가)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "신규 전송 성공"),
            @ApiResponse(responseCode = "200", description = "멱등 재전송 — 기존 메시지 반환"),
            @ApiResponse(responseCode = "400", description = "본문 없음(content 빈 값)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "휴면 스레드에 전송 시도(THREAD_DORMANT)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<MessageItem> sendMessage(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId,
            @Valid @RequestBody SendMessageRequest request
    );

    @Operation(
            summary = "읽음 처리",
            description = "현재 스레드의 마지막 메시지까지 읽음으로 표시한다.\n\n**권한:** CREATOR(본인 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markRead(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId
    );
}
